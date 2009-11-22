package nachos.network;

import java.util.EnumSet;

import nachos.machine.Lib;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class ProtocolPacket extends Packet {

	public ProtocolPacket(byte[] packetBytes) throws MalformedPacketException {
		//TODO  check for valid protocol packet 
		super(packetBytes);
	}
 
	public ProtocolPacket(int dstLink, int srcLink, int dstPort, int srcPort, int seqNum, EnumSet<PacketHeaderFlags> flags, byte[] contents)
			throws MalformedPacketException {
		super(dstLink, srcLink, contents);
		// Make room for the header.
		System.arraycopy(packetBytes, headerLength, packetBytes, headerLength + addtionalHeader,
				 contents.length);
	
		
		byte flagBits = PacketHeaderFlags.getBits(flags);
		byte mustBeZero = (byte) 0;
		
		packetBytes[headerLength] = (byte) dstPort;
		
		packetBytes[headerLength + 1] = (byte) srcPort;
		
		packetBytes[headerLength + 2] = mustBeZero;
		
		packetBytes[headerLength + 3] = flagBits;
		
		byte[] seqNumBits = Lib.bytesFromInt(seqNum);
		System.arraycopy(seqNumBits, 0, packetBytes, headerLength +4, seqNumBits.length);
		
		// update main header with new content length
		packetBytes[headerLength-1] = (byte) (packetBytes[headerLength-1] + addtionalHeader);
		
		
	}

	public ProtocolPacket(Packet packet) throws MalformedPacketException{
		super(packet.dstLink, packet.srcLink, packet.contents);
		if(!(packet instanceof ProtocolPacket)){
			// not enough information for a protocol packet.
			throw new MalformedPacketException();
		}
	}
	
	public static final int addtionalHeader = 8;
	
	
	
}
