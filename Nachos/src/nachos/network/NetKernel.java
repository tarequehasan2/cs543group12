package nachos.network;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.vm.VMKernel;

/**
 * A kernel with network support.
 */
public class NetKernel extends VMKernel
{
    /**
     * Allocate a new networking kernel.
     */
    public NetKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    @Override
    public void initialize(String[] args) {
        super.initialize(args);
        _acceptLock = new Lock();
        _acceptPids = new java.util.HashMap<Integer, Integer>();
        PostOffice post = new PostOffice();
        postOfficeSender = new PostOfficeSender(post);
        dispatcher = new MessageDispatcher(/* post, */ postOfficeSender);
        new KThread(postOfficeSender)
                .setName("PostOfficeSender").fork();
        new KThread(new TimerEventHandler(postOfficeSender))
                .setName("TimerEvent").fork();
    }

    /**
     * Test the network. Create a server thread that listens for pings on port 1
     * and sends replies. Then ping one or two hosts. Note that this test
     * assumes that the network is reliable (i.e. that the network's reliability
     * is 1.0).
     */
    @Override
    public void selfTest() {
        NachosMessageTest.selfTest();
    }


    /**
     * Start running user programs.
     */
    @Override
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    @Override
    public void terminate() {
    	PostOfficeSender.terminate();
    	TimerEventHandler.terminate();
        super.terminate();

    }

    public void scheduleClose(SocketOpenFile sock) {
        debug("close("+sock+")");
        dispatcher.close(sock.getKey());
    }

    public void dispatch(NachosMessage msg) {
        debug("dispatch "+msg);
        dispatcher.dispatch(msg);
    }

    /**
     * Returns the SocketKey describing the accepted connection, or null
     * if no connections are waiting.
     * @param port the local port to accept upon.
     * @return
     */
    public SocketKey accept(int port) {
        _acceptLock.acquire();
        final int currentPid = currentProcess().getPid();
        if (_acceptPids.containsKey(port) && _acceptPids.get(port) != currentPid) {
            error("Attempt to double-accept on port "+port
                    +"; conflicts with PID "+_acceptPids.get(port));
            _acceptLock.release();
            return null;
        } else {
            _acceptPids.put(port, currentPid);
            _acceptLock.release();
        }
        if (! dispatcher.isInSynReceivedState(port)) {
            return null;
        }
        debug("WooHoo SYN on "+port);
        return dispatcher.accept(port);
    }

    public SocketKey connect(int host, int port) {
        debug("connect("+host+","+port+")");
        return dispatcher.connect(host, port);
    }

    /**
     * Pull a DATA packet out of the receive queue, or 0 if empty, or -1 on error.
     * @param key the socket key for which we are reading
     * @param data the buffer into which we put data
     * @param offset but not below this offset
     * @param len but not exceeding this length
     * @return the bytes read
     */
    public int read(SocketKey key,
                      byte[] data, int offset, int len) {
        final int destHost = key.getDestHost();
        final int destPort = key.getDestPort();
        final int srcPort = key.getSourcePort();
        final int srcHost = key.getSourceHost();
        final String qualifier = "(" + destHost + "," + destPort
                + "," + srcHost + "," + srcPort + ")";
//        if (SocketState.ESTABLISHED == dispatcher.getSocketState(key.get))
        NachosMessage msg = dispatcher.nextData(key);
        if (msg == null){
        	return 0;
        }
        int realLen = len - offset;
        debug(qualifier+"DATA:="+msg+",want "+realLen+" bytes of it");
        byte[] payload = msg.getPayload();
        Lib.assertTrue(null != payload, "Egad, null payload?");
        if (payload.length > realLen) {
            final int pushbackBytes = payload.length - realLen;
            debug(qualifier+"pushing back "+pushbackBytes);
            dispatcher.pushBack(msg, pushbackBytes);
        }
        final int numBytes = Math.min(payload.length, realLen);
        System.arraycopy(payload, 0, data, offset, numBytes);
        return numBytes;
    }

    public int write(SocketKey key,
                      byte[] data, int offset, int len) {
        final int destHost = key.getDestHost();
        final int destPort = key.getDestPort();
        final int srcPort = key.getSourcePort();
        final int srcHost = key.getSourceHost();
        final String qualifier = "(" + destHost + "," + destPort
                + "," + srcHost + "," + srcPort + ")";
        // compose up to NachosMessage.MAX_CONTENTS_LENGTH chunk, and tack it
        // into the outgoing queue for the given (host,port) tuple
        final int realLen = len - offset;
        debug(qualifier+":write.realLen = "+realLen);
        for (int i = 0; i < realLen; /*empty*/ ) {
            byte[] contents = new byte[ realLen ];
            System.arraycopy(data, i, contents, 0, realLen );
            // set up for the next iteration
            i += contents.length;
            NachosMessage datagram;
            try {
                datagram = new NachosMessage(destHost, destPort, srcHost, srcPort,
                        contents);
            } catch (MalformedPacketException e) {
                error(qualifier+".write := " +e.getMessage());
                return -1;
            }
            debug(qualifier+".WRITE-DATA:="+datagram);
            postOfficeSender.send(datagram);
        }
        return realLen;
    }

    private void error(String msg) {
        System.err.println("ERROR:NetKernel::"+msg);
    }

    private void debug(String msg) {
        Lib.debug(dbgFlag, msg);
    }

    /** Protects {@link #_acceptPids}. */
    private Lock _acceptLock;
    /**
     * Contains a mapping between local ports
     * to the PIDs which are accepting on them.
     */
    private java.util.Map<Integer, Integer> _acceptPids;
    private MessageDispatcher dispatcher;
    private PostOfficeSender postOfficeSender;
    private char dbgFlag = 'K';
}
