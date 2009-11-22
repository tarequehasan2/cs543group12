package nachos.network;

import nachos.machine.Machine;

public class SocketRetransmitter implements Runnable {


	private static final long RETRANSMIT_TICKS = 20000;
	Socket socket = null;
	public SocketRetransmitter(Socket socket) {
		this.socket = socket;
	}
	@Override
	public void run() {
		resendPackets();	
	}
	
	private void resendPackets() {
		while (true){
			socket.resendPackets();
			long currentTime = Machine.timer().getTime();
			long waitTime = currentTime + RETRANSMIT_TICKS;
			NetKernel.alarm.waitUntil(waitTime);
		}
	}
}
