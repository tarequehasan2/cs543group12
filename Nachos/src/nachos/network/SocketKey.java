package nachos.network;

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
        /// we are comparing socket keys in an order-insensitive way
		result = prime * result + (destHost + sourceHost);
		result = prime * result + (destPort + sourcePort);
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
        /// we are comparing socket keys in an order-insensitive way
        return (destHost + sourceHost) == (other.destHost) + (other.sourceHost) &&
               (destPort + sourcePort) == (other.destPort) + (other.sourcePort);
    }

    @Override
    public String toString() {
        return "SoKey(D:("+destHost+","+destPort+"),S:("+sourceHost+","+sourcePort+"))";
    }
}
