package nachos.vm;

import nachos.machine.Lib;
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
    	super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        debug("terminate()");
        SwapFile.close();
        debug("closed SwapFile");
    	super.terminate();
    }

//    private void error(String message) {
//        Lib.error("ERROR:VMKernel:"+message);
//    }


    private void debug(String message) {
        Lib.debug(dbgFlag, "DEBUG:VMKernel:"+message);
    }

    private static final char dbgFlag = 'K';
}
