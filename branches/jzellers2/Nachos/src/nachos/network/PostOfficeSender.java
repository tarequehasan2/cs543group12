package nachos.network;

import java.util.LinkedList;

import nachos.threads.KThread;
import nachos.threads.Lock;

public class PostOfficeSender implements Runnable {

	PostOfficeSender(PostOffice postOffice){
		this.postOffice = postOffice;
	}
	private LinkedList<NachosMessage> messages = new LinkedList<NachosMessage>();
	
	private Lock sendLock = new Lock(); 
	
	private PostOffice postOffice = null;
	
	private static boolean running = true; // set this to false in terminate.
	
	@Override
	public void run() {
		while (running){
			sendLock.acquire();
			if (!messages.isEmpty()){
				postOffice.send(messages.getFirst());
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
	
	public static void terminate(){
		running = false;
	}

}
