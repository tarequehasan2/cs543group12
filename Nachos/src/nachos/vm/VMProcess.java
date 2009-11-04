package nachos.vm;

import java.util.Hashtable;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
	super();
	//jjt
	Swap_File_Name = "swap";
    }

    //jjt 
    class PageTableKey {
    	public int proccess_Id;
    	public int Vpage_number;
    	public boolean equals(Object obj) { 
    		
    		if (!(obj instanceof PageTableKey)) {
    			return false;
    		}
    		PageTableKey proccess_Id_Vpage_number = (PageTableKey)obj  ;
    		
    		
			return 
			     (proccess_Id_Vpage_number.proccess_Id == this.proccess_Id)
			     &&
			     (proccess_Id_Vpage_number.Vpage_number == this.Vpage_number);
		}
    	
    }
    
	static class PageTable {
		static Lock lock = new Lock();
		static Condition f_pages = new Condition(lock);
		static Hashtable<PageTableKey, TranslationEntry> p_table = new Hashtable<PageTableKey, TranslationEntry>(Machine.processor().getNumPhysPages());
		static LinkedList<Integer> free = new LinkedList<Integer>();
		static LinkedList<TranslationEntry> recentlyUsed = new LinkedList<TranslationEntry>();
		
		static {
			for(int i = 0; i < Machine.processor().getNumPhysPages(); i++) {
				free.addLast(i);
			}
		}
		
		public static TranslationEntry get(PageTableKey Pid_Vpn) { 
			VMProcess process = (VMProcess)VMKernel.currentProcess();
			TranslationEntry entry = p_table.get(Pid_Vpn);
			if(entry == null) {
				
				if(free.size() == 0) {
					remove();
				}
				int index = free.removeFirst();
				// TODO
				entry = Swap.search(Pid_Vpn);
				if(entry != null) {
					entry.ppn = index;
					//TODO
					Swap.read_entry(entry);
				} else {
					
					if(Pid_Vpn.Vpage_number < process.numPages) {
						entry = new TranslationEntry(Pid_Vpn.Vpage_number, index, true, false, false, false);
						if(Pid_Vpn.Vpage_number < (process.numPages - (1 + process.stackPages))) {
							for(int s = 0; s < process.coff.getNumSections(); s++) {
								CoffSection section = process.coff.getSection(s);
								if(Pid_Vpn.Vpage_number < (section.getFirstVPN() + section.getLength())) {
									entry.readOnly = section.isReadOnly();
									section.loadPage(Pid_Vpn.Vpage_number - section.getFirstVPN(), index);
									break;
								}
							}
						} else {
							
							java.util.Arrays.fill(Machine.processor().getMemory(), index * pageSize, (index + 1) * pageSize, (byte) 0); 
						}
					} else {
						process.handleSyscall(0, process.getPid(), 0, 0, 0);
					}
				}
				p_table.put(Pid_Vpn, entry);
			} else {
				recentlyUsed.remove(entry);
			}
			recentlyUsed.addFirst(entry);
			return entry;
		}
		
		
		public static void remove(PageTableKey Pid_Vpn, boolean swapOut) {
			TranslationEntry entry = p_table.remove(Pid_Vpn);
			if(entry != null) {
				if(swapOut) {
					// TODO 
					Swap.write_entry(entry);
				}
				//TODO remove from TLB
				free.add(entry.ppn);
				recentlyUsed.remove(entry);
			}
		}
		
		static void remove() {
			while(recentlyUsed.size() == 0) {
				PageTable.f_pages.sleep();
			}
			VMProcess process = (VMProcess)VMKernel.currentProcess();
			PageTableKey eliminate = process.new PageTableKey();
			eliminate.proccess_Id = process.getPid();
			eliminate.Vpage_number = recentlyUsed.getLast().vpn;
			remove(eliminate, true);
		}
	}
	
   //TODO 
	static class Swap {
		
		public static TranslationEntry search(PageTableKey Pid_Vpn) {
			return null;
		}
		//TODO
		public static void read_entry(TranslationEntry entry) {
			
		}
		//TODO
		public static void write_entry(TranslationEntry entry) {
			
		}
		//TODO
		public static void remove_entry(PageTableKey Pid_Vpn) {
			
		}
				
	}
	
    //jjt  
    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
	super.saveState();
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	super.restoreState();
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
	return super.loadSections();
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	super.unloadSections();
    }    

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	default:
	    super.handleException(cause);
	    break;
	}
    }
	// jjt
    public static String Swap_File_Name;
    
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final char dbgVM = 'v';
}
