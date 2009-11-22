package nachos.network;

import nachos.threads.KThread;

public class SocketReceiver implements Runnable {
	
	Socket socket = null;
	
	public SocketReceiver(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		receivePackets();

	}

	private void receivePackets() {
		while (true){
			//TODO get a packet
			KThread.yield();
		}
		
	}

}
