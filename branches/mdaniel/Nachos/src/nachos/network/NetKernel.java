package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.threads.Lock;
import nachos.vm.VMKernel;

/**
 * A kernel with network support.
 */
public class NetKernel extends VMKernel {
    /**
     * Allocate a new networking kernel.
     */
    public NetKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        localPort = Byte.MAX_VALUE - 1;
        portLock = new Lock();
        postOffice = new PostOffice();
        myLinkID = Machine.networkLink().getLinkAddress();
    }

    /**
     * Test the network. Create a server thread that listens for pings on port 1
     * and sends replies. Then ping one or two hosts. Note that this test
     * assumes that the network is reliable (i.e. that the network's reliability
     * is 1.0).
     */
    public void selfTest() {
        NachosMessageTest.selfTest();
    }


    /**
     * Start running user programs.
     */
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    public NachosMessage accept(int port) {
        // grab a pending connection and ACK it
        //debug("SYN("+port+")?");
        NachosMessage syn = postOffice.nextSyn(port);
        if (null != syn) {
            debug("SYN("+port+") := "+syn);
            NachosMessage ack;
            try {
                ack = NachosMessage.ack(syn);
            } catch (MalformedPacketException e) {
                Lib.assertNotReached(e.getMessage());
                return null;
            }
            debug("ACK("+port+") -> ("+ack.getDestHost()+","+ack.getDestPort()+")");
            postOffice.send(ack);
            // now the connection is established
            debug("SYN-ACK complete("+port+"); welcome host:"+syn.getSourceHost());
        }
        return syn;
    }

    public NachosMessage connect(int host, int port) {
        // grab next free local port number
        final int localPort = nextLocalPort();
        // send a SYN request and then wait for the response
        NachosMessage syn;
        try {
            syn = NachosMessage.syn(host, port);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached(e.getMessage());
            return null;
        }
        postOffice.send(syn);
        debug("Waiting on ACK from "+host+":"+port);
        NachosMessage ack = postOffice.receive(localPort);
        Lib.assertTrue(ack.getSourceHost() == host,
                "Wrong host! wanted "+host+" but got "+ack.getSourceHost());
        return ack;
    }

    public int write(int host, int port, int localPort,
                      byte[] data, int offset, int len) {
        final String qualifier = "(" + host + "," + port + "," + myLinkID + "," + localPort + ")";
        // compose up to NachosMessage.MAX_CONTENTS_LENGTH chunk, and tack it
        // into the outgoing queue for the given (host,port) tuple
        int written = 0;
        for (int i = 0; i < len; i += NachosMessage.MAX_CONTENTS_LENGTH) {
            byte[] contents = new byte[ len - offset ];
            System.arraycopy(data, i, contents, 0, contents.length );
            NachosMessage datagram;
            try {
                datagram = new NachosMessage(host, port, myLinkID, localPort, contents);
            } catch (MalformedPacketException e) {
                error(qualifier+".write := " +e.getMessage());
                return -1;
            }
            // TX queue.add(datagram)
            debug("DATA:="+datagram);
            written += contents.length;
        }
        return written;
    }

    public int close(int host, int port, int localPort) {
        final String qualifier = "(" + host + "," + port + "," + myLinkID + "," + localPort + ")";
        // send the STP
        NachosMessage stp;
        try {
            stp = NachosMessage.stp(host, port);
        } catch (MalformedPacketException e) {
            error(qualifier+".close STP := " +e.getMessage());
            return -1;
        }
        debug(qualifier+":STP := "+stp);
        postOffice.send(stp);
        // wait for the FIN
        debug(qualifier+": awaiting FIN");
        NachosMessage fin = postOffice.receive(localPort);
        // send the FIN-ACK
        NachosMessage finAck;
        try {
            finAck = NachosMessage.newFinAck(fin);
        } catch (MalformedPacketException e) {
            error(qualifier+".close FINACK := "+e.getMessage());
            return -1;
        }
        debug(qualifier+":FINACK := "+finAck);
        postOffice.send(finAck);
        return 0;
    }

    private void error(String msg) {
        System.err.println("ERROR:NetKernel::"+msg);
    }

    private void debug(String msg) {
        Lib.debug(dbgFlag, msg);
    }

    static int nextLocalPort() {
        int result;
        portLock.acquire();
        result = localPort--;
        if (0 == localPort) {
            localPort = Byte.MAX_VALUE - 1;
        }
        portLock.release();
        return result;
    }


    private PostOffice postOffice;
    private static Lock portLock;
    /**
     * The local port number assigned to connections.
     * Guarded by {@link #portLock}.
     */
    private static int localPort;
    private int myLinkID;
    private char dbgFlag = 'K';
}
