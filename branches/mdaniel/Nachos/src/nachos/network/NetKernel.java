package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.threads.KThread;
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
        myLinkID = Machine.networkLink().getLinkAddress();
    }

    /**
     * Test the network. Create a server thread that listens for pings on port 1
     * and sends replies. Then ping one or two hosts. Note that this test
     * assumes that the network is reliable (i.e. that the network's reliability
     * is 1.0).
     */
    public void selfTest() {
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

    public MailMessage accept(int port) {
        final int localPort = 0;
        // grab a pending connection and ACK it
        MailMessage syn = postOffice.receiveNB(port);
        if (null != syn) {
            byte[] payload = new byte[0];
            MailMessage ack;
            try {
                ack = new MailMessage(
                        syn.packet.srcLink, syn.srcPort,
                        myLinkID, localPort, payload);
            } catch (MalformedPacketException e) {
                Lib.assertNotReached(e.getMessage());
                return null;
            }
            postOffice.send(ack);
            // now the connection is established
        }
        return syn;
    }

    public MailMessage connect(int host, int port) {
        // grab next free local port number
        final int localPort = 0;
        // send a SYN request and then wait for the response
        byte[] payload = new byte[0];
        MailMessage syn;
        try {
            syn = new MailMessage(host, port, myLinkID, localPort, payload);
        } catch (MalformedPacketException e) {
            Lib.assertNotReached(e.getMessage());
            return null;
        }
        postOffice.send(syn);
        debug("Waiting on ACK from "+host+":"+port);
        MailMessage ack = postOffice.receive(localPort);
        Lib.assertTrue(ack.packet.srcLink == host,
                "Wrong host! wanted "+host+" but got "+ack.packet.srcLink);
        return ack;
    }

    public int write(int host, int port, int localPort,
                      byte[] data, int offset, int len) {
        final String qualifier = "(" + host + "," + port + "," + myLinkID + "," + localPort + ")";
        // compose up to MailMessage.maxContentsLength chunk, and tack it
        // into the outgoing queue for the given (host,port) tuple
        int written = 0;
        for (int i = 0; i < len; i += MailMessage.maxContentsLength) {
            byte[] contents = new byte[ 0 ];
            System.arraycopy(data, i, contents, 0, contents.length );
            MailMessage datagram;
            try {
                datagram = new MailMessage(host, port, myLinkID, localPort, contents);
            } catch (MalformedPacketException e) {
                error(qualifier+".write := " +e.getMessage());
                return -1;
            }
            // TX queue.add(datagram)
            written += contents.length;
        }
        return written;
    }

    public int close(int host, int port, int localPort) {
        final String qualifier = "(" + host + "," + port + "," + myLinkID + "," + localPort + ")";
        byte[] contents = new byte[0];
        // send the STP
        MailMessage stp;
        try {
            stp = new MailMessage(host, port, myLinkID, localPort, contents);
        } catch (MalformedPacketException e) {
            error(qualifier+".close STP := " +e.getMessage());
            return -1;
        }
        debug(qualifier+":STP := "+stp);
        postOffice.send(stp);
        // wait for the FIN
        debug(qualifier+": awaiting FIN");
        MailMessage fin = postOffice.receive(localPort);
        // send the FIN-ACK
        MailMessage finAck;
        try {
            finAck = new MailMessage(fin.packet.srcLink, fin.srcPort, myLinkID, localPort, contents);
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


    private PostOffice postOffice;
    private int myLinkID;
    private char dbgFlag = 'K';

    private void ping_selfTest() {
        super.selfTest();

        KThread serverThread = new KThread(new Runnable() {
            public void run() {
                pingServer();
            }
        });

        serverThread.fork();

        System.out.println("Press any key to start the network test...");
        console.readByte(true);

        int local = Machine.networkLink().getLinkAddress();

        // ping this machine first
        ping(local);
        System.err.println("Self Ping OK");

        // if we're 0 or 1, ping the opposite
        ping(0 == local ? 1 : 0);
    }

    /**
     * Pings always go from port 0 to port 1, and then are reflected back.
     *
     * @param dstLink the network link to ping.
     */
    private void ping(int dstLink) {
        final int srcLink = Machine.networkLink().getLinkAddress();
        final int srcPort = 0;
        final int dstPort = 1;

        System.out.println("PING " + dstLink + ":" + dstPort
                + " from " + srcLink + ":" + srcPort);

        long startTime = Machine.timer().getTime();

        MailMessage ping;

        try {
            ping = new MailMessage(dstLink, dstPort, srcLink, srcPort,
                    new byte[0]);
        }
        catch (MalformedPacketException e) {
            Lib.assertNotReached();
            return;
        }

        System.out.println("Sending PING");
        postOffice.send(ping);
        System.out.println("Waiting for ACK");
        MailMessage ack = postOffice.receive(srcPort);
        System.out.println("Received ACK from " + ack.packet.srcLink + ":" + ack.srcPort);

        long endTime = Machine.timer().getTime();

        System.out.println("time=" + (endTime - startTime) + " ticks");
    }

    private void pingServer() {
        while (true) {
            System.out.println("Waiting on Ping(:1) ...");
            MailMessage ping = postOffice.receive(1);

            MailMessage ack;

            try {
                ack = new MailMessage(ping.packet.srcLink, ping.srcPort,
                        ping.packet.dstLink, ping.dstPort,
                        ping.contents);
            }
            catch (MalformedPacketException e) {
                Lib.assertNotReached("should never happen haha");
                continue;
            }
            System.out.println("Sending ACK to " + ack.packet.dstLink + ":" + ack.dstPort);
            postOffice.send(ack);
            System.out.println("ACK away");
        }
    }
}
