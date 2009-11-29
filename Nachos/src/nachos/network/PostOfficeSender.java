package nachos.network;

import java.util.LinkedList;
import java.util.List;

import nachos.threads.KThread;
import nachos.threads.Lock;

public class PostOfficeSender implements Runnable {

	PostOfficeSender(PostOffice postOffice){
		this.postOffice = postOffice;
	}
	private LinkedList<NachosMessage> messages = new LinkedList<NachosMessage>();
	
	private final int SEND_WINDOW = 16;
	
	private List<NachosMessage> unackedMessages = new LinkedList<NachosMessage>();
	
	private Lock sendLock = new Lock(); 
	
	private PostOffice postOffice = null;
	
	private static boolean running = true; // set this to false in terminate.
	
	@Override
	public void run() {
		while (running){
			sendLock.acquire();
			if (!messages.isEmpty() && unackedMessages.size() < SEND_WINDOW){
				NachosMessage message = messages.removeFirst();
				postOffice.send(message);
				unackedMessages.add(message);
			}
			sendLock.release();
			KThread.yield();
		}

	}
	
	public void send(NachosMessage message){
		sendLock.acquire();
		messages.addLast(message);
		sendLock.release();
	}
	
	public void resendAll(){
		sendLock.acquire();
		for (NachosMessage message : unackedMessages){
			postOffice.send(message);
		}
		sendLock.release();
	}
	
	public void ackMessage(int seqNum){
		sendLock.acquire();
		for (NachosMessage message : unackedMessages){
			if(message.getSequence() == seqNum){
				unackedMessages.remove(message);
			}
		}
		sendLock.release();
	}
	
	public static void terminate(){
		running = false;
	}

}
