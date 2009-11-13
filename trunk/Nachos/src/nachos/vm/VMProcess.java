package nachos.vm;

import nachos.machine.Coff;
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
/*
        boolean intStatus = Machine.interrupt().disable();
        tlbLock.acquire();
        final Processor proc = Machine.processor();
        final int tlbSize = proc.getTLBSize();
		for (int i=0; i < tlbSize; i++){
			TranslationEntry entry = proc.readTLBEntry(i);
            debug("ProcTLB["+i+"]:="+entry);
			entry.valid = false; // force a TLB miss
			proc.writeTLBEntry(i, entry);
    	}
        tlbLock.release();
        Machine.interrupt().setStatus(intStatus);
*/
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    @Override
    public void restoreState() {
        debug("restoreState()");
        debug("TLB:restoring");
        boolean intStatus = Machine.interrupt().disable();
        tlbLock.acquire();
        final Processor proc = Machine.processor();
        final int tlbSize = proc.getTLBSize();
		for (int i=0; i < tlbSize; i++){
			TranslationEntry entry = proc.readTLBEntry(i);
            debug("ProcTLB["+i+"]:="+entry);
			entry.valid = false; // force a TLB miss
			proc.writeTLBEntry(i, entry);
    	}
        tlbLock.release();
        Machine.interrupt().setStatus(intStatus);
        debug("TLB: restored");
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
        boolean result;
        InvertedPageTable.addCoff(this, stackPages);
        debug("PC=0x"+Integer.toHexString(coff.getEntryPoint()));
        result = true;
        debug("loadSections() <- "+result);
        return result;
    }

    @Override
    protected void allocPageTable() {
        // we override this just to keep the superclass from allocing a pageTable
        debug("allocPageTable()");
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    @Override
    protected void unloadSections() {
        debug("unloadSections()");
        final int pid = getPid();
        // the order matters here
        SwapFile.free( InvertedPageTable.findAllSwapPagesByPid(pid) );
        InvertedPageTable.free(pid);
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        boolean intStatus = Machine.interrupt().disable();
        memoryLock.acquire();
        int result;
        int vpn = Processor.pageFromAddress(vaddr);
        InvertedPageTable.setVirtualUsed(this, vpn);
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
        int vpn = Processor.pageFromAddress(vaddr);
        InvertedPageTable.setVirtualWritten(this, vpn);
        result = super.writeVirtualMemory(vaddr, data, offset, length);
        memoryLock.release();
        Machine.interrupt().setStatus(intStatus);
        return result;
    }

    /**
     * Looks up the requested virtual page table entry for the current process.
     * @param vpn the virtual page whose page table entry we want
     * @return the page table entry for the provided page, or null if not found.
     */
    @Override
    protected TranslationEntry getTranslationEntryForVirtualPage(int vpn) {
        TranslationEntry entry;
        entry = ProcessorUtil.findProcTLBforVpn(vpn);
        if (null != entry && entry.valid) {
        	return entry;
        }
        final int pid = getPid();
        entry = InvertedPageTable.findIPTEntryForVpn(pid, vpn);
        if (null == entry) {
            error("Unmapped page table entry for VPN: "+vpn);
            return null; // kaboom!
        }
        if (! entry.valid) {
            debug("Loading ("+pid+","+vpn+") because UserProcess asked for it");
            InvertedPageTable.handleTLBMiss(this, vpn);
        }
        return entry;
    }

    /**
     * Provides physical address translation via our new management of
     * page table entries instead of per-process page tables.
     * @param vaddr the complete virtual address to translate.
     * @return the complete physical address of the provided virtual address.
     */
    @Override
    protected int convertVaddrToPaddr(int vaddr) {
		int vPageNumber = Processor.pageFromAddress(vaddr);
		int pageAddress = Processor.offsetFromAddress(vaddr);
		TranslationEntry translationEntry
                = getTranslationEntryForVirtualPage(vPageNumber);
		if (null == translationEntry) {
			// TODO: bus error? page fault?
			error("Unmapped page table entry for VPN "+vPageNumber);
			return -1;
		}
		int pPageNumber = translationEntry.ppn;

        if (0 > pPageNumber || pPageNumber >= Machine.processor().getNumPhysPages()) {
            Lib.assertTrue(false, "physical page out of bounds: "+pPageNumber);
            return -1;
        }

		if (pageAddress < 0 || pageAddress >= Processor.pageSize) {
			error("bogus pageAddress: "+pageAddress);
		    return -1;
		}

		return Processor.makeAddress(pPageNumber, pageAddress);
	}

    /**
     * Provides access to the COFF that this VMProcess is currently running.
     * @return the source COFF instance.
     */
    Coff getCoff() {
        return coff;
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
//        debug("handleException("+cause+")");
	    Processor processor = Machine.processor();

        // take advantage of this call back into the OS to sync up the TLB
        InvertedPageTable.syncAllProcTlb();

	    switch (cause) {
        case Processor.exceptionTLBMiss: {
            int badVaddr = processor.readRegister(Processor.regBadVAddr);
            debug("TLB Miss @x"+Integer.toHexString(badVaddr));
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

    /**
     * Provides helper logic for dealing with a TLB miss.
     * Be advised that if we are not able to reconcile your TLB miss, we will
     * terminate your process with exit(1).
     * @param vaddr the virtual address of the memory access which caused the
     * TLB miss.
     */
    protected void handleTLBMiss(int vaddr) {
//        boolean intStatus = Machine.interrupt().disable();
        int page = Processor.pageFromAddress(vaddr);
        debug("vaddr("+Integer.toHexString(vaddr)+"):=pid="+getPid()+";vpn="+page);
        if (!InvertedPageTable.handleTLBMiss(this, page)) {
            error("Unable to handle TLB miss; exit(1)");
//            Machine.interrupt().setStatus(intStatus);
            final int exitCode = 1;
            handleSyscall(syscallExit, exitCode, 0, 0, 0);
//            return;
        }
//        Machine.interrupt().setStatus(intStatus);
    }

    private void error(String message) {
        System.err.println("ERROR:"+toString()+":"+message);
    }

	private void debug(String message) {
        Lib.debug(dbgFlag,"DEBUG:"+toString()+":"+message);
    }

    private static final char dbgFlag = 'P';
    private static final Lock tlbLock = new Lock();
    /**
     * Provides just a VMProcess-scoped lock on page reads and writes,
     * to prevent the pages from being swapped out from underneath us.
     * If you want to lock the whole memory, see {@link VMKernel#lockMemory()}.
     */
    private static final Lock memoryLock = new Lock();
}
