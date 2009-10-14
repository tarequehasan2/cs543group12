package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

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

	console = new SynchConsole(Machine.console());
	
	Processor proc = Machine.processor();
	proc.setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });

	freePagesL = new Lock();
	int pages = proc.getNumPhysPages();
	for (int i = 0; i < pages; i++) {
		// initialize is called before anything else is running, so
		// no need to lock the freePages list at this point
		freePages.add(i);
	}
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
    }
    
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
    
    int[] malloc(int numPages) {
    	int[] result;
    	freePagesL.acquire();
    	if (numPages > freePages.size()) {
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
    
    void free(int page) {
    	freePagesL.acquire();
    	freePages.add(page);
    	freePagesL.release();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
    // don't initialize it statically, 
    // since Lock needs the KThread system to be running
    private static Lock freePagesL; 
    private static LinkedList<Integer> freePages
    	= new LinkedList<Integer>();
}
