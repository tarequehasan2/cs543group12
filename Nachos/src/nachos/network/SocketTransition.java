package nachos.network;

import static nachos.network.SocketState.*;
import static nachos.network.SocketEvent.*;

public final class SocketTransition {
	
	public static void doEvent(Socket socket, SocketEvent event) throws FailSyscall, ProtocolError, ProtocolDeadlock{
		switch (socket.getSocketState()){
		case CLOSED:
			doClosed(socket, event);
			break;
		case SYN_SENT:
			doSynSent(socket, event);
			break;
		case SYN_RCVD:
			doSynRcvd(socket, event);
			break;
		case ESTABLISHED:
			doEstablished(socket, event);
			break;
		case STP_RCVD:
			doStpRcvd(socket, event);
			break;
		case STP_SENT:
			doStpSent(socket, event);
			break;
		case CLOSING:
			doClosing(socket, event);
			break;
		
		}
		
	}

	private static void doClosing(Socket socket, SocketEvent event) throws FailSyscall, ProtocolError {
		switch (event){
		case TIMER:
			socket.send(FIN);
			break;
		case SYN:
			socket.send(SYNACK);
			break;
		case DATA:
		case STP:
			socket.send(FIN);
			break;
		case FIN:
			socket.send(FINACK);
			socket.setSocketState(CLOSED);
			break;
		case FINACK:
			socket.setSocketState(CLOSED);
			break;
		default:
			throw new ProtocolError();
		}

	}

	private static void doStpSent(Socket socket, SocketEvent event) throws ProtocolError {
		switch (event){
		case TIMER:
			socket.resendPackets();
			break;
		case SYN:
			socket.send(SYNACK);
			break;
		case DATA:
			socket.send(STP);
			break;
		case ACK:
			socket.shiftSendWindow();
			socket.sendData();
			if (socket.isSendQueueEmpty()){
				socket.send(FIN);
				socket.setSocketState(CLOSING);
			}
			break;
		case STP:
			socket.clearSendWindow();
			socket.send(FIN);
			socket.setSocketState(CLOSING);
			break;
		case FIN:
			socket.send(FINACK);
			socket.setSocketState(CLOSED);
			break;
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doStpRcvd(Socket socket, SocketEvent event) throws ProtocolError, FailSyscall {
		switch (event){
		case RECV:
			socket.dequeue();
			break;
		case SEND:
			throw new FailSyscall();
		case CLOSE:
			socket.send(FIN);
			socket.setSocketState(CLOSING);
			break;
		case DATA:
			socket.queue();
			socket.send(ACK);
			break;
		case FIN:
			socket.send(FINACK);
			socket.setSocketState(CLOSED);
			break;
		case ACK:
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doEstablished(Socket socket, SocketEvent event) throws ProtocolError {
		switch (event){
		case RECV:
			socket.dequeue();
			break;
		case SEND:
			socket.queue();
			socket.shiftSendWindow();
			break;
		case CLOSE:
			if (socket.isSendQueueEmpty()){
				socket.send(FIN);
				socket.setSocketState(CLOSING);
			}else{
				socket.send(STP);
				socket.setSocketState(STP_SENT);
			}
			break;
		case TIMER:
			socket.resendPackets();
			break;
		case SYN:
			socket.send(SYNACK);
			break;
		case DATA:
			socket.queue();
			socket.send(ACK);
			break;
		case ACK:
			socket.shiftSendWindow();
			socket.sendData();
			break;
		case STP:
			socket.clearSendWindow();
			socket.setSocketState(STP_RCVD);
			break;
		case FIN:
			socket.clearSendWindow();
			socket.send(FINACK);
			socket.setSocketState(CLOSED);
			break;
		case FINACK:
		default:
			throw new ProtocolError();
		}
	}

	private static void doSynRcvd(Socket socket, SocketEvent event) throws ProtocolError {
		switch (event){
		case ACCEPT:
			socket.send(SYNACK);
			socket.setSocketState(ESTABLISHED);
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

	private static void doSynSent(Socket socket, SocketEvent event) throws ProtocolDeadlock, ProtocolError {
		switch (event) {
		case TIMER:
		case DATA:
		case STP:
		case FIN:
			socket.send(SYN);
			break;
		case SYN:
			throw new ProtocolDeadlock();
		case SYNACK:
			socket.setSocketState(ESTABLISHED);
			socket.wakeConnectThread();
			break;
		case ACK:
		default:
			throw new ProtocolError();
		}
		
	}

	private static void doClosed(Socket socket, SocketEvent event) throws FailSyscall, ProtocolError {
		switch (event){
		case CONNECT:
			socket.send(SYN);
			socket.setSocketState(SYN_SENT);
			socket.block();
		case RECV:
			if (!socket.dequeue()){
				throw new FailSyscall();
			}
			break;
		case SEND:
			throw new FailSyscall();
		case SYN:
			socket.setSocketState(SYN_RCVD);
			break;
		case FIN:
			socket.send(FINACK);
			break;
		case DATA:
		case ACK:
		case STP:
		default:
			throw new ProtocolError();
		
		}
	}

}
