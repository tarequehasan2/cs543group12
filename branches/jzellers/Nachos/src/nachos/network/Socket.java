package nachos.network;

import static nachos.network.SocketState.*;
import static nachos.network.SocketEvent.*;

import java.util.EnumSet;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.OpenFile;
import nachos.threads.KThread;
import nachos.threads.Lock;

public final class Socket extends OpenFile {
	
	
	
	private SocketState socketState = CLOSED;
	
	public Socket() {
		new KThread(new SocketReceiver(this)).fork();
		new KThread(new SocketSender(this)).fork();
		new KThread(new SocketRetransmitter(this)).fork();
		sendLock = new Lock();
		packetQueue = new MailMessage[windowSize];
	}
	
	void testTransition(){
		SocketEvent event = FIN;
		try {
			SocketTransition.doEvent(this, event);
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
	}

	public SocketState getSocketState() {
		return socketState;
	}

	public void setSocketState(SocketState socketState) {
		this.socketState = socketState;
	}

	public void send(SocketEvent event) {
		// TODO Auto-generated method stub
		
	}

	public void block() {
		// TODO Auto-generated method stub
		
	}

	public boolean dequeue() {
		// TODO Auto-generated method stub
		return false;
	}

	public void wakeConnectThread() {
		// TODO Auto-generated method stub
		
	}

	public void queue() {
		// TODO Auto-generated method stub
		
	}

	public void shiftSendWindow() {
		// TODO Auto-generated method stub
		
	}

	public boolean isSendQueueEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	public void resendPackets() {
		// TODO Auto-generated method stub
		
	}

	public void sendData() {
		// TODO Auto-generated method stub
		
	}

	public void clearSendWindow() {
		// TODO Auto-generated method stub
		
	}

	public int connect(int a0, int a1) {
    	debug("connect("+a0+","+a1+")");
		try {
			sendLock.acquire();
			SocketTransition.doEvent(this, CONNECT);
			//TODO how do we handle local ports?
			// At the socket level, do we need more than just a0 and a1?
			int srcLink = Machine.networkLink().getLinkAddress();
			int srcPort = 4;
			EnumSet<PacketHeaderFlags> flags = EnumSet.of(PacketHeaderFlags.SYN);
			byte[] contents = new byte[0];
			ProtocolPacket packet = new ProtocolPacket(a0, srcLink, a1, srcPort, currentSeqNum, flags, contents);
			// TODO: how do we send the packet?
		} catch (FailSyscall e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedPacketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ProtocolError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ProtocolDeadlock e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			sendLock.release();
		}
		return -1;
	}

	@Override
	public void close() {
    	debug("close()");
		try {
			SocketTransition.doEvent(this, CLOSE);
			new KThread(new SocketFinisher(this)).fork();
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
		
	}

	@Override
	public int read(byte[] buf, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int read(int pos, byte[] buf, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int write(byte[] buf, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int write(int pos, byte[] buf, int offset, int length) {
		// TODO Auto-generated method stub
		return -1;
	}

	public void finish() {
		// TODO Auto-generated method stub
		
	}

    /**
     * Reports a debug message iff the operating system is running with
     * our specific debug flag turned on. The message will be qualified
     * with the current process's <tt>PID</tt>.
     * @param msg the message to report if running in debug mode.
     */
    private void debug(String msg) {
    	Lib.debug(dbgProcess, "DEBUG:"+toString()+":"+msg);
    }

    private static final char dbgProcess = 's';
	public static final int windowSize = 16;
	private MailMessage[] packetQueue = null;
	int currentSeqNum = 0;
    private Lock sendLock;
}
