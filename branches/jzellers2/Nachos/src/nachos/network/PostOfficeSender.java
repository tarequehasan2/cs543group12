package nachos.network;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nachos.threads.KThread;
import nachos.threads.Lock;

public class PostOfficeSender implements Runnable {

	PostOfficeSender(PostOffice postOffice){
		this.postOffice = postOffice;
	}
	private HashMap<SocketKey, LinkedList<NachosMessage>> sendBuffer = new HashMap<SocketKey, LinkedList<NachosMessage>>();

	private HashMap<SocketKey, Integer> currentSeqNum = new HashMap<SocketKey, Integer>();
	
	private final int SEND_WINDOW = 16;
	
	private Lock sendLock = new Lock(); 
	
	private PostOffice postOffice = null;
	
	private Map<SocketKey, List<NachosMessage>> unackedBuffer = new HashMap<SocketKey, List<NachosMessage>>();
	
	private static boolean running = true; // set this to false in terminate.
	
	@Override
	public void run() {
		while (running){
			sendLock.acquire();
			for (SocketKey socketKey : sendBuffer.keySet()){
				if (!sendBuffer.get(socketKey).isEmpty() && unackedBuffer.get(socketKey).size() < SEND_WINDOW){
					NachosMessage message = sendBuffer.get(socketKey).removeFirst();
					if (-1 == message.getSequence()) {
						if (currentSeqNum.containsKey(socketKey)) {
							int seq = currentSeqNum.get(socketKey);
							message.setSequence(seq);
							seq++;
							currentSeqNum.put(socketKey, seq);
						}
						else {
							int seq = 0;
							message.setSequence(seq);
							seq++;
							currentSeqNum.put(socketKey, seq);
						}
					}
					postOffice.send(message);
					if (unackedBuffer.containsKey(socketKey)){
						unackedBuffer.get(socketKey).add(message);
					}else{
						List<NachosMessage> list = new LinkedList<NachosMessage>();
						list.add(message);
						unackedBuffer.put(socketKey, list);
					}
				}
			}
			sendLock.release();
			KThread.yield();
		}

	}
	
	public void send(NachosMessage message){
		sendLock.acquire();
		SocketKey key = new SocketKey(message);
		if (sendBuffer.containsKey(key)){
			sendBuffer.get(key).addLast(message);
		}else{
			LinkedList<NachosMessage> list = new LinkedList<NachosMessage>();
			list.add(message);
			sendBuffer.put(key, list);
		}
		sendLock.release();
	}
	
	// triggerMessage is the message that triggered a resendAll on this socket.   Needed to look up the socket.
	public void resendAll(NachosMessage triggerMessage){
		sendLock.acquire();
		SocketKey key = new SocketKey(triggerMessage);
		List<NachosMessage> messages = unackedBuffer.get(key);
		if (messages == null || messages.isEmpty()){
			return;
		}
		for (NachosMessage message: messages){
			postOffice.send(message);
		}
		sendLock.release();
	}
	
	public void ackMessage(NachosMessage triggerMessage){
		sendLock.acquire();
		SocketKey key = new SocketKey(triggerMessage);
		for (NachosMessage message : unackedBuffer.get(key)){
			if(message.getSequence() == triggerMessage.getSequence()){
				unackedBuffer.get(key).remove(message);
			}
		}

		sendLock.release();
	}
	
	public static void terminate(){
		running = false;
	}

}
