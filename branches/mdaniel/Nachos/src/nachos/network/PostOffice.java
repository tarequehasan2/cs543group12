package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.Semaphore;

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

//        dataQueues = new SynchList[ NachosMessage.PORT_LIMIT ];
//        for (int i = 0; i < dataQueues.length; i++) {
//            dataQueues[i] = new SynchList();
//        }

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
        t.setName("PostOfficeDelivery");
        t.fork();
    }

    /**
     * Retrieve a message on the specified port, waiting if necessary.
     *
     * @param    port    the port on which to wait for a message.
     * @return the message received.
     */
//    public NachosMessage receive(int port) {
//        Lib.assertTrue(port >= 0 && port < dataQueues.length);
//
//        debug("waiting for mail on port " + port);
//        NachosMessage mail = (NachosMessage) dataQueues[port].removeFirst();
//        debug("got mail on port " + port + ": " + mail);
//
//        return mail;
//    }

    /**
     * Wait for incoming messages, and ask the kernel to dispatch them.
     */
    private void postalDelivery() {
        while (true) {
            messageReceived.P();

            Packet p = Machine.networkLink().receive();

            NachosMessage msg;
            try {
                msg = new NachosMessage(p);
                debug("postalDelivery("+msg+")");
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                continue;
            }
            _kernel.dispatch(msg);
            KThread.yield();
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
    	SocketKey key = new SocketKey(mail).reverse();
    		if (mail.getSourceHost() != Machine.networkLink().getLinkAddress() )
    		mail.setFromKey(key);
        debug("sending mail: " + mail);

        sendLock.acquire();

        try {

            Machine.networkLink().send(mail.toPacket());
            messageSent.P();
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
        }

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

//    private void error(String msg) {
//        System.err.println("ERROR:HOST["+Machine.networkLink().getLinkAddress()+"]::"+msg);
//    }

    private void debug(String msg) {
        Lib.debug(dbgNet, "DEBUG:HOST["+Machine.networkLink().getLinkAddress()+"]::"+msg);
    }

//    private SynchList[] dataQueues;
    /** V'd when a message can be dequeued. */
    private Semaphore messageReceived;
    /** V'd when a message can be queued. */
    private Semaphore messageSent;
    private Lock sendLock;
    /** Convenience variable instead of casting all the time. */
    private NetKernel _kernel;
    private static final char dbgNet = 'n';
}
