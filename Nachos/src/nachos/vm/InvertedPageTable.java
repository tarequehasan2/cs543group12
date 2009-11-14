package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Config;
import nachos.machine.Lib;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class InvertedPageTable
{
	static {
		try{
			String className = Config.getString("Algorithm");
			if (className != null && !className.equals("")){
				algorithm = (Algorithm) Lib.constructObject(Config.getString("Algorithm"));
			}else{
				algorithm = new ClockAlgorithm();
			}
		}catch(Throwable t){
			algorithm = new ClockAlgorithm();
		}
	}
    protected static IMachine machine = LiveMachine.getInstance();

    public static boolean handleTLBMiss(VMProcess process, int page) {
        _lock.acquire();
        debug("ENTER:handleTLBMiss("+process+","+page+")");
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
        Lib.assertTrue(null != entry,
                    "loadEntry OK but no getEntryFor ("+pid+","+page+")"
                            +"\r\nIPT:="+TABLE.get(pid)
                            +"\r\nSWAP:="+SWAP_TABLE.get(pid));
        if (null == entry) {
            _lock.release();
            return false;
        }
        // we have already synced up the processor TLB in VMProcess#handleException
        overwriteRandomTLB(entry);
        _lock.release();
        return result;
    }

    protected static boolean loadEntry(VMProcess process, int page) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:loadEntry("+process+","+page+")");

        /// FIXME: is it worth going through Kernel.kernel and downcasting?
        
		final int pid = process.getPid();

		SwapAwareTranslationEntry entry = findEntryForVpn(pid, page);

        if (null == entry) {
        	error("("+pid+","+page+") requested a BOGUS frame");
            return false;
        }

        if (entry.isValid()) {
        	debug("("+pid+","+page+") requested a load for what we think is a live frame ("+entry.getVpn()+","+entry.getPpn()+")");
            return true;
        }
        VMKernel.recordPageFault();
        /// WARNING: the order matters here!
        if (entry.isInSwap()) {
            debug("Rolling pid="+pid+"'s back in from: "+entry);
            Lib.assertTrue(SWAP_TABLE.containsKey(pid),
                    "Swap doesn't know about your PID("+pid+"):="
                            +TABLE.get(pid)+"\r\n\r\n"
                            +SWAP_TABLE+"\r\n"+entry);
            Lib.assertTrue(SWAP_TABLE.get(pid).containsKey(entry.getVpn()),
                    "How did you get an entry in swap pid="+pid+"::"+entry
                    +"when SWAP:=\r\n"+SWAP_TABLE);
            int ppn = mallocOrSwap();
            SwapFile.rollIn(entry.getSwapPageNumber(), ppn);
            CoreMap.addToCoreMap(ppn, pid, entry.getVpn());
            entry.restoredToMemory(ppn);
            // FIXME: we are just dispensing with the "it's in swap until it's overwritten" idea
            entry.removedFromSwapfile();
            moveEntryFromSwapToMainTable(pid, entry);
            debug("Rolled pid="+pid+"'s back in from: "+entry);
            return true;
        }
        if (entry.isStack()) {
            debug("allocing pid="+pid+"'s stack page "+entry);
            int ppn = mallocOrSwap();
            initializePage(ppn);
            entry.restoredToMemory(ppn);
            CoreMap.addToCoreMap(ppn, pid, entry.getVpn());
            debug("Alloced pid="+pid+"'s stack page "+entry);
            return true;
        }
        if (entry.isCoff()) {
            debug("Rolling pid="+pid+"'s coffSection in from: "+entry);
            int ppn = mallocOrSwap();
            // this will Lib.assert() if the coffSection is out of bounds
            final CoffSection section = process.getCoff()
                    .getSection(entry.getCoffSection());
            section.loadPage(entry.getCoffPage(), ppn);
            entry.restoredToMemory(ppn);
            CoreMap.addToCoreMap(ppn, pid, entry.getVpn());
            debug("Rolled pid="+pid+"'s coffSection in from: "+entry);
            return true;
        }
        Lib.assertTrue(false, "("+pid+","+page+"):: Unhandled case!");
        return false;
    }

    private static int mallocOrSwap() {
        int ppn = CoreMap.malloc();
        if (-1 == ppn) {
            debug("no free pages, someone has to go");
            ppn = swap();
            debug("and our lucky winner is "+ppn);
        }
        return ppn;
    }

    /**
     * Indicates that you wish the system to victimize an in-use page.
     * It will do so, and return that page to you. That page should
     * never be -1 since we will always target someone from 0 to maxPages-1.
     * <b>ENSURE</b> you tidy up any bookkeeping about who's using that ppn.
     * @return the page which was moved to swap.
     */
    public static int swap() {
        int result = chooseVictimPage();
        debug("swap victim page := "+result);
        if (doesNeedRollOut(result)) {
            debug("rolling-out PPN "+result);
            Lib.assertTrue(CoreMap.containsPpn(result),
                    "You're persisting ("+result+") non-Core memory?!");
            // move the existing memory out of the way
            int spn = SwapFile.rollOut(result);
            // invalidate the cached entries who lived in that SPN
            ejectSwapTableEntriesForSpn(spn);
            // notify everyone who used that page that they are now in swap
            movePpnEntriesToSwap(result, spn);
        } else {
            // just eject them
            debug("ejecting PPN "+result+" because is not dirty");
            ejectEntriesForPpn(result);
        }
        CoreMap.free(result);
        debug("malloc based on roll-out := "+result);
        return result;
    }

    private static void movePpnEntriesToSwap(int ppn, int spn) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        for (Integer pid : TABLE.keySet()) {
            for (SwapAwareTranslationEntry entry : TABLE.get(pid).values().toArray(new SwapAwareTranslationEntry[0])) {
                if (entry.getPpn() == ppn) {
                    entry.movedToSwap(spn);
                    moveEntryFromMainToSwapTable(pid, entry);
                }
            }
        }
    }

    private static void moveEntryFromSwapToMainTable(
            int pid, SwapAwareTranslationEntry entry) {
        debug("ENTER:moveEntryFromSwapToMain("+pid+","+entry+")");
        Lib.assertTrue(null != entry,
                "You can't move NULL Entry to the Main table");
        Lib.assertTrue(entry.isValid(),
                "I would expect a valid frame before marking it as live: "+entry);
        final Map<Integer, SwapAwareTranslationEntry> entryMap
                = SWAP_TABLE.get(pid);
        Lib.assertTrue(null != entryMap,
                "No such Entry in SWAP for pid "+pid
                        +"\r\nEntry:"+entry
                        +"\r\n"+SWAP_TABLE);
        Lib.assertTrue(entryMap.containsKey(entry.getVpn()),
                "Unable to find Swap Entry pid("+pid+")\r\n"
                +"Entry: "+entry
                +"\r\n"+SWAP_TABLE);
        entryMap.remove(entry.getVpn());
        entry.removedFromSwapfile();
        addToMainTable(pid, entry);
    }

    private static void moveEntryFromMainToSwapTable(
            int pid, SwapAwareTranslationEntry entry) {
        debug("ENTER:moveEntryFromMainToSwap("+pid+","+entry+")");
        Lib.assertTrue(null != entry,
                "You can't move NULL Entry to the Swap table");
        Lib.assertTrue(entry.isInSwap(),
                "Moving a non-swap entry, are we?");
        Lib.assertTrue(! entry.isValid(),
                "Moving a valid entry, are we?");
        final Map<Integer, SwapAwareTranslationEntry> entryMap = TABLE.get(pid);
        Lib.assertTrue(null != entryMap,
                "No such Entry in Main Table for pid "+pid+"\r\n"+TABLE);
        // increase atomicy; remove it (so it doesn't exist in either)
        // before adding it in case we get context-swapped
        entryMap.remove(entry.getVpn());
        entry.ejectedFromMemory();
        addToSwapTable(pid, entry);
    }

    public static void free(int pid) {
        debug("ENTER:free("+pid+")");
        _lock.acquire();
        freeByPid(pid);
        freeFromMain(pid);
        freeFromSwap(pid);
        _lock.release();
    }

    private static void freeByPid(int pid) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        for (SwapAwareTranslationEntry sate : TABLE.get(pid).values()) {
            CoreMap.free(sate.getPpn());
        }
    }

    protected static void freeFromMain(int pid) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        freeFromTable(TABLE, pid);
    }

    protected static void freeFromSwap(int pid) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        freeFromTable(SWAP_TABLE, pid);
    }

    private static void freeFromTable(
            Map<Integer, Map<Integer, SwapAwareTranslationEntry>> theTable, int pid) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        if (!theTable.containsKey(pid)) {
            return;
        }
        theTable.get(pid).clear();
        theTable.remove(pid);
    }

    /**
     * Replaces the Processor's TLB entry with the value of the provided one.
     * Technically, we're cheating you with this method name, since it only
     * reverts to random ejection if it is unable to find a better heuristic
     * choice. It tries to eject the existing ppn if found, followed by any
     * already invalid TLB entry, and only then does it revert to random.
     * @param entry the values to inject into the Processor's TLB at a location
     * of my choosing.
     */
    private static void overwriteRandomTLB(SwapAwareTranslationEntry entry) {
    	Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:overwriteRandomTLB("+entry+")");
        final int ppn = entry.getPpn();
        final int tlbSize = machine.getTlbSize();
        int victim = -1;
        for (int i = 0; i < tlbSize; i++) {
            TranslationEntry te = machine.readTlbEntry(i);
            // always choose to overwrite an existing PPN mapping
            // regardless of its existing validity because if we
            // are in this method, we have updated information about that ppn
            if (te.ppn == ppn) {
                victim = i;
                break;
            }
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

    protected static void addToMainTable(int forPid, SwapAwareTranslationEntry entry) {
        debug("ENTER:addToMainTable("+forPid+","+entry+")");
        Lib.assertTrue(null != entry, "You can't add a NULL Entry to the Main table");
        addToTable(TABLE, forPid, entry);
    }

    protected static void addToSwapTable(int forPid, SwapAwareTranslationEntry entry) {
        debug("ENTER:addToSwapTable("+forPid+","+entry+")");
        Lib.assertTrue(null != entry,
                "You can't add a NULL Entry to the Swap table");
        Lib.assertTrue(! entry.isValid(),
            "Why is your entry still valid?");
        Lib.assertTrue(entry.isInSwap(),
            "Why are you adding a non-swap entry to the Swap Table?");
        Lib.assertTrue(-1 != entry.getSwapPageNumber(),
            "Why does your entry not have a swap page number?");
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

    /**
     * Notifies all the SATE living at the given ppn that they have been
     * ejected from memory.
     * @param ppn the physical page which has been cleared.
     */
    private static void ejectEntriesForPpn(int ppn) {
        for (Integer pid : TABLE.keySet()) {
            for (SwapAwareTranslationEntry entry : TABLE.get(pid).values()) {
                if (entry.getPpn() == ppn) {
                    entry.ejectedFromMemory();
                }
            }
        }
    }

    /**
     * Finds all Swap Table entries who claim to live at the provided spn
     * and notifies them they have been ejected from swap. This most often
     * occurs when SwapFile re-allocates their swap page.
     * @param spn the swap page number that has just been allocated.
     */
    protected static void ejectSwapTableEntriesForSpn(int spn) {
        debug("ENTER:invalidateSwapCacheForSpn("+spn+")");
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        for (Integer pid : TABLE.keySet()) {
            List<SwapAwareTranslationEntry> killed
                    = new ArrayList<SwapAwareTranslationEntry>();
            final Map<Integer, SwapAwareTranslationEntry> entryMap
                    = TABLE.get(pid);
            for (SwapAwareTranslationEntry entry : entryMap.values()) {
                Lib.assertTrue(!entry.isInSwap(),
                        "A Swap Entry in Main("+pid+")? Scandal!:"+entry+"\r\n"+entryMap);
                if (entry.isInSwap() && entry.getSwapPageNumber() == spn) {
                    entry.removedFromSwapfile();
                    killed.add(entry);
                }
            }
            for (SwapAwareTranslationEntry entry : killed) {
                entryMap.remove(entry.getVpn());
            }
        }
        for (Integer pid : SWAP_TABLE.keySet()) {
            List<SwapAwareTranslationEntry> killed
                    = new ArrayList<SwapAwareTranslationEntry>();
            final Map<Integer, SwapAwareTranslationEntry> entryMap
                    = SWAP_TABLE.get(pid);
            for (SwapAwareTranslationEntry entry : entryMap.values()) {
                if (entry.isInSwap() && entry.getSwapPageNumber() == spn) {
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
            for (Integer pid : TABLE.keySet()) {
                for (SwapAwareTranslationEntry entry : TABLE.get(pid).values()) {
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

    protected static int chooseVictimPage() {
        int result;
        result = algorithm.findVictim();
        return result;
    }

    public static void addCoff(VMProcess process, int stackSize) {
        _lock.acquire();
        debug("ENTER:addCoff("+process+","+stackSize+")");
        final Coff coff = process.getCoff();
        final int pid = process.getPid();
        int sectionCount = coff.getNumSections();
        int pageCount = 0;
        for (int i = 0; i < sectionCount; i++) {
            final CoffSection section = coff.getSection(i);
            final int length = section.getLength();
            debug("CoffSection["+section.getName()+"#"+i+"]("+length+")");
            addCoffSection(pid, section, i);
            pageCount += length;
        }
        final int stackFrameCount = stackSize + 1; // for the arguments
        for (int i = 0; i < stackFrameCount; i++) {
            int vpn = pageCount + i;
            final boolean isStack = true;
            SwapAwareTranslationEntry sate
                    = new SwapAwareTranslationEntry(vpn, isStack);
            addToMainTable(pid, sate);
        }
        debug("CoffLoad:PAGES="+TABLE.get(pid));
        _lock.release();
    }

    protected static void addCoffSection(int pid, CoffSection section, int sectionNumber) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        debug("ENTER:addCoffSection("+pid+","+section+","+sectionNumber+")");
        int baseVpn = section.getFirstVPN();
        int pageCount = section.getLength();
        for (int i = 0; i < pageCount; i++) {
            int vpn = baseVpn + i;
            final boolean readOnly = section.isReadOnly();
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry(
            		vpn, readOnly, sectionNumber, i);
            addToMainTable(pid, sate);
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

        /// have to check Swap here because we are also invoked from
        /// readVirtualMemory and writeVirtualMemory, which don't go through
        /// the Processor's TLB
        SwapAwareTranslationEntry entry = findEntryForVpn(pid, vpn);
        if (null == entry) {
            Lib.assertTrue(false, "no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        if (!entry.isValid()) {
            tlbLoaded = false;
            if (!loadEntry(process, vpn)) {
                Lib.assertTrue(false,
                    "unable to load \"used\" entry for ("+pid+","+vpn+"):\r\n"
                                +TABLE.get(pid));
            }
        }
        entry.markAsUsed();
        if (!tlbLoaded) {
            overwriteRandomTLB(entry);
        }
        _lock.release();
    }

    public static void setVirtualWritten(VMProcess process, int vpn) {
        debug("ENTER:setVirtualWritten("+process+","+vpn+")");
        final int pid = process.getPid();
        // ensure you do this outside the lock
        // also, this will result in the page being brought into memory
        // plus being stored in the TLB; so no need for checking that here
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

        SwapAwareTranslationEntry entry = findEntryForVpn(pid, vpn);
        if (null == entry) {
            Lib.assertTrue(false,
                    "no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        if (entry.isValid()) {
            // clear the swap backing page since it needs to be re-writen now
            if (entry.isInSwap()) {
                final int spn = entry.getSwapPageNumber();
                ejectSwapTableEntriesForSpn(spn);
                entry.removedFromSwapfile();
            }
            Lib.assertTrue(!entry.isReadOnly(),
                "CORE_MAP:Attempt to write to readOnly memory ("+pid+","+vpn+")");
            entry.markAsDirty();
        }
        _lock.release();
    }

    /**
     * Consults only my own tables to find the vpn belonging to that pid.
     * If you want to ask the Processor,
     * use {@link InvertedPageTable#findProcTLBforVpn(int)} instead.
     * @param pid the process id whose page you seek.
     * @param vpn the virtual page number you are looking for.
     * @return the entry if it exists, null otherwise.
     */
    protected static TranslationEntry findIPTEntryForVpn(int pid, int vpn) {
        SwapAwareTranslationEntry entry = findEntryForVpn(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
            return null;
        }
        return entry.toTranslationEntry();
    }

    /**
     * Consults the IPT table first, then checks to see if the provided vpn
     * lives in the SWAP table.
     * @param pid the process id whose table we should consult.
     * @param vpn the virtual page number you seek.
     * @return the entry if found in either table, or null if not.
     */
    protected static SwapAwareTranslationEntry findEntryForVpn(int pid, int vpn) {
        SwapAwareTranslationEntry result;
        result = findMainEntryForVpn(pid, vpn);
        if (null == result) {
            result = findSwapEntryForVpn(pid, vpn);
        }
        return result;
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
    	boolean needLock = !_lock.isHeldByCurrentThread();
    	if (needLock){
    		_lock.acquire();
    	}
        int tlbSize = machine.getTlbSize();
        for (int i = 0; i < tlbSize; i++) {
            final TranslationEntry entry = machine.readTlbEntry(i);
            debug("SYNC:ProcTLB["+i+"]:="+entry);
            if (entry.valid) {
                syncProcTlb(entry);
            }
        }
        if (needLock){
        	_lock.release();
        }
    }

    private static boolean syncProcTlb(TranslationEntry tlbEntry) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        if (! tlbEntry.valid) {
            debug("Request to sync a non-valid TLB entry, which I ignored");
            return false;
        }
        Lib.assertTrue(CoreMap.containsPpn(tlbEntry.ppn),
                "Cannot sync up a TLB for non-core "+tlbEntry);
        boolean result = false;
        for (CoreMap.CoreMapEntry coreEntry : CoreMap.findEntriesForPpn(tlbEntry.ppn)) {
            final SwapAwareTranslationEntry sate
                    = findEntryForVpn(coreEntry.getPid(), coreEntry.getVpn());
            Lib.assertTrue(null != sate,
                    "Core Map contains "+coreEntry+" but IPT does not");
            Lib.assertTrue(sate.getPpn() == tlbEntry.ppn, "Bogus SATE "+sate);
            if (sate.getVpn() == tlbEntry.vpn) {
                if (tlbEntry.used) {
                    if (! sate.isUsed()) {
                        result = true;
                        sate.markAsUsed();
                    }
                }
                if (tlbEntry.dirty) {
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
//                        final int spn = sate.getSwapPageNumber();
//                        invalidateSwapCacheForSpn(spn);
                        sate.removedFromSwapfile();
                        result = true;
                    }
                }
                Lib.assertTrue(sate.isReadOnly() == tlbEntry.readOnly,
                        "Mismatched r/o state: "+sate);
                Lib.assertTrue(sate.isValid() == tlbEntry.valid,
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
    private static Lock _lock = new Lock();
    protected static Algorithm algorithm;
    private static final char dbgFlag = 'I';
}
