package nachos.network;

import java.util.EnumSet;

public enum PacketHeaderFlags {
	FIN(3), STP(2), ACK(1), SYN(0);

	private byte bit;

	PacketHeaderFlags(int shift) {
		bit = (byte) (1 << shift);
	}

	public static byte getBits(EnumSet<PacketHeaderFlags> flags) {
		byte result = 0;
		for (PacketHeaderFlags flag : flags) {
			result += flag.bit;
		}
		return result;
	}

	public static EnumSet<PacketHeaderFlags> getFlags(byte bitFields) {
		EnumSet<PacketHeaderFlags> result = null;
		for (PacketHeaderFlags flag : PacketHeaderFlags.values()) {
			if ((bitFields & flag.bit) == flag.bit) {
				if (result == null) {
					result = EnumSet.of(flag);
				} else {
					result.add(flag);
				}
			}
		}
		return result;
	}

}
