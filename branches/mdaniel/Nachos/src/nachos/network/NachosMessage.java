package nachos.network;

import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;

public class NachosMessage
{
    /**
     * Constructs a new NachosMessage which can be used to ACK the
     * provided message. It does this by reversing the source and
     * destination parts, setting the ACK flag and setting this message's
     * sequence number to the sequence number of your message.
     * @param msg the message to ack.
     * @return the new message which will ack the provided message.
     * @throws MalformedPacketException if unable to construct your message
     */
    public static NachosMessage ack(NachosMessage msg)
            throws MalformedPacketException {
        NachosMessage result = new NachosMessage(
                msg.getSourceHost(), msg.getSourcePort(),
                msg.getDestHost(), msg.getDestPort(),
                ACK, new byte[0]);
        result._seq = msg._seq;
        return result;
    }

    public static NachosMessage fin(int destHost, int destPort,
                                    int sourceHost, int sourcePort)
            throws MalformedPacketException {
        return new NachosMessage(destHost, destPort,
                sourceHost, sourcePort,
                FIN, new byte[0]);
    }

    /**
     * Creates a Nachos Message suitable for reporting FIN/ACK
     * to the provided FIN message.
     * @param fin The FIN message to which we are replying.
     * @return a newly constructed Nachos Message suitable for replying to
     * the provided message.
     * @throws MalformedPacketException if unable to construct said message.
     */
    public static NachosMessage ackFin(NachosMessage fin)
            throws MalformedPacketException {
        NachosMessage result = new NachosMessage(
                fin.getSourceHost(), fin.getSourcePort(),
                fin.getDestHost(), fin.getDestPort(),
                (byte)(FIN | ACK),
                new byte[0]);
        result._seq = fin._seq;
        return result;
    }

    /**
     * Creates a Nachos Message suitable for reporting SYN/ACK
     * to the provided SYN message.
     * @param syn The SYN message to which we are replying.
     * @return a newly constructed Nachos Message suitable for replying to
     * the provided message.
     * @throws MalformedPacketException if unable to construct said message.
     */
    public static NachosMessage ackSyn(NachosMessage syn)
            throws MalformedPacketException {
        NachosMessage result = new NachosMessage(
                syn.getSourceHost(), syn.getSourcePort(),
                syn.getDestHost(), syn.getDestPort(),
                (byte)(SYN | ACK),
                new byte[0]);
        result._seq = syn._seq;
        return result;
    }

    public static NachosMessage stp(int host, int port, int srcHost, int srcPort)
            throws MalformedPacketException {
        return new NachosMessage(host, port,
                srcHost, srcPort,
                STP, new byte[0]);
    }

    public static NachosMessage syn(int dstLink, int dstPort, int srcHost, int srcPort)
            throws MalformedPacketException {
        return new NachosMessage(
                dstLink, dstPort,
                srcHost, srcPort,
                SYN, new byte[0]);
    }

    /**
     * Constructs a shortened version of the given NachosMessage,
     * which only contains the last <em>bytes</em> bytes of the original message.
     * It is my discretion to return a modified version of your message
     * or a new message, so you should consider the first parameter as
     * my property from now on.
     * @param msg the message to shorten its payload
     * @param bytes the number of bytes <u>remaining</u> in the new message.
     * @return a (lets call it) new message containing only <em>bytes</em>
     * bytes of payload.
     */
    public static NachosMessage shorten(NachosMessage msg, int bytes) {
        byte[] tmp = new byte[ bytes ];
        System.arraycopy(msg._payload, bytes, tmp, 0, tmp.length );
        msg._payload = tmp;
        return msg;
    }

    public static NachosMessage pong(NachosMessage ping)
            throws MalformedPacketException {
        return new NachosMessage(
                ping.getSourceHost(), ping.getSourcePort(),
                ping.getDestHost(), ping.getDestPort(),
                ping.getPayload());
    }

    /** Indicates the number of bytes required for our header. */
    private static final int HEADER_SIZE = 2 + 2 + 4;

    /** Maximum number of bytes I will accept as a payload. */
    public static final int MAX_CONTENTS_LENGTH =
	    Packet.maxContentsLength - HEADER_SIZE;
    /** The upper bound on the value of any source or destination port. */
    public static final int PORT_LIMIT = Byte.MAX_VALUE;

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

    private NachosMessage(int dstLink, int dstPort, int srcLink, int srcPort,
                         byte flags, byte[] contents)
            throws MalformedPacketException {
        // the Packet constructor will check the link-ids for us
        if (dstPort < 0 || dstPort > PORT_LIMIT) {
            throw new MalformedPacketException();
        }
        if (srcPort < 0 || srcPort > PORT_LIMIT) {
            throw new MalformedPacketException();
        }
        _dstHost = dstLink;
        _dstPort = dstPort;
        _srcHost = srcLink;
        _srcPort = srcPort;
        initializeFlags(flags);
        // clone their byte array since in Java, arrays are live
        byte[] tmp = new byte[ contents.length ];
        System.arraycopy(contents, 0, tmp, 0, tmp.length );
        _payload = tmp;
        _seq = Integer.MIN_VALUE;
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
//        System.err.print("PACKET(");
//        for (byte b : raw.packetBytes) {
//            System.err.print(Integer.toHexString(b & 0xFF));
//            System.err.print(' ');
//        }
//        System.err.println(")");
//        System.err.flush();
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
        if (0 != ( (Byte.MAX_VALUE - (8+4+2+1)) & raw.contents[3])) {
            System.err.println("NachosMessage:Bogus Flags:="+Integer.toHexString(raw.contents[3]));
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

    public void setSequence(int seq) {
        _seq = seq;
    }

    public byte[] getPayload() {
        return _payload;
    }

    public Packet toPacket() throws MalformedPacketException {
        return createPacket();
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

    private Packet createPacket() throws MalformedPacketException {
        byte[] pContents = new byte[ HEADER_SIZE + _payload.length ];
        pContents[0] = (byte)(_dstPort & 0xFF);
        pContents[1] = (byte)(_srcPort & 0xFF);
        pContents[2] = (byte)0; // MBZ-HI
        pContents[3] = makeMbzLo();
        writeSequence(pContents, 4, _seq);
        // load in the data
        System.arraycopy(_payload, 0, pContents, HEADER_SIZE, _payload.length);
        return new Packet(_dstHost, _srcHost, pContents);
    }

    private byte makeMbzLo() {
        return (byte)(
                 (isACK() ? ACK : 0)
                |(isFIN() ? FIN : 0)
                |(isSTP() ? STP : 0)
                |(isSYN() ? SYN : 0)
                );
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
//    private Packet _packet;
}
