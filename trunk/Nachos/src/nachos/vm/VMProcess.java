package nachos.vm;

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
	private TranslationEntry[] state = new TranslationEntry[Machine.processor().getTLBSize()];
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    @Override
    public void saveState() {
        debug("saveState()");
        debug("TLB: saving state and invalidating");
        boolean intStatus = Machine.interrupt().disable();
        tlbLock.acquire();
        final Processor proc = Machine.processor();
        final int tlbSize = proc.getTLBSize();
        for (int i=0; i < tlbSize; i++){
    		state[i] = proc.readTLBEntry(i);
    		TranslationEntry translationEntry = new TranslationEntry(state[i]);
    		translationEntry.valid = false;
            proc.writeTLBEntry(i,translationEntry);
    	}
        tlbLock.release();
        Machine.interrupt().setStatus(intStatus);
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
        for (int i=0; i < proc.getTLBSize(); i++){
    		if (state[i] != null) {
    		    proc.writeTLBEntry(i, state[i]);
    		}
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
        InvertedPageTable.addCoff(getPid(), coff, stackPages);
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
        InvertedPageTable.free(getPid());
        SwapFile.free( InvertedPageTable.findAllSwapPagesByPid(getPid()) );
    }

    @Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        boolean intStatus = Machine.interrupt().disable();
        memoryLock.acquire();
        int result;
        int vpn = Processor.pageFromAddress(vaddr);
        InvertedPageTable.setVirtualUsed(getPid(), vpn);
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
        InvertedPageTable.setVirtualWritten(getPid(), vpn);
        result = super.writeVirtualMemory(vaddr, data, offset, length);
        memoryLock.release();
        Machine.interrupt().setStatus(intStatus);
        return result;
    }

    @Override
    protected TranslationEntry getTranslationEntryForVirtualPage(int vpn) {
        final TranslationEntry entry = InvertedPageTable.getTranslationEntryForVirtualPage(getPid(), vpn);
        if (null == entry) {
            error("Unmapped page table entry for VPN: "+vpn);
            return null; // kaboom!
        }
        return entry;
    }

    @Override
    protected int convertVaddrToPaddr(int vaddr) {
		int vPageNumber = Processor.pageFromAddress(vaddr);
		int pageAddress = Processor.offsetFromAddress(vaddr);
		TranslationEntry translationEntry = getTranslationEntryForVirtualPage(vPageNumber);
		if (null == translationEntry) {
			// TODO: bus error? page fault?
			error("Unmapped page table entry for VPN "+vPageNumber);
			return -1;
		}
		// TODO: validity check?
		int pPageNumber = translationEntry.ppn;

		if (pageAddress < 0 || pageAddress >= Processor.pageSize) {
			error("bogus pageAddress: "+pageAddress);
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


    protected void handleTLBMiss(int vaddr) {
        boolean intStatus = Machine.interrupt().disable();
        int page = Processor.pageFromAddress(vaddr);
        debug("vaddr("+Integer.toHexString(vaddr)+"):=pid="+getPid()+";vpn="+page);
        if (!InvertedPageTable.handleTLBMiss(getPid(), page)) {
            error("Unable to handle TLB miss");
        }
        Machine.interrupt().setStatus(intStatus);
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
    private static final Lock tlbLock = new Lock();
    private static final Lock memoryLock = new Lock();
}
