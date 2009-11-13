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
        SwapFileTest.selfTest();
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
    
    public int[] malloc(int numPages) {
    	int[] result = new int[numPages];
    	for (int i = 0; i < numPages; i++ ){
    		int ppn = InvertedPageTable.mallocOrSwap();
    		if (ppn == -1){
    			return null;
    		}else{
    			result[i] = ppn;
    		}
    	}
    	return result;
    }

    private void error(String message) {
        System.err.println("ERROR:VMKernel:"+message);
    }

    
    private void debug(String message) {
        Lib.debug(dbgVM, "DEBUG:VMKernel:"+message);
    }
    
    // dummy variables to make javac smarter
    private static VMProcess dummy1;

    private static final char dbgVM = 'v';
}
