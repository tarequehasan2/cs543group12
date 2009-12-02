package nachos.network;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	private PostOffice postOffice;

	private Map<SocketKey, LinkedList<NachosMessage>> unackedBuffer = new HashMap<SocketKey, LinkedList<NachosMessage>>();

	private Map<SocketKey, LinkedList<Acked>> unackedBufferIndicator = new HashMap<SocketKey, LinkedList<Acked>>();
    private Map<SocketKey, Boolean> stopInEffect = new HashMap<SocketKey, Boolean>();

	private static boolean running = true; // set this to false in terminate.

	@Override
	/**
	 * This is the driver method that does the sending and buffering.  It acquires a lock to do it's work,
	 * but yields the cpu to give other threads a chance.
	 *
	 */
	public void run() {
		while (running){
			sendLock.acquire();
			for (SocketKey socketKey : sendBuffer.keySet()){
				if (!sendBuffer.get(socketKey).isEmpty() && unackedBuffer.get(socketKey).size() < SEND_WINDOW){
					NachosMessage message = sendBuffer.get(socketKey).removeFirst();
					if (Integer.MIN_VALUE == message.getSequence()) {
						if (! currentSeqNum.containsKey(socketKey)) {
							currentSeqNum.put(socketKey, 0);
						}
                        int seq = currentSeqNum.get(socketKey);
                        message.setSequence(seq);
                        seq++;
                        currentSeqNum.put(socketKey, seq);
					}
					
					postOffice.send(message);
					if (!message.isACK()){
						if (unackedBuffer.containsKey(socketKey)){
							unackedBuffer.get(socketKey).addLast(message);
							unackedBufferIndicator.get(socketKey).addLast(Acked.NO);
						}else{
							LinkedList<NachosMessage> list = new LinkedList<NachosMessage>();
							list.addLast(message);
							unackedBuffer.put(socketKey, list);
							LinkedList<Acked> listIndicator = new LinkedList<Acked>();
							listIndicator.addLast(Acked.NO);
							unackedBufferIndicator.put(socketKey, listIndicator);
						}
					}
				}
			}
			sendLock.release();
			KThread.yield();
		}

	}

    public boolean isQueueEmpty(SocketKey key) {
        // if we don't know about that key, its queue is definitely empty
        return !sendBuffer.containsKey(key)
                || sendBuffer.get(key).isEmpty();
    }

    public void stop(SocketKey key) {
        sendLock.acquire();
        stopInEffect.put(key, true);
        sendLock.release();
    }

    public void close(SocketKey key) {
        sendLock.acquire();
        sendBuffer.remove(key);
        currentSeqNum.remove(key);
        unackedBuffer.remove(key);
        unackedBufferIndicator.remove(key);
        stopInEffect.remove(key);
        sendLock.release();
    }

	/**
	 * Add a message to the sendbuffer.  The message won't be sent immediately, but will be sent after
	 * the buffer has an available slot and the driver yields this thread.
	 *
	 * @param message
	 */
	public void send(NachosMessage message){
		sendLock.acquire();
		SocketKey key = new SocketKey(message).reverse();
        if (stopInEffect.containsKey(key)) {
            sendLock.release();
            return;
        }
		if (sendBuffer.containsKey(key)){
			sendBuffer.get(key).addLast(message);
		}else{
			LinkedList<NachosMessage> list = new LinkedList<NachosMessage>();
			list.add(message);
			sendBuffer.put(key, list);
            unackedBuffer.put(key, new LinkedList<NachosMessage>());
            unackedBufferIndicator.put(key, new LinkedList<Acked>());
		}
		sendLock.release();
	}

	/**
	 * Go through all of the unacked packets in all of the queues.  Resend if necessary.
	 *
	 * This method will be triggered on a timer event.
	 */
	public void resendAllUnacked(){
		sendLock.acquire();
		Set<SocketKey> keySet = unackedBuffer.keySet();
		for(SocketKey key : keySet){
			List<NachosMessage> messages = unackedBuffer.get(key);
			if (messages == null || messages.isEmpty()){
				continue;
			}
			for (int i=0; i< messages.size(); i++){
				NachosMessage message = messages.get(i);
				if (unackedBufferIndicator.get(key).get(i).equals(Acked.NO)){
					System.err.println("TIMER");
					postOffice.send(message);
				}

			}
		}
		sendLock.release();
	}

	/**
	 * Based on a particular message, we need to acknowledge receipt.
	 * since this is a sliding window, we need to wait until the first message is received
	 * before sending more.   If the first packet is dropped, but the second packet is acked,
	 * we will mark it as acked.   When the first packet is received, both the first and second
	 * packet will be removed from the buffer.
	 *
	 * @param triggerMessage - message to be acked - should contain the sequence number.
	 */
	public void ackMessage(NachosMessage triggerMessage){
		sendLock.acquire();
		SocketKey key = new SocketKey(triggerMessage,true); // .reverse();
        if (! unackedBuffer.containsKey(key)) {
            sendLock.release();
            return;
        }
		LinkedList<NachosMessage> nachosMessages= unackedBuffer.get(key);
        final LinkedList<Acked> ackeds = unackedBufferIndicator.get(key);
        for (int i = 0; i < nachosMessages.size(); i++){
			NachosMessage message = nachosMessages.get(i);
			if (message.getSequence() == triggerMessage.getSequence()){
				ackeds.set(i, Acked.YES);
			}
		}

		//slide window if necessary to allow more messages to flow.

		while (ackeds.isEmpty() && ackeds.getFirst().equals(Acked.YES)) {
			ackeds.removeFirst();
			unackedBuffer.get(key).removeFirst();
		}


		sendLock.release();
	}

	/**
	 * Indicate to the driver that this thread should be terminated.  Called by the kernel.
	 */
	public static void terminate(){
		running = false;
	}

	/**
	 * Better than bit fields, IMO.
	 *
	 */
	public enum Acked {
		YES,
		NO
	}

}
