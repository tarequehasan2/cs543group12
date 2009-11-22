package nachos.network;

public class SocketFinisher implements Runnable {

	Socket socket;
	
	SocketFinisher(Socket socket){
		this.socket = socket;
	}
	
	@Override
	public void run() {
		socket.finish();
	}

}
