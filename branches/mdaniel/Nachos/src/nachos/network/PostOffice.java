package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
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

        Lib.debug(dbgNet, "waiting for mail on port " + port);

        NachosMessage mail = (NachosMessage) dataQueues[port].removeFirst();

        Lib.debug(dbgNet, "got mail on port " + port + ": " + mail);

        return mail;
    }

    public NachosMessage nextSyn(int port) {
        Lib.assertTrue(port >= 0 && port < synQueues.length);

        Lib.debug(dbgNet, "waiting for SYN on port " + port);

        NachosMessage mail = (NachosMessage) synQueues[port].removeFirst();

        Lib.debug(dbgNet, "got mail on port " + port + ": " + mail);

        return mail;
    }

    /**
     * Wait for incoming messages, and then put them in the correct mailbox.
     */
    private void postalDelivery() {
        while (true) {
            messageReceived.P();

            Packet p = Machine.networkLink().receive();

            NachosMessage mail;
            try {
                mail = new NachosMessage(p);
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                continue;
            }


            // atomically add message to the mailbox and wake a waiting thread
            if (mail.isSYN()) {
                Lib.debug(dbgNet, "delivering SYN on port " + mail.getDestPort()
                            + ": " + mail);
                synQueues[mail.getDestPort()].add(mail);
            } else {
                Lib.debug(dbgNet, "delivering mail to port " + mail.getDestPort()
                            + ": " + mail);
                dataQueues[mail.getDestPort()].add(mail);
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
        Lib.debug(dbgNet, "sending mail: " + mail);

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

    private SynchList[] dataQueues;
    private SynchList[] synQueues;
    private Semaphore messageReceived;    // V'd when a message can be dequeued
    private Semaphore messageSent;    // V'd when a message can be queued
    private Lock sendLock;

    private static final char dbgNet = 'n';
}
