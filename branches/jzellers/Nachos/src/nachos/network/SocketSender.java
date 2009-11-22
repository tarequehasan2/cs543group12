package nachos.network;

import nachos.threads.KThread;

public class SocketSender implements Runnable {

	Socket socket = null;
	public SocketSender(Socket socket) {
		this.socket = socket;
	}
	@Override
	public void run() {
		sendPackets();
	}

	private void sendPackets() {
		while(true){
			socket.sendData();
			KThread.yield();
		}
	}

}
