package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class InvertedPageTable
{
    public static boolean handleTLBMiss(VMProcess process, int page) {
        boolean result = loadEntry(process, page);
        if (! result) {
            error("LoadEntry("+process+","+page+") failed");
            return result;
        }
        final int pid = process.getPid();
        final SwapAwareTranslationEntry entry = getEntryFor(pid, page);
        if (null == entry) {
            error("loadEntry but no getEntryFor ("+pid+","+page+")");
            return false;
        }
        overwriteRandomTLB(entry);
        return result;
    }

    public static boolean loadEntry(VMProcess process, int page) {
        final SwapAwareTranslationEntry entry = getEntryFor(process.getPid(), page);
        if (null == entry) {
            return false;
        }
        if (entry.valid) {
            return true;
        }
        if (entry.isStack) {
            int ppn = mallocOrSwap();
            entry.ppn = ppn;
            initializePage(ppn);
            addToCoreMap(ppn, entry);
            entry.valid = true;
            return true;
        }
        if (entry.inSwap) {
            int ppn = mallocOrSwap();
            SwapFile.rollIn(entry.swapPageNumber, ppn);
            entry.ppn = ppn;
            addToCoreMap(ppn, entry);
            entry.valid = true;
            return true;
        }
        if (entry.isCoff) {
            int ppn = mallocOrSwap();
            // this will Lib.assert() if the coffSection is out of bounds
            final CoffSection section = process.getCoff()
                    .getSection(entry.coffSection);
            section.loadPage(entry.coffPage, ppn);
            entry.ppn = ppn;
            addToCoreMap(ppn, entry);
            entry.valid = true;
            return true;
        }
        return false;
    }

    public static void free(int pid) {
        if (!TABLE.containsKey(pid)) {
            return;
        }
        final VMKernel kernel = (VMKernel) Kernel.kernel;
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        for (SwapAwareTranslationEntry entry : pages.values()) {
            final int ppn = entry.ppn;
            if (entry.valid && -1 != ppn) {
                kernel.free(new int[] {ppn});
                clearCoreMapEntry(ppn);
            }
        }
    }

    private static void clearCoreMapEntry(int ppn) {
        if (CORE_MAP.containsKey(ppn)) {
            CORE_MAP.get(ppn).clear();
        }
    }

    private static void overwriteRandomTLB(SwapAwareTranslationEntry entry) {
        Processor proc = Machine.processor();
        int tlbSize = proc.getTLBSize();
        int victim = Lib.random(tlbSize);
        proc.writeTLBEntry(victim, entry.toTranslationEntry());
    }

    private static void initializePage(int ppn) {
        int offset = ppn * Processor.pageSize;
        byte[] memory = Machine.processor().getMemory();
        for (int i = 0; i < Processor.pageSize; i++) {
            int addr = offset + i;
            memory[addr] = 0;
        }
    }

    protected static void addToCoreMap(int ppn, SwapAwareTranslationEntry entry) {
        List<SwapAwareTranslationEntry> pages;
        if (! CORE_MAP.containsKey(ppn)) {
            pages = new ArrayList<SwapAwareTranslationEntry>();
            CORE_MAP.put(ppn, pages);
        } else {
            pages = CORE_MAP.get(ppn);
        }
        pages.add(entry);
        boolean releaseLock = false;
        //if (!_lock.isHeldByCurrentThread()){
      //  	_lock.acquire();
      //  	releaseLock = true;
       // }
        if (isPinned(ppn)){
        	unpin(ppn);
        }
       // if(releaseLock){
      //  	_lock.release();
       // }
    }

    // TODO: move this into VMKernel and provide support methods here
    protected static int mallocOrSwap() {
        final VMKernel kernel = (VMKernel) Kernel.kernel;
        int result;
            int victimPPN = chooseVictimPage();
            if (doesNeedRollOut(victimPPN)) {
                int spn = SwapFile.rollOut(victimPPN);
                if (CORE_MAP.containsKey(victimPPN)) {
                    for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                        entry.valid = false;
                        entry.readOnly = false;
                        entry.inSwap = true;
                        entry.swapPageNumber = spn;
                    }
                    clearCoreMapEntry(victimPPN);
                }
            } else {
                // just eject them
                if (CORE_MAP.containsKey(victimPPN)) {
                    for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                        entry.valid = false;
                        entry.readOnly = false;
                    }
                    clearCoreMapEntry(victimPPN);
                }
            }
            result = victimPPN;
//        } else {
//            result = pages[0];
//        }
        return result;
    }

    protected static boolean doesNeedRollOut(int victimPPN) {
        boolean result = false;
        if (CORE_MAP.containsKey(victimPPN)) {
            for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                if (entry.dirty) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    protected static int chooseVictimPage() {
    	for (int i= 0; i < Machine.processor().getNumPhysPages(); i++){
    		if (!CORE_MAP.containsKey(i)){
    			if (!isPinned(i)){
    				pin(i);
    				return i;
    			}
    		}
    	}
        return algorithm.findVictim();
    }

    public static void addCoff(int pid, Coff coff, int stackSize) {
        _lock.acquire();
        int sectionCount = coff.getNumSections();
        int pageCount = 0;
        if (! TABLE.containsKey(pid)) {
            TABLE.put(pid, new HashMap<Integer, SwapAwareTranslationEntry>());
        }
        for (int i = 0; i < sectionCount; i++) {
            final CoffSection section = coff.getSection(i);
            addCoffSection(pid, section, i);
            pageCount += section.getLength();
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        final int stackFrameCount = stackSize + 1; // for the arguments
        for (int i = 0; i < stackFrameCount; i++) {
            int vpn = pageCount + i;
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry();
            pages.put(vpn, sate);
            sate.vpn = vpn;
            sate.valid = false;
            sate.readOnly = false;
            sate.isStack = true;
            sate.pid = pid;
        }
        _lock.release();
    }

    public static void addCoffSection(int pid, CoffSection section, int sectionNumber) {
        Lib.assertTrue(_lock.isHeldByCurrentThread());
        if (!TABLE.containsKey(pid)) {
            TABLE.put(pid, new HashMap<Integer, SwapAwareTranslationEntry>());
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        int baseVpn = section.getFirstVPN();
        int pageCount = section.getLength();
        for (int i = 0; i < pageCount; i++) {
            int vpn = baseVpn + i;
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry();
            pages.put(vpn, sate);
            sate.vpn = vpn;
            sate.readOnly = section.isReadOnly();
            sate.isCoff = true;
            sate.coffSection = sectionNumber;
            sate.coffPage = i;
            sate.valid = false;
            sate.pid = pid;
        }
    }

    public static void setVirtualUsed(VMProcess process, int vpn) {
        _lock.acquire();
        final int pid = process.getPid();
        SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        if (!entry.valid) {
            if (!loadEntry(process, vpn)) {
                error("unable to load \"used\" entry for ("+pid+","+vpn+"):\r\n"
                                +TABLE.get(pid));
            }
        }
        entry.used = true;
        _lock.release();
    }

    public static void setVirtualWritten(VMProcess process, int vpn) {
        final int pid = process.getPid();
        setVirtualUsed(process, vpn);
        _lock.acquire();
        SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
        	_lock.release();
            return;
        }
        entry.dirty = true;
        _lock.release();
    }

    public static TranslationEntry getTranslationEntryForVirtualPage(int pid, int vpn) {
        final SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
            error("no entry for ("+pid+","+vpn+"):\r\n"+TABLE.get(pid));
            return null;
        }
        return entry.toTranslationEntry();
    }

    public static int[] findAllSwapPagesByPid(int pid) {
        java.util.Set<Integer> pages = new java.util.HashSet<Integer>();
        if (! TABLE.containsKey(pid)) {
            return new int[0];
        }
        for (SwapAwareTranslationEntry entry : TABLE.get(pid).values()) {
            if (entry.inSwap && -1 != entry.swapPageNumber) {
                pages.add(entry.swapPageNumber);
            }
        }
        // now remove the keys which belong to other PIDs
        for (Integer key : TABLE.keySet()) {
            if (key == pid) {
                continue;
            }
            for (SwapAwareTranslationEntry entry : TABLE.get(key).values()) {
                if (entry.inSwap && -1 != entry.swapPageNumber) {
                    pages.remove(entry.swapPageNumber);
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
    
    public static boolean isPinned(int ppn){
    	return PINNED.contains(ppn);
    }
    
    public static boolean pin(int ppn){
    	if (isPinned(ppn)){
    		return false;
    	}else{
    		PINNED.add(ppn);
    		return true;
    	}
    }
    
    public static boolean unpin(int ppn){
    	if (isPinned(ppn)){
    		PINNED.remove(Integer.valueOf(ppn));
    		return true;
    	}else{
    		return false;
    	}
    }

    protected static SwapAwareTranslationEntry getEntryFor(int pid, int vpn) {
        if (!TABLE.containsKey(pid)) {
            return null;
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        if (! pages.containsKey(vpn)) {
            return null;
        }
        return pages.get(vpn);
    }

    private static void error(String msg) {
        System.err.println("ERROR:IPT:"+msg);
    }

//    private static void debug(String msg) {
//        Lib.debug(dbgVM, "DEBUG:IPT:"+msg);
//    }

    private static Map<Integer, Map<Integer, SwapAwareTranslationEntry>>
        TABLE = new HashMap<Integer, Map<Integer, SwapAwareTranslationEntry>>();
    private static Map<Integer, List<SwapAwareTranslationEntry>>
        CORE_MAP = new HashMap<Integer, List<SwapAwareTranslationEntry>>();
    private static List<Integer> PINNED = new ArrayList<Integer>();
    private static Lock _lock = new Lock();
    private static Algorithm algorithm = new RandomAlgorithm(CORE_MAP, _lock);
//    private static final char dbgVM = 'v';
}