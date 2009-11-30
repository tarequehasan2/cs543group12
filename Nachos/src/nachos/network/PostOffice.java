package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.NetworkLink;
import nachos.machine.Packet;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.Semaphore;
import nachos.threads.SynchList;

/**
 * A collection of message dataQueues, one for each local port. A
 * <tt>PostOffice</tt> interacts directly with the network hardware. Because of
 * the network hardware, we are guaranteed that messages will never be
 * corrupted, but they might get lost.
 * <p/>
 * <p/>
 * The post office uses a "postal worker" thread to wait for messages to arrive
 * from the network and to place them in the appropriate dataQueues. This cannot be
 * done in the receive interrupt handler because each queue (implemented with a
 * <tt>SynchList</tt>) is protected by a lock.
 */
public class PostOffice {
    /**
     * Allocate a new post office, using an array of <tt>SynchList</tt>s.
     * Register the interrupt handlers with the network hardware and start the
     * "postal worker" thread.
     */
    public PostOffice() {
        _kernel = (NetKernel) NetKernel.kernel;
        messageReceived = new Semaphore(0);
        messageSent = new Semaphore(0);
        sendLock = new Lock();

        dataQueues = new SynchList[ NachosMessage.PORT_LIMIT ];
        for (int i = 0; i < dataQueues.length; i++) {
            dataQueues[i] = new SynchList();
        }

        synQueues = new SynchList[ NachosMessage.PORT_LIMIT ];
        for (int i = 0; i < synQueues.length; i++) {
            synQueues[i] = new SynchList();
        }

        Runnable receiveHandler = new Runnable() {
            public void run() {
                receiveInterrupt();
            }
        };
        Runnable sendHandler = new Runnable() {
            public void run() {
                sendInterrupt();
            }
        };
        Machine.networkLink().setInterruptHandlers(
                receiveHandler,
                sendHandler);

        KThread t = new KThread(new Runnable() {
            public void run() {
                postalDelivery();
            }
        });

        t.fork();
    }

    /**
     * Retrieve a message on the specified port, waiting if necessary.
     *
     * @param    port    the port on which to wait for a message.
     * @return the message received.
     */
    public NachosMessage receive(int port) {
        Lib.assertTrue(port >= 0 && port < dataQueues.length);

        debug("waiting for mail on port " + port);

        NachosMessage mail = (NachosMessage) dataQueues[port].removeFirst();

        SocketEvent event = SocketEvent.getEvent(mail);
        SocketOpenFile socket = NetProcess.sockets.get(new SocketKey(mail));

      try {
		SocketTransition.doEvent(socket, event );
	} catch (FailSyscall e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ProtocolError e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ProtocolDeadlock e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

        debug("got mail on port " + port + ": " + mail);

        return mail;
    }

    public NachosMessage nextSyn(int port) {
        Lib.assertTrue(port >= 0 && port < synQueues.length);

        // debug("waiting for SYN on port " + port);

        NachosMessage mail = (NachosMessage)
                synQueues[port].removeFirstWithoutBlocking();
        if (null != mail) {
            debug("got SYN on port " + port + ": " + mail);
        }

        return mail;
    }

    /**
     * Wait for incoming messages, and then put them in the correct mailbox.
     * Notify the ProtocolStateMachine for the specific Socket.
     * <hr/>
     * <ol>
     * <li> If it's a DATA, put it in the mailbox for the incoming port.
     * <li> If it's a SYN packet, put in it in the SYN queue
     * <li> If it's a SYN-ACK packet, transition the socket to ESTABLISHED and wake the connect Condition.
     * <li> If it's a ACK packet, find the SEQ in the POSender
     * <li> If it's a STP packet, inform the Socket no more writes
     * <li> If it's a FIN packet, reply with fin-ack
     * <li> If it's a FIN-ACK packet, notify it's actually closed and can be dealloced
     * </ol>
     */
    private void postalDelivery() {
        while (true) {
            messageReceived.P();

            Packet p = Machine.networkLink().receive();

            NachosMessage msg;
            try {
                msg = new NachosMessage(p);
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                continue;
            }
            // 1.
            if (! (msg.isData())) {
                debug("delivering mail to port " + msg.getDestPort()
                            + ": " + msg);
                dataQueues[msg.getDestPort()].add(msg);
                continue;
            } else
            // 2.
            if (msg.isSYN() && !msg.isACK()) {
                debug("delivering SYN on port " + msg.getDestPort()
                            + ": " + msg);
                synQueues[msg.getDestPort()].add(msg);
                continue;
            } else
            // 3.
            if (msg.isSYN() && msg.isACK()) {

                psm.getMachine(new SocketKey(msg))
                        .onSYNACK();
                _kernel.wakeConnect(msg);
                continue;
            }
            // 4.
            if (msg.isACK()) {
                _kernel.reportAck(msg.getSequence());
                continue;
            } else
            // 5.
            if (msg.isSTP()) {
                continue;
            } else
            // 6.
            if (msg.isFIN() && !msg.isACK()) {
                continue;
            } else
            // 7.
            if (msg.isFIN() && msg.isACK()) {
                continue;
            } else {
                error("Unknown PostOffice situation!");
            }
        }
    }

    /**
     * Called when a packet has arrived and can be dequeued from the network
     * link.
     */
    private void receiveInterrupt() {
        messageReceived.V();
    }

    /**
     * Send a message to a mailbox on a remote machine.
     */
    public void send(NachosMessage mail) {
        debug("sending mail: " + mail);

        sendLock.acquire();

        Machine.networkLink().send(mail.toPacket());
        messageSent.P();

        sendLock.release();
    }

    /**
     * Called when a packet has been sent and another can be queued to the
     * network link. Note that this is called even if the previous packet was
     * dropped.
     */
    private void sendInterrupt() {
        messageSent.V();
    }

    private void error(String msg) {
        System.err.println("ERROR:"+NetworkLink.networkID+"::"+msg);
    }

    private void debug(String msg) {
        Lib.debug(dbgNet, "DEBUG:"+NetworkLink.networkID+"::"+msg);
        System.out.println("DEBUG:"+NetworkLink.networkID+"::"+msg);
    }

    private SynchList[] dataQueues;
    private SynchList[] synQueues;
    private Semaphore messageReceived;    // V'd when a message can be dequeued
    private Semaphore messageSent;    // V'd when a message can be queued
    private Lock sendLock;
    private ProtocolStateMachine psm;
    /** Convenience variable instead of casting all the time. */
    private NetKernel _kernel;
    private static final char dbgNet = 'n';
}
