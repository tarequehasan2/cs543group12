package nachos.network;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;
import nachos.threads.Condition;
import nachos.threads.KThread;
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
        postOffice = new PostOffice();
        postOfficeSender = new PostOfficeSender(postOffice);
        new KThread(postOfficeSender).fork();
        new KThread(new TimerEventHandler(postOfficeSender)).fork();
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
    	PostOfficeSender.terminate();
    	TimerEventHandler.terminate();
        super.terminate();

    }

    /**
     * Grab the next SYN packet from the receive queue,
     * send a SYN/ACK and return the SYN which shows the remote host, remote
     * port, local host, local port and SEQ.
     * @param port the port in the legal range to check for connections.
     * @return the SYN message showing all relevant endpoint information.
     */
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
            postOfficeSender.send(ack);
            // now the connection is established
            debug("SYN-ACK complete("+port+"); welcome host:"+syn.getSourceHost());
        }
        return syn;
    }

    /** Allows the system to ack a specific sequence number. */
    public void reportAck(NachosMessage message) {
        postOfficeSender.ackMessage(message);
    }

    public void wakeConnect(NachosMessage synAck) {
        SocketKey key = new SocketKey(
                synAck.getDestHost(), synAck.getDestPort(), -1, -1);
        final Condition cond = connectConds.get(key);
        final Lock lock = condLocks.get(cond);
        lock.acquire();
        cond.wake();
        lock.release();
    }

    public void scheduleClose(SocketOpenFile sock) {
    }

    private java.util.Map<Condition, Lock> condLocks;
    private java.util.Map<SocketKey, Condition> connectConds;
    /**
     * Send a SYN packet and await the SYN/ACK showing the connection is wired up.
     * The protocol spec says this should wait on a Condition.
     * @param host host id
     * @param port port
     * @return the message containing all the info
     */
    public NachosMessage connect(int host, int port) {
        Lock l = new Lock();
        Condition cond = new Condition(l);
        condLocks.put(cond, l);
        connectConds.put(new SocketKey(host, port, -1, -1), cond);
        l.acquire();
        cond.sleep();
        l.release();
        return null;
    }

    /**
     * Pull a DATA packet out of the receive queue, or 0 if empty, or -1 on error.
     * @param msg the socket's syn message for which we are reading
     * @param data the buffer into which we put data
     * @param offset but not below this offset
     * @param len but not exceeding this length
     * @return the bytes read
     */
    public int read(NachosMessage msg,
                      byte[] data, int offset, int len) {
        return -1;
    }
    public int write(NachosMessage msg,
                      byte[] data, int offset, int len) {
        final int destHost = msg.getDestHost();
        final int destPort = msg.getDestPort();
        final int srcPort = msg.getSourcePort();
        final int srcHost = msg.getSourceHost();
        final String qualifier = "(" + destHost + "," + destPort
                + "," + srcHost + "," + srcPort + ")";
        // compose up to NachosMessage.MAX_CONTENTS_LENGTH chunk, and tack it
        // into the outgoing queue for the given (host,port) tuple
        int written = 0;
        for (int i = 0; i < len; i += NachosMessage.MAX_CONTENTS_LENGTH) {
            byte[] contents = new byte[ len - offset ];
            System.arraycopy(data, i, contents, 0, contents.length );
            NachosMessage datagram;
            try {
                datagram = new NachosMessage(destHost, destPort, srcHost, srcPort,
                        contents);
            } catch (MalformedPacketException e) {
                error(qualifier+".write := " +e.getMessage());
                return -1;
            }
            debug("DATA:="+datagram);
            postOfficeSender.send(datagram);
            written += contents.length;
        }
        return written;
    }

    private void error(String msg) {
        System.err.println("ERROR:NetKernel::"+msg);
    }

    private void debug(String msg) {
        Lib.debug(dbgFlag, msg);
    }

    private PostOffice postOffice;
    private PostOfficeSender postOfficeSender;
    private char dbgFlag = 'K';
}
