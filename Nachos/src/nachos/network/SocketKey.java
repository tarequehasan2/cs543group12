package nachos.network;

import nachos.machine.Machine;

// import nachos.machine.Machine;

public class SocketKey {
	private int sourceHost, sourcePort, destHost, destPort;

    SocketKey (int sourceHost, int sourcePort, int destHost, int destPort){
		this.sourceHost = sourceHost;
		this.sourcePort = sourcePort;
		this.destHost = destHost;
		this.destPort = destPort;
	}

	SocketKey(NachosMessage message){
			this.sourceHost = message.getSourceHost();
			this.sourcePort = message.getSourcePort();
			this.destHost = message.getDestHost();
			this.destPort = message.getDestPort();
	}

	SocketKey(NachosMessage message, boolean reverse){
		this(message);		
		if (reverse){
			this.destHost = message.getSourceHost();
			this.destPort = message.getSourcePort();
			this.sourceHost = message.getDestHost();
			this.sourcePort = message.getDestPort();
		}
	}
	
	
	public int getSourceHost() {
		return sourceHost;
	}
	public int getSourcePort() {
		return sourcePort;
	}
	public int getDestHost() {
		return destHost;
	}
	public int getDestPort() {
		return destPort;
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
        return destHost == other.destHost &&
               destPort == other.destPort &&
               sourceHost == other.sourceHost &&
               sourcePort == other.sourcePort;
    }

    @Override
    public String toString() {
        return "SocKey(D:("+destHost+","+destPort+"),S:("+sourceHost+","+sourcePort+"))";
    }
}
