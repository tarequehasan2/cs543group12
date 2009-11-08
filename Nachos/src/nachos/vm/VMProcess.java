package nachos.vm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess
{
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    @Override
    public void saveState() {
        debug("saveState()");
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    @Override
    public void restoreState() {
        debug("restoreState()");
        debug("TLB:flushing");
        boolean intStatus = Machine.interrupt().disable();
        _tlbLock.acquire();
        final Processor proc = Machine.processor();
        final int tlbMax = proc.getTLBSize();
        for (int i = 0; i < tlbMax; i++) {
            TranslationEntry entry = proc.readTLBEntry(i);
            entry.valid = false;
            proc.writeTLBEntry(i, entry);
        }
        _tlbLock.release();
        Machine.interrupt().setStatus(intStatus);
        debug("TLB:flush OK");
    }

    /**
     * Constructs a new {@link TranslationEntry} and initializes it to be
     * not valid, read-only, not used, not dirty with vpn and ppn of -1.
     * @return a freshly initialized TranslationEntry.
     */
    private TranslationEntry newTranslationEntry() {
        int vpn = -1;
        int ppn = -1;
        boolean valid = false;
        boolean readOnly = true;
        boolean used = false;
        boolean dirty = false;
        return new TranslationEntry(vpn, ppn, valid, readOnly, used, dirty);
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    @Override
    protected boolean loadSections() {
        debug("loadSections()");

        final VMKernel kernel = (VMKernel)Kernel.kernel;
        
        boolean result;

        Map<Integer, TranslationEntry> entryMap = getEntryMap();
        int sections = coff.getNumSections();
        for (int i = 0; i < sections; i++) {
            CoffSection section = coff.getSection(i);
            debug( "\tinitializing " + section.getName()
                  + " section (" + section.getLength() + " pages)");
            int firstVpn = section.getFirstVPN();
            int len = section.getLength();
            for (int s = 0; s < len; s++) {
                int myVpn = firstVpn + s;
                /// TODO: this is where lazy loading takes place; q.v. stack frames below
                final int[] pages = kernel.malloc(1);
                final int ppn;
                if (null == pages || 0 == pages.length) {
                    boolean intStatus = Machine.interrupt().disable();
                    memoryLock.acquire();
                    int luckyPage = findRollOutPage();
                    SwapFile.rollOut(getPid(), luckyPage);
                    invalidateEntryPageForPpn(luckyPage);
                    memoryLock.release();
                    Machine.interrupt().setStatus(intStatus);
                    ppn = luckyPage;
                } else {
                    ppn = pages[0];
                }
                TranslationEntry entry = newTranslationEntry();
                entry.readOnly = section.isReadOnly();
                entry.valid = true;
                entry.vpn = myVpn;
                entry.ppn = ppn;
                storeTLBEntry(entry);
                section.loadPage(s, ppn);
            }
        }
        int coffPages = entryMap.size();
        int stackPages = super.numPages - coffPages;
        for (int i = 0; i < stackPages; i++) {
            final int stackVpn = coffPages + i;
            TranslationEntry entry = newTranslationEntry();
            entry.vpn = stackVpn;
            // purposefully don't set ppn for stack pages
            entry.ppn = -1;
            entry.readOnly = false;
            entry.valid = true;
            entryMap.put(stackVpn, entry);
        }
        debug("loadSections entryMap := "+entryMap);
        result = true;
        debug("loadSections() <- "+result);
        return result;
    }

    private void invalidateEntryPageForPpn(int ppn) {
        TranslationEntry item = null;
        for (TranslationEntry entry : getEntryMap().values()) {
            if (entry.ppn == ppn) {
                item = entry;
                break;
            }
        }
        if (null != item) {
            storeSwappedEntry(item);
            getEntryMap().remove(item.vpn);
        }
    }

    private void storeSwappedEntry(TranslationEntry item) {
        Collection<TranslationEntry> entries = SwappedPages.get(getPid());
        if (null == entries) {
            entries = new java.util.ArrayList<TranslationEntry>();
            SwappedPages.put(getPid(), entries);
        }
        entries.add(item);
    }

    protected int findRollOutPage() {
        int result;
        java.util.List<TranslationEntry> pool = new java.util.LinkedList<TranslationEntry>();
        for (Map<Integer,TranslationEntry> pages : MyTLBMap.values()) {
            for (TranslationEntry entry : pages.values()) {
                if (entry.dirty) continue;
                if (!entry.valid) continue;
                if (entry.used) continue;
                if (-1 == entry.ppn) continue;
                pool.add(entry);
            }
        }
        int lottery = new java.util.Random().nextInt(pool.size());
        result = pool.get(lottery).ppn;
        debug("Lottery found "+result);
        return result;
    }

    @Override
    protected void allocPageTable() {
        debug("allocPageTable()");
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    @Override
    protected void unloadSections() {
        debug("unloadSections()");
    	VMKernel.free(this.getPid());
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        boolean intStatus = Machine.interrupt().disable();
        memoryLock.acquire();
        int result;
        result = super.readVirtualMemory(vaddr, data, offset, length);
        memoryLock.release();
        Machine.interrupt().setStatus(intStatus);
        return result;
    }

    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        int result;
        boolean intStatus = Machine.interrupt().disable();
        memoryLock.acquire();
        result = doWriteVirtualMemory(vaddr, data, offset, length);
        memoryLock.release();
        Machine.interrupt().setStatus(intStatus);
        return result;
    }

    private static final Lock memoryLock = new Lock();

    protected int doWriteVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

        int vpn = Processor.pageFromAddress(vaddr);
        TranslationEntry pageEntry = getEntryMap().get(vpn);
        if (null == pageEntry) {
            if (swapContainsVPN(vpn)) {
                int pages[] = ((VMKernel)Kernel.kernel).malloc(1);
                if (null == pages || 0 == pages.length) {
                    error("Egad, have to swap to get the swap back in");
                    return 0;
                }
                SwapFile.rollIn(getPid(), pages[0]);
                pageEntry = findEntryForVpn(vpn);
                pageEntry.ppn = pages[0];
                SwappedPages.remove(getPid());
            }
        }
        if (null == pageEntry) {
            error("Attempting to write to an unmapped page");
            // TODO: signal page fault? bus error?
            return 0;
        }

        if (-1 == pageEntry.ppn) {
            if (! mallocStackFrame(pageEntry)) {
                return 0;
            }
        }

        if (pageEntry.readOnly) {
            error("attempting to write to read-only memory");
            return 0;
        }

        int paddr = convertVaddrToPaddr(vaddr);
        if (-1 == paddr) {
            error("Unable to convert virtual ("+vaddr+") to physical");
            return 0;
        }

        byte[] memory = Machine.processor().getMemory();

        if (paddr < 0 || paddr >= memory.length) {
            error("Attempt to write out of entire memory bounds");
            return 0;
        }

        int amount = Math.min(length, memory.length-paddr);
        System.arraycopy(data, offset, memory, paddr, amount);

        return amount;
    }

    private TranslationEntry findEntryForVpn(int vpn) {
        TranslationEntry result = null;
        final Collection<TranslationEntry> entries = SwappedPages.get(getPid());
        for (TranslationEntry entry : entries) {
            if (entry.vpn == vpn) {
                result = entry;
                break;
            }
        }
        return result;
    }

    protected boolean mallocStackFrame(TranslationEntry entry) {
        if (-1 == entry.ppn) {
            debug("StackFrame["+entry.vpn+"]: alloc()ing");
            int[] pages = ((VMKernel) Kernel.kernel).malloc(1);
            if (null == pages || 0 == pages.length) {
                error("Unable to alloc stack frame for VPN("+entry.vpn+")");
                return false;
            }
            entry.ppn = pages[0];
            entry.valid = true;
            entry.dirty = false;
            entry.used = false;
            storeTLBEntry(entry);
        } else {
            error("Requested a stack frame malloc() on a non-stack frame TE: "
                    +entry);
            return false;
        }
        return true;
    }

    /**
     * Retrieves the EntryMap for the current process's PID, and ensures
     * the result is never null (updating the original Map if necessary).
     * @return a non-null Entry Map.
     */
    private Map<Integer, TranslationEntry> getEntryMap() {
        Map<Integer, TranslationEntry> entryMap = MyTLBMap.get(getPid());
        if (null == entryMap) {
            entryMap = new HashMap<Integer, TranslationEntry>();
            MyTLBMap.put(getPid(), entryMap);
        }
        return entryMap;
    }

    protected void storeTLBEntry(TranslationEntry entry) {
        getEntryMap().put(entry.vpn, entry);
    }

    @Override
    protected int convertVaddrToPaddr(int vaddr) {
        int vPageNumber = Processor.pageFromAddress(vaddr);
        int pageAddress = Processor.offsetFromAddress(vaddr);
        final Map<Integer, TranslationEntry> entryMap = getEntryMap();
        if (vPageNumber >= entryMap.size()) {
            error("Requested a page number (" + vPageNumber
                    + ") outside the page mapping (" + pageTable.length + " total)");
            return -1;
        }
        TranslationEntry translationEntry = entryMap.get(vPageNumber);
        if (null == translationEntry) {
            // TODO: bus error? page fault?
            error("Unmapped page table entry for VPN " + vPageNumber);
            return -1;
        }
        // TODO: validity check?
        int pPageNumber = translationEntry.ppn;

        if (pageAddress < 0 || pageAddress >= Processor.pageSize) {
            debug("bogus pageAddress: " + pageAddress+" outside "+Processor.pageSize);
            return -1;
        }

        return Processor.makeAddress(pPageNumber, pageAddress);
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    @Override
    public void handleException(int cause) {
        debug("handleException("+cause+")");
	Processor processor = Machine.processor();

	switch (cause) {
        case Processor.exceptionTLBMiss: {
            int badVaddr = processor.readRegister(Processor.regBadVAddr);
            debug("TLB Miss @ "+badVaddr);
            handleTLBMiss(badVaddr);
            break;
        }
    	default:
	        super.handleException(cause);
    	    break;
	    }
    }

    @Override
    public String toString() {
        return "VMProcess[pid="+getPid()+"]";
    }

    private static Lock _tlbLock = new Lock();

    protected void handleTLBMiss(int vaddr) {
        boolean intStatus = Machine.interrupt().disable();
        _tlbLock.acquire();
        int page = Processor.pageFromAddress(vaddr);
//        int offset = Processor.offsetFromAddress(vaddr);

        debug("vaddr("+Integer.toHexString(vaddr)+"):=pid="+getPid()+";vpn="+page);

        final Processor proc = Machine.processor();

        Map<Integer, TranslationEntry> entryMap = getEntryMap();
        if (null != entryMap && entryMap.containsKey(page)) {
            // just needs to be put back in the cache
            TranslationEntry entry = entryMap.get(page);
            // stack frame, needs to be alloced
            if (-1 == entry.ppn) {
                if (!mallocStackFrame(entry)) {
                    error("Unable to malloc stack frame");
                    _tlbLock.release();
                    Machine.interrupt().setStatus(intStatus);
                    return;
                }
            }
            int frame = entry.ppn;
            entry.valid = true;
            debug("I've seen that page before, it's at "+frame);

            int tlbSize = proc.getTLBSize();
            int tlbEntryNum = -1;
            for (int i = 0; i < tlbSize; i++) {
                if (! proc.readTLBEntry(i).valid) {
                    tlbEntryNum = i;
                    break;
                }
            }
            if (-1 == tlbEntryNum) {
                tlbEntryNum = new java.util.Random().nextInt(tlbSize);
            }
            debug("ejecting "+tlbEntryNum
                    +" old:="+proc.readTLBEntry(tlbEntryNum)+" new:="+entry);
            debug("expect write at "
                    +Integer.toHexString(
                    Processor.makeAddress(entry.ppn, Processor.offsetFromAddress(vaddr))
                    )
            );
            proc.writeTLBEntry(tlbEntryNum, entry);
        } else if (swapContainsVPN(page)) {
        } else {
            error("VPN["+page+"] needs to be loaded for pid="+getPid());
        }
        _tlbLock.release();
        Machine.interrupt().setStatus(intStatus);
    }

    private boolean swapContainsVPN(int page) {
        for (java.util.Collection<TranslationEntry> entries : SwappedPages.values()) {
            for (TranslationEntry entry : entries) {
                if (entry.vpn == page) {
                    return true;
                }
            }
        }
        return false;
    }

    private void error(String message) {
        System.err.println("ERROR:"+toString()+":"+message);
    }

	private void debug(String message) {
        Lib.debug(dbgVM,"DEBUG:"+toString()+":"+message);
    }

//    private static final int pageSize = Processor.pageSize;
//    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
    /**
     * Maps <tt>(getPid()+","+virtualPage)</tt>
     * to the physical frame which contains it.
     */
    private static Map<Integer, Map<Integer, TranslationEntry>>
        MyTLBMap = new HashMap<Integer, Map<Integer, TranslationEntry>>();
    private static Map<Integer, java.util.Collection<TranslationEntry>>
        SwappedPages = new HashMap<Integer, java.util.Collection<TranslationEntry>>();
}
