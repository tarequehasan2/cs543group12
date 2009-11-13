package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class InvertedPageTable
{
    protected static IMachine machine = LiveMachine.getInstance();

    public static boolean handleTLBMiss(VMProcess process, int page) {
        _lock.acquire();
        debug("ENTER:handleTLBMiss("+process+","+page+")");

        // sync from Processor's TLB statuses before we blow it away
        syncAllProcTlb();

        boolean result = loadEntry(process, page);
        if (! result) {
            error("LoadEntry("+process+","+page+") failed");
            _lock.release();
            return result;
        }
        final int pid = process.getPid();
        /// we only need to check CoreEntry because that was loadEntry's JOB
        /// it should not have put it into swap
        final SwapAwareTranslationEntry entry = findMainEntryForVpn(pid, page);
        if (null == entry) {
            error("loadEntry OK but no getEntryFor ("+pid+","+page+")");
            _lock.release();
            return false;
        }
        overwriteRandomTLB(entry);
        _lock.release();
        return result;
    }

    protected static boolean loadEntry(VMProcess process, int page) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:loadEntry("+process+","+page+")");
		final int pid = process.getPid();
		SwapAwareTranslationEntry entry = findMainEntryForVpn(pid, page);
        if (null == entry) {
            debug("Missing core entry for ("+pid+","+page+"); consulting swap");
            // then maybe it's in swap
            entry = findSwapEntryForVpn(pid, page);
        }

        if (null == entry) {
        	error("("+pid+","+page+") requested a BOGUS frame");
            return false;
        }

        if (entry.isValid()) {
        	debug("("+pid+","+page+") requested a load for what we think is a live frame ("+entry.getVpn()+","+entry.getPpn()+")");
            return true;
        }

        /// WARNING: the order matters here!
        if (entry.isInSwap()) {
            debug("Rolling pid="+pid+"'s back in from: "+entry);
            int ppn = mallocOrSwap(pid);
            SwapFile.rollIn(entry.getSwapPageNumber(), ppn);
            moveEntryFromSwapToMainTable(pid, entry);
            addToCoreMap(ppn, entry);
            entry.restoredToMemory(ppn);
            debug("Rolled pid="+pid+"'s back in from: "+entry);
            return true;
        }
        if (entry.isStack()) {
            debug("allocing pid="+pid+"'s stack page "+entry);
            int ppn = mallocOrSwap(pid);
            initializePage(ppn);
            addToCoreMap(ppn, entry);
            entry.restoredToMemory(ppn);
            debug("Alloced pid="+pid+"'s stack page "+entry);
            return true;
        }
        if (entry.isCoff()) {
            debug("Rolling pid="+pid+"'s coffSection in from: "+entry);
            int ppn = mallocOrSwap(pid);
            // this will Lib.assert() if the coffSection is out of bounds
            final CoffSection section = process.getCoff()
                    .getSection(entry.getCoffSection());
            section.loadPage(entry.getCoffPage(), ppn);
            addToCoreMap(ppn, entry);
            entry.restoredToMemory(ppn);
            debug("Rolled pid="+pid+"'s coffSection in from: "+entry);
            return true;
        }
        Lib.assertTrue(false, "("+pid+","+page+"):: Unhandled case!");
        return false;
    }

    private static void moveEntryFromSwapToMainTable(
            int pid, SwapAwareTranslationEntry entry) {
        debug("ENTER:moveEntryFromSwapToMain("+pid+","+entry+")");
        final Map<Integer, SwapAwareTranslationEntry> entryMap
                = SWAP_TABLE.get(pid);
        SwapAwareTranslationEntry entry2 = entryMap.get(entry.getVpn());
        addToMainTable(pid, entry2);
        entryMap.remove(entry.getVpn());
    }

    private static void moveEntryFromMainToSwapTable(
            int pid, SwapAwareTranslationEntry entry) {
        debug("ENTER:moveEntryFromMainToSwap("+pid+","+entry+")");
        final Map<Integer, SwapAwareTranslationEntry> entryMap = TABLE.get(pid);
        SwapAwareTranslationEntry entry2 = entryMap.get(entry.getVpn());
        addToSwapTable(pid, entry2);
        entryMap.remove(entry.getVpn());
    }

    public static void free(int pid) {
        debug("ENTER:free("+pid+")");
        freeFromMain(pid);
        freeFromSwap(pid);
    }

    protected static void freeFromMain(int pid) {
        freeFromTable(TABLE, pid);
    }

    protected static void freeFromSwap(int pid) {
        freeFromTable(SWAP_TABLE, pid);
    }

    private static void freeFromTable(
            Map<Integer, Map<Integer, SwapAwareTranslationEntry>> theTable, int pid) {
    	_lock.acquire();
        if (!theTable.containsKey(pid)) {
        	_lock.release();
            return;
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = theTable.get(pid);
        for (SwapAwareTranslationEntry entry : pages.values()) {
            final int ppn = entry.getPpn();
            if (entry.isValid() && -1 != ppn) {
                clearCoreMapEntry(ppn);
            }
            entry.ejectedFromMemory();
        }
        theTable.get(pid).clear();
        theTable.remove(pid);
    	_lock.release();
    }

    private static void clearCoreMapEntry(int ppn) {
        if (CORE_MAP.containsKey(ppn)) {
            CORE_MAP.get(ppn).clear();
        }
    }

    private static void overwriteRandomTLB(SwapAwareTranslationEntry entry) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:overwriteRandomTLB("+entry+")");
        final int tlbSize = machine.getTlbSize();
        int victim = -1;
        for (int i = 0; i < tlbSize; i++) {
            TranslationEntry te = machine.readTlbEntry(i);
            if (! te.valid) {
                victim = i;
                break;
            }
        }
        if (-1 == victim) {
            victim = Lib.random(tlbSize);
        }
        final TranslationEntry tlbEntry = entry.toTranslationEntry();
        debug("Overwriting TLB["+victim+"] with "+tlbEntry);
        machine.writeTlbEntry(victim, tlbEntry);
    }

    private static void initializePage(int ppn) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:initializePage("+ppn+")");
        final int pageSize = machine.getPageSize();
		int offset = ppn * pageSize;
        byte[] memory = machine.getMemory();
        for (int i = 0; i < pageSize; i++) {
            int addr = offset + i;
            memory[addr] = 0;
        }
    }

    protected static void addToCoreMap(int ppn, SwapAwareTranslationEntry entry) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:addToCoreMap("+ppn+","+entry+")");
        List<SwapAwareTranslationEntry> pages;
        if (! CORE_MAP.containsKey(ppn)) {
            pages = new ArrayList<SwapAwareTranslationEntry>();
            CORE_MAP.put(ppn, pages);
        } else {
            pages = CORE_MAP.get(ppn);
        }
        pages.add(entry);
    }

    protected static void addToMainTable(int forPid, SwapAwareTranslationEntry entry) {
        debug("ENTER:addToMainTable("+forPid+","+entry+")");
        addToTable(TABLE, forPid, entry);
    }

    protected static void addToSwapTable(int forPid, SwapAwareTranslationEntry entry) {
        debug("ENTER:addToSwapTable("+forPid+","+entry+")");
        addToTable(SWAP_TABLE, forPid, entry);
    }

    private static void addToTable(
            Map<Integer, Map<Integer, SwapAwareTranslationEntry>> theTable,
            int forPid, SwapAwareTranslationEntry entry) {
        Map<Integer, SwapAwareTranslationEntry> map;
        if (! theTable.containsKey(forPid)) {
            map = new HashMap<Integer, SwapAwareTranslationEntry>();
            theTable.put(forPid, map);
        } else {
            map = theTable.get(forPid);
        }
        map.put(entry.getVpn(), entry);
    }

    // TODO: move this into VMKernel and provide support methods here
    protected static int mallocOrSwap(int forPid) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:mallocOrSwap("+forPid+")");
        int result;
        final int numPhysPages = nachos.machine.Machine.processor().getNumPhysPages();
        for (int i = 0; i < numPhysPages; i++) {
        	if (! CORE_MAP.containsKey(i)) {
                // this shouldn't happen, but is harmless if it does
                invalidateTlbForPpn(i);
                debug("malloc based on coremap:= "+i);
        		return i;
        	}
        }
        int victimPPN = chooseVictimPage();
        debug("malloc victim page := "+victimPPN);
        if (doesNeedRollOut(victimPPN)) {
            debug("rolling-out PPN "+victimPPN);
            int spn = SwapFile.rollOut(victimPPN);
            // notify everyone else that we just blew away that SPN's values
            invalidateSwapCacheForSpn(spn);
            if (CORE_MAP.containsKey(victimPPN)) {
                for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                    entry.movedToSwap(spn);
                    debug("VictimPPN("+victimPPN+") held "+entry+", which we swapped out");
                    moveEntryFromMainToSwapTable(forPid, entry);
                }
                clearCoreMapEntry(victimPPN);
            }
        } else {
            // just eject them
            debug("ejecting PPN "+victimPPN+" because is not dirty");
            if (CORE_MAP.containsKey(victimPPN)) {
                for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                    entry.ejectedFromMemory();
                    debug("VictimPPN("+victimPPN+") held "+entry+", which we ejected");
                }
                clearCoreMapEntry(victimPPN);
            }

        }
        invalidateTlbForPpn(victimPPN);
        result = victimPPN;
        debug("malloc based on roll-out := "+result);
        return result;
    }

    protected static void invalidateSwapCacheForSpn(int spn) {
        debug("ENTER:invalidateSwapCacheForSpn("+spn+")");
        for (Integer pid : SWAP_TABLE.keySet()) {
            java.util.List<SwapAwareTranslationEntry> killed
                    = new java.util.ArrayList<SwapAwareTranslationEntry>();
            final Map<Integer, SwapAwareTranslationEntry> entryMap
                    = SWAP_TABLE.get(pid);
            for (SwapAwareTranslationEntry entry : entryMap.values()) {
                if (entry.isInSwap() && entry.getSwapPageNumber() == spn) {
                    SwapFile.free(new int[] { spn });
                    entry.removedFromSwapfile();
                    killed.add(entry);
                }
            }
            for (SwapAwareTranslationEntry entry : killed) {
                entryMap.remove(entry.getVpn());
            }
        }
    }

    protected static void invalidateTlbForPpn(int ppn) {
        debug("ENTER:invalidateTlbForPpn("+ppn+")");
        TranslationEntry tlbEntry = findProcTLBforPpn(ppn);
        if (null != tlbEntry) {
            debug("Malloc claimed the life of ProcTLB[ppn="+ppn+"]:="+tlbEntry);
            tlbEntry.valid = false;
		}
    }

    protected static boolean doesNeedRollOut(int ppn) {
        debug("ENTER:doesNeedRollOut("+ppn+")");
        boolean result = false;
        TranslationEntry tlbEntry = findProcTLBforPpn(ppn);
        if (null != tlbEntry) {
            debug("ProcTLB[ppn="+ppn+"]:="+tlbEntry);
        	if (ppn == tlbEntry.ppn  && tlbEntry.valid && tlbEntry.dirty) {
        		result = true;
        	}
		}
        if (! result) {
        	// check our copies
        	if (CORE_MAP.containsKey(ppn)) {
        		for (SwapAwareTranslationEntry entry : CORE_MAP.get(ppn)) {
                    debug("CoreMapTLB[ppn="+ppn+"]:="+entry);
        			if (entry.isValid() && entry.isDirty()) {
        				result = true;
        				break;
        			}
                }
            }
        }
        debug("RETURN:doesNeedRollOut("+ppn+"):"+result);
        return result;
    }

	protected static TranslationEntry findProcTLBforPpn(int ppn) {
		final int tlbSize = machine.getTlbSize();
		TranslationEntry result = null;
        for (int i = 0; i < tlbSize; i++) {
			final TranslationEntry entry = machine.readTlbEntry(i);
			if (entry.ppn == ppn) {
				result = entry;
				break;
			}
		}
		return result;
	}

	public static TranslationEntry findProcTLBforVpn(int vpn) {
		final int tlbSize = machine.getTlbSize();
		TranslationEntry result = null;
        for (int i = 0; i < tlbSize; i++) {
			final TranslationEntry entry = machine.readTlbEntry(i);
			if (entry.vpn == vpn) {
				result = entry;
				break;
			}
		}
		return result;
	}

    protected static int chooseVictimPage() {
        return algorithm.findVictim();
    }

    public static void addCoff(VMProcess process, int stackSize) {
        _lock.acquire();
        debug("ENTER:addCoff("+process+","+stackSize+")");
        final Coff coff = process.getCoff();
        final int pid = process.getPid();
        int sectionCount = coff.getNumSections();
        int pageCount = 0;
        if (! TABLE.containsKey(pid)) {
            TABLE.put(pid, new HashMap<Integer, SwapAwareTranslationEntry>());
        }
        for (int i = 0; i < sectionCount; i++) {
            final CoffSection section = coff.getSection(i);
            final int length = section.getLength();
            debug("CoffSection["+section.getName()+"#"+i+"]("+length+")");
            addCoffSection(pid, section, i);
            pageCount += length;
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        final int stackFrameCount = stackSize + 1; // for the arguments
        for (int i = 0; i < stackFrameCount; i++) {
            int vpn = pageCount + i;
            final boolean isStack = true;
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry(vpn, isStack);
            pages.put(vpn, sate);
        }
        debug("CoffLoad:PAGES="+pages);
        _lock.release();
    }

    protected static void addCoffSection(int pid, CoffSection section, int sectionNumber) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:addCoffSection("+pid+","+section+","+sectionNumber+")");
        if (!TABLE.containsKey(pid)) {
            TABLE.put(pid, new HashMap<Integer, SwapAwareTranslationEntry>());
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        int baseVpn = section.getFirstVPN();
        int pageCount = section.getLength();
        for (int i = 0; i < pageCount; i++) {
            int vpn = baseVpn + i;
            final boolean readOnly = section.isReadOnly();
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry(
            		vpn, readOnly, sectionNumber, i);
            pages.put(vpn, sate);
        }
    }

    public static void setVirtualUsed(VMProcess process, int vpn) {
        _lock.acquire();
        debug("ENTER:setVirtualUsed("+process+","+vpn+")");
        final int pid = process.getPid();

		final int tlbSize = machine.getTlbSize();
        boolean tlbLoaded = false;
        for (int i = 0; i < tlbSize; i++) {
			final TranslationEntry entry = machine.readTlbEntry(i);
			if (entry.vpn == vpn) {
	        	entry.used = true;
	        	machine.writeTlbEntry(i, entry);
                tlbLoaded = true;
				break;
			}
		}
        if (!tlbLoaded) {
            debug("Used page ("+pid+","+vpn+") isn't in the TLB");
        }

        /// shouldn't have to check Swap here becaus we are being invoked from
        /// a live virtual page access
        SwapAwareTranslationEntry entry = findMainEntryForVpn(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        if (!entry.isValid()) {
            if (!loadEntry(process, vpn)) {
                error("unable to load \"used\" entry for ("+pid+","+vpn+"):\r\n"
                                +TABLE.get(pid));
            }
        }
        entry.markAsUsed();
        _lock.release();
    }

    public static void setVirtualWritten(VMProcess process, int vpn) {
        debug("ENTER:setVirtualWritten("+process+","+vpn+")");
        final int pid = process.getPid();
        // ensure you do this outside the lock
        setVirtualUsed(process, vpn);
        _lock.acquire();

		final int tlbSize = machine.getTlbSize();
        for (int i = 0; i < tlbSize; i++) {
			final TranslationEntry entry = machine.readTlbEntry(i);
			if (entry.valid && entry.vpn == vpn) {
                Lib.assertTrue(!entry.readOnly,
                        "PROC:Attempt to write to readOnly memory ("+pid+","+vpn+")");
	        	entry.dirty = true;
	        	machine.writeTlbEntry(i, entry);
				break;
			}
		}

        /// shouldn't have to check Swap here becaus we are being invoked from
        /// a live virtual page access
        SwapAwareTranslationEntry entry = findMainEntryForVpn(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        if (entry.isValid()) {
            // clear the swap backing page since it needs to be re-writen now
            if (entry.isInSwap()) {
                SwapFile.free(new int[] { entry.getSwapPageNumber() });
                entry.removedFromSwapfile();
            }
            Lib.assertTrue(!entry.isReadOnly(),
                "CORE_MAP:Attempt to write to readOnly memory ("+pid+","+vpn+")");
            entry.markAsDirty();
        }
        _lock.release();
    }

    protected static TranslationEntry findIPTEntryForVirtualPage(int pid, int vpn) {
        SwapAwareTranslationEntry entry = findMainEntryForVpn(pid, vpn);
        if (null == entry) {
            // okay, maybe we moved it to swap
            entry = findSwapEntryForVpn(pid, vpn);
        }
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
            return null;
        }
        return entry.toTranslationEntry();
    }

    public static int[] findAllSwapPagesByPid(int pid) {
        java.util.Set<Integer> pages = new java.util.HashSet<Integer>();
        if (! SWAP_TABLE.containsKey(pid)) {
            return new int[0];
        }
        for (SwapAwareTranslationEntry entry : SWAP_TABLE.get(pid).values()) {
            if (entry.isInSwap() && -1 != entry.getSwapPageNumber()) {
                pages.add(entry.getSwapPageNumber());
            }
        }
        // now remove the keys which belong to other PIDs
        for (Integer key : SWAP_TABLE.keySet()) {
            if (key == pid) {
                continue;
            }
            for (SwapAwareTranslationEntry entry : SWAP_TABLE.get(key).values()) {
                if (entry.isInSwap() && -1 != entry.getSwapPageNumber()) {
                    pages.remove(entry.getSwapPageNumber());
                }
            }
        }
        int[] results = new int[pages.size()];
        int i = 0;
        for (Integer value : pages) {
            results[i] = value;
            i++;
        }
        return results;
    }

    protected static SwapAwareTranslationEntry findMainEntryForVpn(int pid, int vpn) {
        return getTableEntryForVpn(TABLE, pid, vpn);
    }

    protected static SwapAwareTranslationEntry findSwapEntryForVpn(int pid, int vpn) {
        return getTableEntryForVpn(SWAP_TABLE, pid, vpn);
    }

    public static void syncAllProcTlb() {
        int tlbSize = machine.getTlbSize();
        for (int i = 0; i < tlbSize; i++) {
            final TranslationEntry entry = machine.readTlbEntry(i);
            debug("SYNC:ProcTLB["+i+"]:="+entry);
            if (entry.valid) {
                debug("\tSYNC:IPT-TLB["+i+"]:="+CORE_MAP.get(entry.ppn).get(0));
                if (syncProcTlb(entry)) {
                    debug("\tSYNC:IPT'-TLB["+i+"]:="+CORE_MAP.get(entry.ppn).get(0));
                }
            }
        }
    }

    public static boolean syncProcTlb(TranslationEntry entry) {
        if (! entry.valid) {
            debug("Request to sync a non-valid TLB entry, which I ignored");
            return false;
        }
        Lib.assertTrue(CORE_MAP.containsKey(entry.ppn),
                "Cannot sync up a TLB for non-core "+entry);
        final List<SwapAwareTranslationEntry> pages = CORE_MAP.get(entry.ppn);
        boolean result = false;
        for (SwapAwareTranslationEntry sate : pages) {
            Lib.assertTrue(sate.getPpn() == entry.ppn, "Bogus SATE "+sate);
            if (sate.getVpn() == entry.vpn) {
                if (entry.used) {
                    if (! sate.isUsed()) {
                        result = true;
                        sate.markAsUsed();
                    }
                }
                if (entry.dirty) {
                    if (! sate.isDirty()) {
                        result = true;
                        sate.markAsDirty();
                    }
                }
                // specifically check this after the prior, so we'll benefit
                // from its knowledge as well as our own
                if (sate.isDirty()) {
                    // clear the swap backing page since it needs to be re-writen now
                    if (sate.isInSwap()) {
                        SwapFile.free(new int[] { sate.getSwapPageNumber() });
                        result = true;
                        sate.removedFromSwapfile();
                    }
                }
                Lib.assertTrue(sate.isReadOnly() == entry.readOnly,
                        "Mismatched r/o state: "+sate);
                Lib.assertTrue(sate.isValid() == entry.valid,
                        "Mismatched valid state: "+sate);
            }
        }
        return result;
    }

    private static SwapAwareTranslationEntry getTableEntryForVpn(
            Map<Integer, Map<Integer, SwapAwareTranslationEntry>> fromTable,
            int pid, int vpn) {
        if (! fromTable.containsKey(pid)) {
            return null;
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = fromTable.get(pid);
        if (! pages.containsKey(vpn)) {
            return null;
        }
        return pages.get(vpn);
    }

    private static void error(String msg) {
    	System.err.println("ERROR:IPT:"+msg);
    }

    private static void debug(String msg) {
        Lib.debug(dbgFlag, "DEBUG:IPT:"+msg);
    }


    /**
     * Indexes the VPN and SATE for it, grouped by PID, available to be
     * loaded into main memory.
     */
    private static Map<Integer, Map<Integer, SwapAwareTranslationEntry>>
        TABLE = new HashMap<Integer, Map<Integer, SwapAwareTranslationEntry>>();
    /**
     * Indexes the VPN and SATE for it, grouped by PID, but whose entries are
     * housed in swap.
     */
    private static Map<Integer, Map<Integer, SwapAwareTranslationEntry>>
        SWAP_TABLE = new HashMap<Integer, Map<Integer, SwapAwareTranslationEntry>>();
    /**
     * Indexes the PPN and the SATE page tables which are stored there.
     * Since we did not implement shared loading, the size of the list will
     * always be 1.
     */
    private static Map<Integer, List<SwapAwareTranslationEntry>>
        CORE_MAP = new HashMap<Integer, List<SwapAwareTranslationEntry>>();
    private static Lock _lock = new Lock();
    private static Algorithm algorithm = new RandomAlgorithm(CORE_MAP, _lock);
    private static final char dbgFlag = 'I';
}
