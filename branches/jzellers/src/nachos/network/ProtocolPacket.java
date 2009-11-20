package nachos.network;

import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class ProtocolPacket extends Packet {
	

	public ProtocolPacket(byte[] packetBytes) throws MalformedPacketException {
		super(packetBytes);
	}

	public ProtocolPacket(int dstLink, int srcLink, byte[] contents)
			throws MalformedPacketException {
		super(dstLink, srcLink, contents);
	}

	public ProtocolPacket(Packet packet) throws MalformedPacketException{
		this(packet.dstLink, packet.srcLink, packet.contents);
	}
	
	public static final int addtionalHeader = 8;
	
	public static final int headerLength = 4 + addtionalHeader;
	
	public static final int maxPacketLength = 32 + addtionalHeader;
	
}
