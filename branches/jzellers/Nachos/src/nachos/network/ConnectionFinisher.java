package nachos.network;

public class ConnectionFinisher implements Runnable {

	Connection connection;
	
	ConnectionFinisher(Connection connection){
		this.connection = connection;
	}
	
	@Override
	public void run() {
		connection.finish();
	}

}
