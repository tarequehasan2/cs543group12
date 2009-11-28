package nachos.network;

import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class NachosMessage
{
    public static NachosMessage ack(NachosMessage syn)
            throws MalformedPacketException {
        final int localPort = NetKernel.nextLocalPort();
        return new NachosMessage(
                syn.getSourceHost(), syn.getSourcePort(),
                getLocalLinkId(), localPort,
                ACK, new byte[0]);
    }

    /**
     * Creates a Nachos Message suitable for reporting FIN/ACK
     * to the provided FIN message.
     * @param fin The FIN message to which we are replying.
     * @return a newly constructed Nachos Message suitable for replying to
     * the provided message.
     * @throws MalformedPacketException if unable to construct said message.
     */
    public static NachosMessage newFinAck(NachosMessage fin)
            throws MalformedPacketException {
        return new NachosMessage(
                fin.getSourceHost(), fin.getSourcePort(),
                fin.getDestHost(), fin.getDestPort(),
                (byte)(FIN | ACK),
                new byte[0]);
    }

    public static NachosMessage stp(int host, int port)
            throws MalformedPacketException {
        final int localPort = NetKernel.nextLocalPort();
        return new NachosMessage(host, port,
                getLocalLinkId(), localPort,
                STP, new byte[0]);
    }

    public static NachosMessage pong(NachosMessage ping)
            throws MalformedPacketException {
        return new NachosMessage(
                ping.getSourceHost(), ping.getSourcePort(),
                ping.getDestHost(), ping.getDestPort(),
                ping.getPayload());
    }

    public static NachosMessage syn(int dstLink, int dstPort)
            throws MalformedPacketException {
        final int localPort = NetKernel.nextLocalPort();
        return new NachosMessage(
                dstLink, dstPort,
                getLocalLinkId(), localPort,
                SYN, new byte[0]);
    }

    /** Returns the default source host id for newly created messages. */
    private static int getLocalLinkId() {
        return Machine.networkLink().getLinkAddress();
    }

    /** Indicates the number of bytes required for our header. */
    private static final int HEADER_SIZE = 2 + 2 + 4;

    /** Maximum number of bytes I will accept as a payload. */
    public static final int MAX_CONTENTS_LENGTH =
	    Packet.maxContentsLength - HEADER_SIZE;
    /** The upper bound on the value of any source or destination port. */
    public static final int PORT_LIMIT = 0xFF - 1;

    public static final byte SYN = 1;
    public static final byte ACK = 2;
    public static final byte STP = 4;
    public static final byte FIN = 8;

    /**
     * Constructs a data message, devoid of special message flags.
     * @param dstLink the host-id this message is destined for.
     * @param dstPort the port on the destination.
     * @param srcLink the host-id this message is originating from.
     * @param srcPort the local port of this message, where the foreign host
     * should respond.
     * @param contents the payload of this message.
     * @throws MalformedPacketException
     */
    public NachosMessage(int dstLink, int dstPort, int srcLink, int srcPort,
                         byte[] contents) throws MalformedPacketException {
        this(dstLink, dstPort, srcLink, srcPort, (byte)0,
                (null == contents ? new byte[0] : contents));
    }

    public NachosMessage(int dstLink, int dstPort, int srcLink, int srcPort,
                         byte flags, byte[] contents)
            throws MalformedPacketException {
        _dstHost = dstLink;
        _dstPort = dstPort;
        _srcHost = srcLink;
        _srcPort = srcPort;
        _payload = contents;
        _seq = (int)System.currentTimeMillis();
        byte[] pContents = new byte[ _payload.length + HEADER_SIZE ];
        pContents[0] = (byte)(dstPort & 0xFF);
        pContents[1] = (byte)(srcPort & 0xFF);
        pContents[2] = (byte)0; // MBZ-HI
        pContents[3] = (byte)(FIN & flags); // MBZ-LO
        writeSequence(pContents, 4, _seq);
        initializeFlags(flags);
        // load in the data
        System.arraycopy(contents, 0, pContents, HEADER_SIZE, contents.length);
        _packet = new Packet(dstLink, srcLink, pContents);
    }

    /**
     * Constructs a NachosMessage from the raw Packet off
     * the Nachos NetworkLink interface.
     * @param raw the raw packet received from the Nachos hardware.
     * @throws MalformedPacketException if the Packet is malformed.
     */
    public NachosMessage(Packet raw) throws MalformedPacketException {
        // make sure we have a valid header
        if (raw.contents.length < HEADER_SIZE) {
            throw new MalformedPacketException();
        }
        // dport sport mbzhi mbzlo seq[4] dat dat dat
        final int dataOffset = HEADER_SIZE;
        _payload = new byte[ raw.contents.length - dataOffset ];
        _dstHost = raw.dstLink;
        _srcHost = raw.srcLink;
        _dstPort = raw.contents[0];
        _srcPort = raw.contents[1];
        if (0 != raw.contents[2]) {
            System.err.println("MBZ-HI = "+Integer.toHexString(raw.contents[2]));
            throw new MalformedPacketException();
        }
        if (0 != ((0xFF - 8) & raw.contents[3])) {
            throw new MalformedPacketException();
        }
        initializeFlags(raw.contents[3]);
        initializeSeq(raw.contents, 4);
        System.arraycopy( raw.contents, dataOffset,
                          _payload, 0, _payload.length);
    }

    public int getSourceHost() {
        return _srcHost;
    }

    public int getSourcePort() {
        return _srcPort;
    }

    public int getDestHost() {
        return _dstHost;
    }

    public int getDestPort() {
        return _dstPort;
    }

    public boolean isACK() {
        return _ack;
    }

    public boolean isFIN() {
        return _fin;
    }

    public boolean isSTP() {
        return _stp;
    }

    public boolean isSYN() {
        return _syn;
    }

    public int getSequence() {
        return _seq;
    }

    public byte[] getPayload() {
        return _payload;
    }

    public Packet toPacket() {
        return _packet;
    }

    @Override
    public String toString() {
        return "NachosMessage[" +
                " SRC=("+getSourceHost()+":"+getSourcePort()+")" +
                " DST=("+getDestHost()+":"+getDestPort()+")" +
                (isACK() ? " ACK" : "") +
                (isFIN() ? " FIN" : "") +
                (isSTP() ? " STP" : "") +
                (isSYN() ? " SYN" : "") +
                " SEQ="+getSequence() +
                " SIZE="+_payload.length +
                ']';
    }

    private void initializeFlags(byte mbzLo) {
        _syn = (SYN == (SYN & mbzLo));
        _ack = (ACK == (ACK & mbzLo));
        _stp = (STP == (STP & mbzLo));
        _fin = (FIN == (FIN & mbzLo));
    }

    private void initializeSeq(byte[] bytes, int offset) {
        // it's networking, so always in "network endian"
        _seq = ((bytes[offset]     & 0xFF) << 24)
              |((bytes[offset + 1] & 0xFF) << 16)
              |((bytes[offset + 2] & 0xFF) << 8)
              | (bytes[offset + 3] & 0xFF);
    }

    private void writeSequence(byte[] bytes, int offset, int seq) {
        bytes[offset]     = (byte)((seq >> 24) & 0xFF);
        bytes[offset + 1] = (byte)((seq >> 16) & 0xFF);
        bytes[offset + 2] = (byte)((seq >>  8) & 0xFF);
        bytes[offset + 3] = (byte)((seq      ) & 0xFF);
    }

    private byte[] _payload;
    private boolean _ack;
    private boolean _fin;
    private boolean _stp;
    private boolean _syn;
    private int _seq;
    private int _srcHost;
    private int _srcPort;
    private int _dstHost;
    private int _dstPort;
    private Packet _packet;
}
