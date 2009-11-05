package nachos.vm;

/**
 * Comments go here, blah, blah, blah.....
 */
public class MemoryKey {
	final int pid;
	final int vpn;
	
	public MemoryKey(int pid, int vpn){
		this.pid = pid;
		this.vpn = vpn;
	}
	
	
	private MemoryKey(){
		throw new AssertionError();
	}

	public int getPid() {
		return pid;
	}

	public int getVpn() {
		return vpn;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + pid;
		result = prime * result + vpn;
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
		MemoryKey other = (MemoryKey) obj;
		if (pid != other.pid)
			return false;
		if (vpn != other.vpn)
			return false;
		return true;
	}
	
	

}
