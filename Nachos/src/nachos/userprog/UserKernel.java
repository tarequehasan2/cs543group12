package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
	super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	// have to initialize the lock here, rather than statically,
	// because at <clinit> time the threading system isn't running
	// causing the Lock constructor to fail
	freePagesL = new Lock();
	int pages = Machine.processor().getNumPhysPages();
	for (int i = 0; i < pages; i++) {
		// initialize is called before anything else is running, so
		// no need to lock the freePages list at this point
		freePages.add(i);
	}
	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
    	
    }

    // disabled because does not work with paged memory
    public void selfTest1() {
	super.selfTest();

	System.out.println("Testing the console device. Typed characters");
	System.out.println("will be echoed until q is typed.");

	char c;

	do {
	    c = (char) console.readByte(true);
	    console.writeByte(c);
	}
	while (c != 'q');

	System.out.println("");
	// Testing handleRead() & handleWrite()
	// 
	UserProcess  myread = UserProcess.newUserProcess();
	myread.writeVirtualMemory(100, "myfile.txt".getBytes()); //write string to memory (pointer)
	int fd =  myread.handleSyscall(4, 100, 0, 0, 0);  // create a file
	myread.writeVirtualMemory(500, "my textdata".getBytes()); // more write string to memory (pointer)
	myread.handleSyscall(7, fd, 500, 11, 0); // Writing Data
	myread.handleSyscall(8, fd, 0, 0, 0); // Close file
	fd =  myread.handleSyscall(5, 100, 0, 0, 0); //Open, Mem Aloc, not use
	int byteRead = myread.handleSyscall(6, fd, 200, 20, 0); // Reading
	String mystr = String.valueOf(byteRead);  // Write to console
	for (int i=0;i < mystr.length(); i++){    // Write ro console
		console.writeByte(mystr.charAt(i));
	}
	byte[] mybuffer = new byte[byteRead];  // create buffer
	myread.readVirtualMemory(200, mybuffer); // put file contents in the buffer
	for (int i=0;i < mybuffer.length; i++){   // Writes the buffer to the console
		console.writeByte(mybuffer[i]);
	}
	myread.handleSyscall(8, fd, 0, 0, 0);  // Close the file
	console.writeByte('\n');
	myread.handleSyscall(1, myread.getPid(), 0, 0, 0);  // handleExit();
	
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }
    
    /**
     * Requests one or more <b>physical</b> pages from the kernel's memory
     * pool. The page numbers range from <tt>0</tt> 
     * to <tt>{@link Processor#getNumPhysPages()}</tt>, inclusive.
     * Please note that the pages are not guaranteed to be contiguous, 
     * but we'll do our best.
     * @param numPages the number of pages you require.
     * @return the <b>physical</b> page numbers
     */
    int[] malloc(int numPages) {
    	int[] result;
    	freePagesL.acquire();
    	if (numPages > freePages.size()) {
    		freePagesL.release();
    		return null;
    	}
    	result = new int[numPages];
    	for (int i = result.length - 1; i >= 0; i--) {
    		// use removeLast to expose pageTable errors
    		// but malloc them in reverse order 
    		// so the request for contiguous memory is honored
    		result[i] = freePages.removeLast();
    	}
    	freePagesL.release();
    	return result;
    }
    
    /**
     * Deallocates the <b>physical</b> page numbers provided. 
     * @param pages the array of <b>physical</b> page numbers.
     */
    void free(int[] pages) {
    	if (null == pages || 0 == pages.length) {
    		return;
    	}
    	for (int i = 0; i < pages.length; i++) {
    		free(pages[i]);
    	}
    }
    
    /**
     * Deallocates the <b>physical</b> page number provided. 
     * @param pages the <b>physical</b> page number.
     */
    void free(int page) {
    	freePagesL.acquire();
    	if (!freePages.contains(page)) {
    		freePages.add(page);
    	}
    	freePagesL.release();
    }


    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    static Coff dummy1;
    
    // don't initialize it statically, 
    // since Lock needs the KThread system to be running
    private static Lock freePagesL; 
    private static LinkedList<Integer> freePages
    	= new LinkedList<Integer>();
}
