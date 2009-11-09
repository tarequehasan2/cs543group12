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
    public static boolean handleTLBMiss(int pid, int page) {
        final SwapAwareTranslationEntry entry = getEntryFor(pid, page);
        if (null == entry) {
            return false;
        }
        if (entry.valid) {
            overwriteRandomTLB(entry);
            return true;
        }
        if (entry.isStack) {
            int ppn = mallocOrSwap();
            entry.ppn = ppn;
            initializePage(ppn);
            addToCoreMap(ppn, entry);
            entry.valid = true;
            overwriteRandomTLB(entry);
            return true;
        }
        if (entry.inSwap) {
            int ppn = mallocOrSwap();
            SwapFile.rollIn(entry.swapPageNumber, ppn);
            entry.ppn = ppn;
            addToCoreMap(ppn, entry);
            entry.valid = true;
            overwriteRandomTLB(entry);
            return true;
        }
        if (null != entry.coffSection) {
            int ppn = mallocOrSwap();
            entry.coffSection.loadPage(entry.coffPage, ppn);
            entry.ppn = ppn;
            addToCoreMap(ppn, entry);
            entry.valid = true;
            overwriteRandomTLB(entry);
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
    }

    // TODO: move this into VMKernel and provide support methods here
    protected static int mallocOrSwap() {
        final VMKernel kernel = (VMKernel) Kernel.kernel;
        int[] pages = kernel.malloc(1);
        int result;
        if (null == pages || 0 == pages.length) {
            int victimPPN = chooseVictimPage();
            if (doesNeedRollOut(victimPPN)) {
                int spn = SwapFile.rollOut(victimPPN);
                if (CORE_MAP.containsKey(victimPPN)) {
                    for (SwapAwareTranslationEntry entry : CORE_MAP.get(victimPPN)) {
                        entry.valid = false;
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
                    }
                    clearCoreMapEntry(victimPPN);
                }
            }
            result = victimPPN;
        } else {
            result = pages[0];
        }
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
        return algorithm.findVictim();
    }

    public static void addCoff(int pid, Coff coff, int stackFrameCount) {
        _lock.acquire();
        int sectionCount = coff.getNumSections();
        int pageCount = 0;
        if (! TABLE.containsKey(pid)) {
            TABLE.put(pid, new HashMap<Integer, SwapAwareTranslationEntry>());
        }
        for (int i = 0; i < sectionCount; i++) {
            final CoffSection section = coff.getSection(i);
            addCoffSection(pid, section);
            pageCount += section.getLength();
        }
        final Map<Integer, SwapAwareTranslationEntry> pages = TABLE.get(pid);
        for (int i = 0; i < stackFrameCount; i++) {
            int vpn = pageCount + i;
            SwapAwareTranslationEntry sate = new SwapAwareTranslationEntry();
            pages.put(vpn, sate);
            sate.vpn = vpn;
            sate.valid = false;
            sate.readOnly = false;
            sate.isStack = true;
        }
        _lock.release();
    }

    public static void addCoffSection(int pid, CoffSection section) {
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
            sate.coffSection = section;
            sate.coffPage = i;
            sate.valid = false;
        }
    }

    public static void setVirtualUsed(int pid, int vpn) {
        _lock.acquire();
        SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
        	_lock.release();
            return;
        }
        entry.used = true;
        _lock.release();
    }

    public static void setVirtualWritten(int pid, int vpn) {
        setVirtualUsed(pid, vpn);
        _lock.acquire();
        SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
        	_lock.release();
            return;
        }
        entry.dirty = true;
        _lock.release();
    }

    public static TranslationEntry getTranslationEntryForVirtualPage(int pid, int vpn) {
        final SwapAwareTranslationEntry entry = getEntryFor(pid, vpn);
        if (null == entry) {
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

    private static Map<Integer, Map<Integer, SwapAwareTranslationEntry>>
        TABLE = new HashMap<Integer, Map<Integer, SwapAwareTranslationEntry>>();
    private static Map<Integer, List<SwapAwareTranslationEntry>>
        CORE_MAP = new HashMap<Integer, List<SwapAwareTranslationEntry>>();
    private static Lock _lock = new Lock();
    private static Algorithm algorithm = new RandomAlgorithm(CORE_MAP, _lock);
}
