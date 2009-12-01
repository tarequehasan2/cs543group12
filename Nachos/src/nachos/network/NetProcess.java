package nachos.network;

import nachos.machine.Lib;
import nachos.vm.VMProcess;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends VMProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
        super();
        _kernel = ((NetKernel) NetKernel.kernel);
    }

    protected static final int
            syscallConnect = 11,
            syscallAccept = 12;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     * <p/>
     * <table> <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr> </table>
     *
     * @param    syscall    the syscall number.
     * @param    a0    the first syscall argument.
     * @param    a1    the second syscall argument.
     * @param    a2    the third syscall argument.
     * @param    a3    the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    @Override
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallAccept:
                return handleAccept(a0);
            case syscallConnect:
                return handleConnect(a0, a1);
            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }

    protected int handleAccept(int port) {
        debug("ACCEPT("+port+")");
        SocketKey key = _kernel.accept(port);
        if (null == key) {
            return -1;
        }
        return assignFdAndReturnIt(key);
    }

    protected int handleConnect(int host, int port) {
        debug("CONNECT("+host+","+port+")");
        SocketKey key = _kernel.connect(host, port);
        if (null == key) {
            error("Connect didn't return an ACK");
            return -1;
        }
        return assignFdAndReturnIt(key);
    }

    private int assignFdAndReturnIt(SocketKey key) {
        if (numOpenFiles < maxNumFiles){
        	for (int i = 0; i < fileDescriptors.length; i++){
        		if (fileDescriptors[i] == null){
        			fileDescriptors[i] = new SocketOpenFile(key);
        			numOpenFiles++;
        			return i;
        		}
        	}
        }
        return -1;
    }

    private void error(String msg) {
        System.err.println("ERROR:" + this + "::" + msg);
    }

    private void debug(String msg) {
        Lib.debug(dbgFlag, "DEBUG:" + this + "::" + msg);
    }

    @Override
    public String toString() {
        return "NetProcess["+getPid()+"]";
    }

    private NetKernel _kernel;
    private char dbgFlag = 'N';
}
