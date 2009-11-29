package nachos.network;

public class SocketFinisher implements Runnable {

	private NetKernel netKernel;
	private int sourceHost;
	private int sourcePort;
	private int destPort; 
	
	SocketFinisher(NetKernel netKernel, int sourceHost, int sourcePort, int destPort){
		this.netKernel = netKernel;
		this.destPort = destPort;
		this.sourceHost = sourcePort;
		this.sourcePort = sourcePort;
	}
	
	@Override
	public void run() {
		netKernel.close(sourceHost, sourcePort, destPort);
	}

}
