package nachos.vm;

import nachos.machine.Lib;
import nachos.threads.Lock;
import nachos.userprog.UserKernel;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */
    public VMKernel() {
	    super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        debug("initialize("+java.util.Arrays.asList(args)+")");
    	super.initialize(args);
    }

    /**
     * Test this kernel.
     */
    public void selfTest() {
        debug("selfTest()");
    	super.selfTest();
//        SwapFileTest.selfTest();
//        Lib.assertTrue(false, "self test");
    }

    /**
     * Start running user programs.
     */
    public void run() {
        debug("run()");
        // I know it looks goofy to initialize a static variable
        // from an instance method but we can't alloc them statically since
        // the Machine isn't running at class creation time
        pageFaultsLock = new Lock();
        memoryLock = new Lock();
    	super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        debug("terminate()");
        SwapFile.close();
        debug("closed SwapFile");
        System.out.println("VMM Paging: page faults "+pageFaults);
    	super.terminate();
    }

    /**
     * Indicates there was a page fault. We cannot use the existing Processor
     * page fault tracking mechanism due to visibility constraints within
     * Nachos.
     */
    public static void recordPageFault() {
        pageFaultsLock.acquire();
        pageFaults++;
        pageFaultsLock.release();
    }

    public static void lockMemory() {
        memoryLock.acquire();
    }

    public static void unlockMemory() {
        memoryLock.release();
    }

//    private static void error(String message) {
//        Lib.error("ERROR:VMKernel:"+message);
//    }

    private static void debug(String message) {
        Lib.debug(dbgFlag, "DEBUG:VMKernel:"+message);
    }

    private static int pageFaults;
    // don't initialize these Locks statically
    // since the Machine isn't running at Kernel class load time
    private static Lock pageFaultsLock;
    private static Lock memoryLock;
    private static final char dbgFlag = 'K';
}
