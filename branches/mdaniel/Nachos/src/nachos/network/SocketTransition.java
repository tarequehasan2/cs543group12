package nachos.network;

import java.util.HashMap;
import java.util.Map;

import static nachos.network.SocketState.*;

import nachos.threads.Lock;

public final class SocketTransition
{
    public static void doEvent(SocketKey key, SocketEvent event)
            throws FailSyscall, ProtocolError, ProtocolDeadlock {
        switch (getState(key)) {
            case CLOSED:
                doClosed(key, event);
                break;
            case SYN_SENT:
                doSynSent(key, event);
                break;
            case SYN_RCVD:
                doSynRcvd(key, event);
                break;
            case ESTABLISHED:
                doEstablished(key, event);
                break;
            case STP_RCVD:
                doStpRcvd(key, event);
                break;
            case STP_SENT:
                doStpSent(key, event);
                break;
            case CLOSING:
                doClosing(key, event);
                break;
        }
    }

    private static void doClosing(SocketKey socket, SocketEvent event)
            throws FailSyscall, ProtocolError {
        switch (event) {
            case TIMER:
//			socket.send(FIN);
                break;
            case SYN:
//			socket.send(SYNACK);
                break;
            case DATA:
            case STP:
//			socket.send(FIN);
                break;
            case FIN:
//			socket.send(FINACK);
//			socket.setSocketState(CLOSED);
                break;
            case FINACK:
//			socket.setSocketState(CLOSED);
                break;
            default:
                throw new ProtocolError();
        }
    }

    private static void doStpSent(SocketKey socket, SocketEvent event)
            throws ProtocolError {
        switch (event) {
            case TIMER:
//			socket.resendPackets();
                break;
            case SYN:
//			socket.send(SYNACK);
                break;
            case DATA:
//			socket.send(STP);
                break;
            case ACK:
//			socket.shiftSendWindow();
//			socket.sendData();
//			if (socket.isSendQueueEmpty()){
//				socket.send(FIN);
//				socket.setSocketState(CLOSING);
//			}
                break;
            case STP:
//			socket.clearSendWindow();
//			socket.send(FIN);
//			socket.setSocketState(CLOSING);
                break;
            case FIN:
//			socket.send(FINACK);
//			socket.setSocketState(CLOSED);
                break;
            case FINACK:
            default:
                throw new ProtocolError();
        }
    }

    private static void doStpRcvd(SocketKey socket, SocketEvent event)
            throws ProtocolError, FailSyscall {
        switch (event) {
            case RECV:
//			socket.dequeue();
                break;
            case SEND:
                throw new FailSyscall();
            case CLOSE:
//			socket.send(FIN);
//			socket.setSocketState(CLOSING);
                break;
            case DATA:
//			socket.queue();
//			socket.send(ACK);
                break;
            case FIN:
//			socket.send(FINACK);
//			socket.setSocketState(CLOSED);
                break;
            case ACK:
            case FINACK:
            default:
                throw new ProtocolError();
        }
    }

    private static void doEstablished(SocketKey socket, SocketEvent event)
            throws ProtocolError {
        switch (event) {
            case RECV:
//			socket.dequeue();
                break;
            case SEND:
//			socket.queue();
//			socket.shiftSendWindow();
                break;
            case CLOSE:
//			if (socket.isSendQueueEmpty()){
//				socket.send(FIN);
//				socket.setSocketState(CLOSING);
//			}else{
//				socket.send(STP);
//				socket.setSocketState(STP_SENT);
//			}
                break;
            case TIMER:
//			socket.resendPackets();
                break;
            case SYN:
//			socket.send(SYNACK);
                break;
            case DATA:
//			socket.queue();
//			socket.send(ACK);
                break;
            case ACK:
//			socket.shiftSendWindow();
//			socket.sendData();
                break;
            case STP:
//			socket.clearSendWindow();
//			socket.setSocketState(STP_RCVD);
                break;
            case FIN:
//			socket.clearSendWindow();
//			socket.send(FINACK);
//			socket.setSocketState(CLOSED);
                break;
            case FINACK:
            default:
                throw new ProtocolError();
        }
    }

    private static void doSynRcvd(SocketKey socket, SocketEvent event)
            throws ProtocolError {
        switch (event) {
            case ACCEPT:
//			socket.send(SYNACK);
//			socket.setSocketState(ESTABLISHED);
                break;
            case DATA:
            case ACK:
            case STP:
            case FIN:
            case FINACK:
            default:
                throw new ProtocolError();
        }
    }

    private static void doSynSent(SocketKey socket, SocketEvent event)
            throws ProtocolDeadlock, ProtocolError {
        switch (event) {
            case TIMER:
            case DATA:
            case STP:
            case FIN:
//			socket.send(SYN);
                break;
            case SYN:
                throw new ProtocolDeadlock();
            case SYNACK:
                //		socket.setSocketState(ESTABLISHED);
                //		socket.wakeConnectThread();
                break;
            case ACK:
            default:
                throw new ProtocolError();
        }
    }

    private static void doClosed(SocketKey socket, SocketEvent event)
            throws FailSyscall, ProtocolError {
        switch (event) {
            case CONNECT:
//			socket.send(SYN);
//			socket.setSocketState(SYN_SENT);
//			socket.block();
            case RECV:
//			if (!socket.dequeue()){
//				throw new FailSyscall();
//			}
                break;
            case SEND:
                throw new FailSyscall();
            case SYN:
//			socket.setSocketState(SYN_RCVD);
                break;
            case FIN:
//			socket.send(FINACK);
                break;
            case DATA:
            case ACK:
            case STP:
            default:
                throw new ProtocolError();
        }
    }

    private static SocketState getState(SocketKey key) {
        SocketState result;
        _statesLock.acquire();
        if (! _states.containsKey(key)) {
            _states.put(key, CLOSED);
        }
        result = _states.get(key);
        _statesLock.release();
        return result;
    }

    private static Map<SocketKey, SocketState> _states
            = new HashMap<SocketKey, SocketState>();
    private static Lock _statesLock = new Lock();
}
