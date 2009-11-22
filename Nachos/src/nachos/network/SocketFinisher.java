package nachos.network;

public class SocketFinisher implements Runnable {

	Socket connection;
	
	SocketFinisher(Socket connection){
		this.connection = connection;
	}
	
	@Override
	public void run() {
		connection.finish();
	}

}
