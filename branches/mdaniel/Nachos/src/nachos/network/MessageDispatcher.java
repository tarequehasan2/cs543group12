package nachos.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.threads.Condition;
import nachos.threads.Lock;

public class MessageDispatcher {
    public final int RECV_WINDOW = 16;

    public MessageDispatcher(PostOfficeSender sender) {
        _sender = sender;
        _states = new HashMap<SocketKey, SocketState>();
        _queues = new HashMap<SocketKey, List<NachosMessage>>();
        _queueLock = new Lock();
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
        final SocketState sockState = getSocketState(key);
        final int mySeq = msg.getSequence();
        if (!msg.isACK() && SequenceUtil.hasReceivedAMessage(key)) {
            final int minimumSeq = SequenceUtil.getRecvSequence(key);
            if (mySeq <= minimumSeq) {
                // okay, maybe it is stale, but it could be because the client
                // never heard our ACK; we'll just re-ACK it
                try {
                    _sender.send( NachosMessage.ack(msg) );
                } catch (MalformedPacketException e) {
                    e.printStackTrace(System.err);
                    Lib.assertNotReached(e.getMessage());
                }
                error("DROPPING stale Message\n\tMSG="+msg+"\n\tSEQ="+mySeq+", RCVD_SEQ="+minimumSeq);
            }
        }
        /// don't do this, otherwise we can't deal with out-of-order messages
//        if (SequenceUtil.hasSentAMessage(key)) {
//            final int maximumSeq = SequenceUtil.getSentSequence(key) + 1;
//            if (mySeq > maximumSeq) {
//                error("DROPPING psychic Message\n\tMSG="+msg+"\n\tSEQ="+mySeq+", SENT_SEQ="+maximumSeq);
//            }
//        }
        SequenceUtil.receivedSequence(key, mySeq);
        debug("[RECEIVE]\n\tMSG="+msg+"\n\tKEY="+key+"\n\tEVT="+evt+"\n\tSTAT="+sockState);
        if (SocketEvent.DATA == evt) {
            if (sockState != SocketState.ESTABLISHED) {
                debug("DROPPING new DATA due to not in ESTABLISHED condition");
                return;
            }
            if (addToQueue(key, msg)) {
                try {
                    _sender.send( NachosMessage.ack(msg) );
                } catch (MalformedPacketException e) {
                    e.printStackTrace(System.err);
                    Lib.assertNotReached(e.getMessage());
                }
            } else {
                error("Full RECV window; dropping "+msg);
            }
        } else if (SocketEvent.SYN == evt) {
            if (sockState == SocketState.SYN_RCVD) {
                error("DROPPING extraneous SYN; hold your damn horses");
                return;
            }
            if (sockState == SocketState.ESTABLISHED) {
                try {
                    _sender.send( NachosMessage.ackSyn(msg) );
                } catch (MalformedPacketException e) {
                    e.printStackTrace(System.err);
                    Lib.assertNotReached(e.getMessage());
                }
                return;
            }
            if (sockState != SocketState.CLOSED) {
                error("Trying to SYN a non-CLOSED ("+ sockState +") port?!");
                return;
            }
            // hang on to this, because we're going to have to SYN/ACK it
            Lib.assertTrue(addToQueue(key, msg), "Full buffer for a SYN packet?!");
            // notify the accept() it can proceed now
            setSocketState(key, SocketState.SYN_RCVD);
        } else if (SocketEvent.SYNACK == evt) {
            // it's a SYN/ACK, so report that our SYN was successfully received
            _sender.ackMessage(msg);
            if (SocketState.ESTABLISHED == sockState) {
                // it's just a dupe; we're already up and running
                return;
            }
            // SYNACKs don't need an ACK to be transmitted,
            // the flow of data will be their ACK
            Lib.assertTrue(SocketState.SYN_SENT ==
                    sockState,
                    "Protocol Error; expected SYN_SENT for SYNACK" +
                            "\nfound KEY="+key+"/STAT="+sockState
                        +"\nSTATES="+_states);
            Condition cond = _connectConds.get(key);
            Lock lck = _condLocks.get(cond);
            lck.acquire();
            // ensure we set this before releasing the connect()
            setSocketState(key, SocketState.ESTABLISHED);
            // notify the connect() that her SYN/ACK arrived; proceed!
            cond.wake();
            lck.release();
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
            setSocketState(key, SocketState.CLOSED);
        } else if (SocketEvent.FINACK == evt) {
            // tell the sender to clean up resources for that connection
            _sender.close(key);
            clearSocketState(key);
        } else {
            error("Unknown Dispatcher situation! := " + evt);
        }
    }

    public SocketKey accept(int port) {
        // we have to look it up by wildcard because we don't know the whole tuple
        SocketKey key = getSynLocalPortSocketKey(port);
        debug("ACCEPT-KEY="+key);
        final NachosMessage synMsg = getSynFromQueue(key);
        Lib.assertTrue(null != synMsg,
                "Egad, how did you get accept() without a SYN message?");
        Lib.assertTrue(synMsg.isSYN(),
                "Egad, I gave you a non-SYN message?!");
        try {
            _sender.send(NachosMessage.ackSyn(synMsg));
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            Lib.assertNotReached(e.getMessage());
        }
        setSocketState(key, SocketState.ESTABLISHED);
        return key;
    }

    public SocketKey connect(int host, int port) {
        int localPort = findLocalPort();
        if (-1 == localPort) {
            error("Out of local ports?!");
            return null;
        }
        int localHost = Machine.networkLink().getLinkAddress();
        SocketKey key = new SocketKey(host, port, localHost, localPort);
        debug("SYN-KEY="+key);
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
        SocketState newState = getSocketState(key);
        Lib.assertTrue(newState  == SocketState.ESTABLISHED,
                "Was not EST when our Condition released: "+newState
                +"\nSTATES="+_states);
        return key;
    }

    private int findLocalPort() {
        int result = -1;
        for (int i = 0; i < NachosMessage.PORT_LIMIT; i++) {
            boolean used = false;
            for (SocketKey key : _states.keySet()) {
                if (key.getDestPort() == i) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                result = i;
                break;
            }
        }
        return result;
    }

    /**
     * Gets the next DATA message from the receive queue for the provided
     * socket descriptor. Be aware that there may <em>be</em> data messages
     * in your receive queue, but if they are not the expected sequence,
     * you'll still get null until the missing Message arrives.
     * @param key the socket descriptor
     * @return the next available DATA message, or null if none exists.
     * @see #pushBack(NachosMessage, int)
     */
    public NachosMessage nextData(SocketKey key) {
        _queueLock.acquire();
        if (! _queues.containsKey(key)) {
            _queueLock.release();
            return null;
        }

        final List<NachosMessage> receiveQ = _queues.get(key);
        if (receiveQ.isEmpty()) {
            _queueLock.release();
            return null;
        }

//        final int currentSeq = SequenceUtil.querySequence(key);
//        if (-1 == currentSeq) {
//            _queueLock.release();
//            return null;
//        }

//        final int expectedSeq = currentSeq + 1;
        NachosMessage result = null;
        final int qLen = receiveQ.size();

        // Hide non-data messages,
        // or it's possible read() might eat a control message
        for (int i = 0; i < qLen; i++) {
            NachosMessage msg = receiveQ.get(i);
            if (! msg.isData()) {
                continue;
            }
//            final int seq = msg.getSequence();
//            Lib.assertTrue(seq >= expectedSeq,
//                    "Egad, a dupe got into the receive queue at "+i+":" +
//                            "MSG="+msg+"\nSEQ="+expectedSeq);
//            if (seq == expectedSeq) {
                result = receiveQ.remove(i);
                break;
//            }
        }
//        if (null == result) {
//            error("nextData:currentSeq="+currentSeq+";expectedSeq="+expectedSeq+";Q="+receiveQ);
//        }
        _queueLock.release();
        return result;
    }

    /**
     * Indicates that the entire message was not read, so the next read()
     * should get the same message, just with a smaller payload.
     * @param msg the message to be pushed back into the queue.
     * @param bytes the number of bytes <u>unread</u> from that message.
     */
    public void pushBack(NachosMessage msg, int bytes) {
        _queueLock.acquire();
        _queues.get(new SocketKey(msg))
                .add(0, NachosMessage.shorten(msg, bytes));
        _queueLock.release();
    }

    public void close(SocketKey key) {
        if (getSocketState(key) == SocketState.CLOSED) {
            // they've already left, no need to inform them
            return;
        }
        final int destHost = key.getSourceHost();
        final int destPort = key.getSourcePort();
        final int srcHost = key.getDestHost();
        final int srcPort = key.getDestPort();
        if (_sender.isQueueEmpty(key)) {
            // woo-hoo, we can just switch them off
            try {
                _sender.send(NachosMessage.fin(destHost, destPort, srcHost, srcPort));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            setSocketState(key, SocketState.CLOSING);
        } else {
            // drat, we have to request them to not send US anything,
            // but we still have data to send them
            try {
                _sender.send(NachosMessage.stp(destHost, destPort, srcHost, srcPort));
            } catch (MalformedPacketException e) {
                e.printStackTrace(System.err);
                Lib.assertNotReached(e.getMessage());
            }
            // when the FIN comes back from them we'll actually clean up our end
            setSocketState(key, SocketState.STP_SENT);
        }
    }

    /**
     * Returns true iff there is a SYN message addressed to the specified
     * local port.
     * @param port the local port to check
     * @return true if that local port is in SYN_RCVD state.
     */
    public boolean isInSynReceivedState(int port) {
        final SocketKey key = getSynLocalPortSocketKey(port);
        return null != key;
    }

    /**
     * Extracts the SYN Message from the receive queue. It <b>better</b> be
     * the first Message in the designated queue.
     * @param key the socket descriptor who's SYN we're looking for.
     * @return the SYN message or null if none exists.
     */
    private NachosMessage getSynFromQueue(SocketKey key) {
        _queueLock.acquire();
        NachosMessage result;
        if (! _queues.containsKey(key)) {
            _queueLock.release();
            return null;
        }
        final List<NachosMessage> list = _queues.get(key);
        if (list.isEmpty()) {
            result = null;
        } else {
            result = list.remove(0);
        }
        _queueLock.release();
        return result;
    }

    private SocketState getSocketState(SocketKey key) {
        if (! _states.containsKey(key)) {
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
    private boolean addToQueue(SocketKey key, NachosMessage msg) {
        _queueLock.acquire();
        if (!_queues.containsKey(key)) {
            _queues.put(key, new ArrayList<NachosMessage>());
        }
        final List<NachosMessage> list = _queues.get(key);
        if (list.size() >= RECV_WINDOW) {
            _queueLock.release();
            return false;
        }
        list.add(msg);
        _queueLock.release();
        return true;
    }

    private void setSocketState(SocketKey key, SocketState state) {
        _states.put(key, state);
    }

    /**
     * This is called from the Timer to clean up cases where
     * we didn't receive the FINACK.
     */
    public void clearClosingStates() {
        for (SocketKey key : _states.keySet()) {
            if (SocketState.CLOSING == getSocketState(key)) {
                debug("Clearing state CLOSING "+key);
                setSocketState(key, SocketState.CLOSED);
            }
        }
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
        SocketKey result = null;
        for (SocketKey key : _states.keySet()) {
            if (key.getDestHost() == Machine.networkLink().getLinkAddress() &&
                key.getDestPort() == port &&
                    SocketState.SYN_RCVD == _states.get(key)) {
                result = key;
                break;
            }
        }
        return result;
    }

    private void error(String msg) {
        final int address = Machine.networkLink().getLinkAddress();
        final String s = "ERROR:MessageDispatch@"
                + address + "::" + msg;
        Lib.debug('n', s);
    }

    private void debug(String msg) {
        final int address = Machine.networkLink().getLinkAddress();
        final String s = "DEBUG:MessageDispatch@"
                + address + "::" + msg;
        Lib.debug('n', s);
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
    private Lock _queueLock;
    /**
     * Maps between the connection descriptor to the NachosMessages waiting on
     * that socket endpoint. It may be a List, but we don't accept any more than
     * {@link #RECV_WINDOW} Messages.
     */
    private Map<SocketKey, List<NachosMessage>> _queues;
    private PostOfficeSender _sender;
}
