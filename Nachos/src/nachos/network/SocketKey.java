package nachos.network;

import nachos.machine.Machine;

public class SocketKey {
	private int sourceHost, sourcePort, destHost, destPort;
	SocketKey (int sourceHost, int sourcePort, int destHost, int destPort){
		this.sourceHost = sourceHost;
		this.sourcePort = sourcePort;
		this.destHost = destHost;
		this.destPort = destPort;
	}
	
	SocketKey(){
		
	}
	
	SocketKey(NachosMessage message){
		if (message.getSourceHost() == Machine.networkLink().getLinkAddress() ){  //packet sent
			this.sourceHost = message.getSourceHost();
			this.sourcePort = message.getSourcePort();
			this.destHost = message.getDestHost();
			this.destPort = message.getDestPort();			
		}else{  // packet received
			this.destHost = message.getSourceHost();
			this.destPort = message.getSourcePort();
			this.sourceHost = message.getDestHost();
			this.sourcePort = message.getDestPort();
		}
	}
	
	public int getSourceHost() {
		return sourceHost;
	}
	public void setSourceHost(int sourceHost) {
		this.sourceHost = sourceHost;
	}
	public int getSourcePort() {
		return sourcePort;
	}
	public void setSourcePort(int sourcePort) {
		this.sourcePort = sourcePort;
	}
	public int getDestHost() {
		return destHost;
	}
	public void setDestID(int destHost) {
		this.destHost = destHost;
	}
	public int getDestPort() {
		return destPort;
	}
	public void setDestPort(int destPort) {
		this.destPort = destPort;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + destHost;
		result = prime * result + destPort;
		result = prime * result + sourceHost;
		result = prime * result + sourcePort;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SocketKey other = (SocketKey) obj;
		if (destHost != other.destHost)
			return false;
		if (destPort != other.destPort)
			return false;
		if (sourceHost != other.sourceHost)
			return false;
		if (sourcePort != other.sourcePort)
			return false;
		return true;
	}
	
	
	
}
