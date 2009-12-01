package nachos.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.NetworkLink;
import nachos.threads.Condition;
import nachos.threads.Lock;

public class MessageDispatcher {
    public final int RECV_WINDOW = 16;

    public MessageDispatcher(/*PostOffice post, */ PostOfficeSender sender) {
//        _post = post;
        _sender = sender;
        _states = new HashMap<SocketKey, SocketState>();
        _queues = new HashMap<SocketKey, List<NachosMessage>>();
        _condLocks = new HashMap<Condition, Lock>();
        _connectConds = new HashMap<SocketKey, Condition>();
    }

    /**
     * Wait for incoming messages, and then put them in the correct mailbox.
     * Notify the ProtocolStateMachine for the specific Socket. <hr/>
     * <ol>
     * <li> If it's a DATA, put it in the mailbox for the incoming port.
     * If the client has violated the receive window, we can just drop their
     * Message without ACK-ing it.
     * <li> If it's a SYN packet, mark the state as SYN_RCVD
     * so an accept() will know to ask for a Message
     * <li> If it's a SYN-ACK packet, transition the socket to ESTABLISHED
     * and wake the connect Condition.
     * <li> If it's a ACK packet, find the SEQ in the POSender
     * <li> If it's a STP packet, inform the Socket no more writes if we have
     * more data, otherwise tell them FIN that we are talking too.
     * <li> If it's a FIN packet, reply with fin-ack and shut that socket down.
     * <li> If it's a FIN-ACK packet, notify it's actually closed
     * and can be dealloced </ol>
     */
    public void dispatch(NachosMessage msg) {
        final SocketEvent evt = SocketEvent.getEvent(msg);
        final SocketKey key = new SocketKey(msg);
        if (SocketEvent.DATA == evt) {
            if (getSocketState(key) != SocketState.ESTABLISHED) {
                debug("DROPPING new DATA due to not in ESTABLISHED condition");
                return;
            }
            if (addToQueue(msg)) {
                try {
                    _sender.send(NachosMessage.ack(msg));
                } catch (MalformedPacketException e) {
                    e.printStackTrace(System.err);
                    Lib.assertNotReached(e.getMessage());
                }
            }
        } else if (SocketEvent.SYN == evt) {
            if (getSocketState(key) != SocketState.CLOSED) {
                error("Trying to SYN a non-CLOSED port?!");
                return;
            }
            if (getSocketState(key) == SocketState.SYN_RCVD) {
                error("DROPPING extraneous SYN; hold your damn horses");
                return;
            }
            // hang on to this, because we're going to have to SYN/ACK it
            Lib.assertTrue(addToQueue(msg), "Full buffer for a SYN packet?!");
            // notify the accept() it can proceed now
            setSocketState(key, SocketState.SYN_RCVD);
        } else if (SocketEvent.SYNACK == evt) {
            // it's a SYN/ACK, so report that our SYN was successfully received
            _sender.ackMessage(msg);
            // SYNACKs don't need an ACK to be transmitted,
            // the flow of data will be their ACK
            Lib.assertTrue(SocketState.SYN_SENT ==
                    getSocketState(key),
                    "Protocol Error; expected SYN_SENT for SYNACK");
            Condition cond = _connectConds.get(key);
            Lock lck = _condLocks.get(cond);
            lck.acquire();
            // notify the connect() that her SYN/ACK arrived; proceed!
            cond.wake();
            lck.release();
            setSocketState(key, SocketState.ESTABLISHED);
        } else if (SocketEvent.ACK == evt) {
            _sender.ackMessage(msg);
        } else if (SocketEvent.STP == evt) {
            // report that we understood
            try {
                _sender.send(NachosMessage.ack(msg));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            // halt the flow of new messages
            _sender.stop(key);
            setSocketState(key, SocketState.STP_RCVD);
        } else if (SocketEvent.FIN == evt) {
            try {
                _sender.send(NachosMessage.ackFin(msg));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            setSocketState(key, SocketState.CLOSING);
        } else if (SocketEvent.FINACK == evt) {
            // tell the sender to clean up resources for that connection
            _sender.close(key);
            clearSocketState(key);
        } else {
            error("Unknown Dispatcher situation! := " + evt);
        }
    }

    public SocketKey accept(int port) {
        SocketKey result;
        SocketKey key = getSynLocalPortSocketKey(port);
        final NachosMessage synMsg = _queues.get(key).remove(0);
        Lib.assertTrue(synMsg.isSYN(),
                "Egad, how did you get accept() without a SYN message?");
        result = new SocketKey(synMsg);
        try {
            _sender.send(NachosMessage.ackSyn(synMsg));
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            Lib.assertNotReached(e.getMessage());
        }
        setSocketState(key, SocketState.ESTABLISHED);
        return result;
    }

    public SocketKey connect(int host, int port) {
        int localPort = findLocalPort();
        if (-1 == localPort) {
            error("Out of local ports?!");
            return null;
        }
        int localHost = Machine.networkLink().getLinkAddress();
        SocketKey key = new SocketKey(host, port, localHost, localPort);
        try {
            _sender.send(NachosMessage.syn(host, port, localHost, localPort));
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            Lib.assertNotReached(e.getMessage());
        }
        setSocketState(key, SocketState.SYN_SENT);
        if (!_connectConds.containsKey(key)) {
            Lock lck = new Lock();
            Condition cond = new Condition(lck);
            _condLocks.put(cond, lck);
            _connectConds.put(key, cond);
        }
        Condition cond = _connectConds.get(key);
        Lock lck = _condLocks.get(cond);
        lck.acquire();
        cond.sleep();
        lck.release();
        // we should have come out of this sleep
        // with the socket EST and ready to go
        Lib.assertTrue(getSocketState(key) == SocketState.ESTABLISHED);
        return key;
    }

    private int findLocalPort() {
        for (int i = 0; i < NachosMessage.PORT_LIMIT; i++) {
            boolean used = false;
            for (SocketKey key : _states.keySet()) {
                if (key.getDestPort() == i) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @see #pushBack(NachosMessage, int)
     */
    public NachosMessage nextData(SocketKey key) {
        return _queues.get(key).remove(0);
    }

    /**
     * Indicates that the entire message was not read, so the next read()
     * should get the same message, just with a smaller payload.
     * @param msg the message to be pushed back into the queue.
     * @param bytes the number of bytes <u>unread</u> from that message.
     */
    public void pushBack(NachosMessage msg, int bytes) {
        _queues.get(new SocketKey(msg))
                .add(0, NachosMessage.shorten(msg, bytes));
    }

    public void close(SocketKey key) {
        if (_sender.isQueueEmpty(key)) {
            // woo-hoo, we can just switch them off
            try {
                _sender.send(NachosMessage.fin(
                        key.getDestHost(), key.getDestPort(),
                        key.getSourceHost(), key.getSourcePort()));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            setSocketState(key, SocketState.CLOSING);
        } else {
            // drat, we have to request them to not send US anything,
            // but we still have data to send them
            try {
                _sender.send(NachosMessage.stp(
                        key.getDestHost(), key.getDestPort(),
                        key.getSourceHost(), key.getSourcePort()));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            // when the FIN comes back from them we'll actually clean up our end
            setSocketState(key, SocketState.STP_SENT);
        }
    }

    public boolean isInSynReceivedState(int port) {
        final SocketKey key = getSynLocalPortSocketKey(port);
        return null != key;
    }

    private SocketState getSocketState(SocketKey key) {
//    SocketKey key = newLocalPortSocketKey(port);
        if (!_states.containsKey(key)) {
            // if its the first we've heard of it, then its closed
            _states.put(key, SocketState.CLOSED);
        }
        return _states.get(key);
    }

    /**
     * Adds the specified NachosMessage to its correct receive queue, and
     * returns true iff I actually added your message.
     *
     * @param msg the message to enqueue
     * @return true iff the receive buffer has sufficient space for your
     *         message.
     */
    private boolean addToQueue(NachosMessage msg) {
        SocketKey key = new SocketKey(msg);
        if (!_queues.containsKey(key)) {
            _queues.put(key, new ArrayList<NachosMessage>());
        }
        final List<NachosMessage> list = _queues.get(key);
        if (list.size() >= RECV_WINDOW) {
            return false;
        }
        list.add(msg);
        return true;
    }

    private void setSocketState(SocketKey key, SocketState state) {
        _states.put(key, state);
    }

    private void clearSocketState(SocketKey key) {
        _states.remove(key);
    }

    /**
     * Returns a SocketKey which describes an unassociated local port.
     *
     * @param port the local port to describe
     * @return the SocketKey which describes the endpoint for that port.
     */
    private SocketKey getSynLocalPortSocketKey(int port) {
        for (SocketKey key : _states.keySet()) {
            if (key.getDestPort() == port &&
                    SocketState.SYN_RCVD == _states.get(key)) {
                return key;
            }
        }
        return null;
    }

    private void error(String msg) {
        System.err.println("ERROR:" + NetworkLink.networkID + "::" + msg);
    }

    private void debug(String msg) {
        System.err.println("MessageDispatch::DEBUG:"
                + NetworkLink.networkID + "::" + msg);
    }

    /**
     * Contains the Lock for each Condition.
     */
    private Map<Condition, Lock> _condLocks;
    /**
     * Contains the Condition for whomever wants to wait on a connection to the
     * given SocketKey.
     */
    private Map<SocketKey, Condition> _connectConds;
    private Map<SocketKey, SocketState> _states;
    /**
     * Maps between the connection descriptor to the NachosMessages waiting on
     * that socket endpoint. It may be a List, but we don't accept any more than
     * {@link #RECV_WINDOW} Messages.
     */
    private Map<SocketKey, List<NachosMessage>> _queues;
    private PostOfficeSender _sender;
//    private PostOffice _post;
}
