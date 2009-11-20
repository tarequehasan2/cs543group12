package nachos.network;

import static nachos.network.ConnectionState.*;
import static nachos.network.ConnectionEvent.*;

public final class ConnectionTransition {
	
	public static void doEvent(Connection connection, ConnectionEvent event) throws FailSyscall, ProtocolError, ProtocolDeadlock{
		switch (connection.getConnectionState()){
		case CLOSED:
			doClosed(connection, event);
			break;
		case SYN_SENT:
			doSynSent(connection, event);
			break;
		case SYN_RCVD:
			doSynRcvd(connection, event);
			break;
		case ESTABLISHED:
			doEstablished(connection, event);
			break;
		case STP_RCVD:
			doStpRcvd(connection, event);
			break;
		case STP_SENT:
			doStpSent(connection, event);
			break;
		case CLOSING:
			doClosing(connection, event);
			break;
		
		}
		
	}

	private static void doClosing(Connection connection, ConnectionEvent event) throws FailSyscall, ProtocolError {
		switch (event){
		case TIMER:
			connection.send(FIN);
			break;
		case SYN:
			connection.send(SYNACK);
			break;
		case DATA:
		case STP:
			connection.send(FIN);
			break;
		case FIN:
			connection.send(FINACK);
			connection.setConnectionState(CLOSED);
			break;
		case FINACK:
			connection.setConnectionState(CLOSED);
			break;
		default:
			throw new ProtocolError();
		}

	}

	private static void doStpSent(Connection connection, ConnectionEvent event) throws ProtocolError {
		switch (event){
		case TIMER:
			connection.resendPackets();
			break;
		case SYN:
			connection.send(SYNACK);
			break;
		case DATA:
			connection.send(STP);
			break;
		case ACK:
			connection.shiftSendWindow();
			connection.sendData();
			if (connection.isSendQueueEmpty()){
				connection.send(FIN);
				connection.setConnectionState(CLOSING);
			}
			break;
		case STP:
			connection.clearSendWindow();
			connection.send(FIN);
			connection.setConnectionState(CLOSING);
			break;
		case FIN:
			connection.send(FINACK);
			connection.setConnectionState(CLOSED);
			break;
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doStpRcvd(Connection connection, ConnectionEvent event) throws ProtocolError, FailSyscall {
		switch (event){
		case RECV:
			connection.dequeue();
			break;
		case SEND:
			throw new FailSyscall();
		case CLOSE:
			connection.send(FIN);
			connection.setConnectionState(CLOSING);
			break;
		case DATA:
			connection.queue();
			connection.send(ACK);
			break;
		case FIN:
			connection.send(FINACK);
			connection.setConnectionState(CLOSED);
			break;
		case ACK:
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doEstablished(Connection connection, ConnectionEvent event) throws ProtocolError {
		switch (event){
		case RECV:
			connection.dequeue();
			break;
		case SEND:
			connection.queue();
			connection.shiftSendWindow();
			break;
		case CLOSE:
			if (connection.isSendQueueEmpty()){
				connection.send(FIN);
				connection.setConnectionState(CLOSING);
			}else{
				connection.send(STP);
				connection.setConnectionState(STP_SENT);
			}
			break;
		case TIMER:
			connection.resendPackets();
			break;
		case SYN:
			connection.send(SYNACK);
			break;
		case DATA:
			connection.queue();
			connection.send(ACK);
			break;
		case ACK:
			connection.shiftSendWindow();
			connection.sendData();
			break;
		case STP:
			connection.clearSendWindow();
			connection.setConnectionState(STP_RCVD);
			break;
		case FIN:
			connection.clearSendWindow();
			connection.send(FINACK);
			connection.setConnectionState(CLOSED);
			break;
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doSynRcvd(Connection connection, ConnectionEvent event) throws ProtocolError {
		switch (event){
		case ACCEPT:
			connection.send(SYNACK);
			connection.setConnectionState(ESTABLISHED);
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

	private static void doSynSent(Connection connection, ConnectionEvent event) throws ProtocolDeadlock, ProtocolError {
		switch (event) {
		case TIMER:
		case DATA:
		case STP:
		case FIN:
			connection.send(SYN);
			break;
		case SYN:
			throw new ProtocolDeadlock();
		case SYNACK:
			connection.setConnectionState(ESTABLISHED);
			connection.wakeConnectThread();
			break;
		case ACK:
		default:
			throw new ProtocolError();
		}
		
	}

	private static void doClosed(Connection connection, ConnectionEvent event) throws FailSyscall, ProtocolError {
		switch (event){
		case CONNECT:
			connection.send(SYN);
			connection.setConnectionState(SYN_SENT);
			connection.block();
		case RECV:
			boolean dequeued = connection.dequeue();
			if (!dequeued){
				throw new FailSyscall();
			}
			break;
		case SEND:
			throw new FailSyscall();
		case SYN:
			connection.setConnectionState(SYN_RCVD);
			break;
		case FIN:
			connection.send(FINACK);
			break;
		case DATA:
		case ACK:
		case STP:
		default:
			throw new ProtocolError();
		
		}
	}

}
