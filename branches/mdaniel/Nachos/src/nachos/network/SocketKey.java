package nachos.network;

public class SocketKey implements Cloneable{
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

	public static SocketKey reverse (SocketKey thisKey){
		SocketKey result = null;
		try {
			result = (SocketKey) thisKey.clone();
			result.destHost = thisKey.sourceHost;
			result.sourceHost = thisKey.destHost;
			result.destPort = thisKey.sourcePort;
			result.sourcePort = thisKey.destPort;
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
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

    /**
     * Returns the inverse of this SocketKey.
     * @return the inverse of this SocketKey.
     */
    public SocketKey reverse() {
        return new SocketKey(destHost, destPort, sourceHost, sourcePort);
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
