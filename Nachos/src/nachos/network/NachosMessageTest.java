package nachos.network;

import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.NetworkLink;
import nachos.machine.Packet;
import static java.lang.Integer.toHexString;
import static nachos.machine.Lib.assertTrue;
import static nachos.machine.Lib.assertNotReached;

public class NachosMessageTest
{
    public static void selfTest() {
        testSimple();
        testSYN();
        testDeserialize();
    }

    public static void testDeserialize() {
        final byte dHost = 15;
        final byte dPort = 112;
        final byte sHost = 25;
        final byte sPort = 109;
        final int seq = (int)System.currentTimeMillis();
        final byte[] PACKET_BYTES = new byte[] {
                NetworkLink.networkID,
                dHost,
                sHost,
                0, // data length, calculated below
                /// Packet.contents begins here ///
                dPort,
                sPort,
                0, // MBZ-HI
                NachosMessage.SYN, // MBZ-LO
                (byte)((seq >> 24) & 0xFF),
                (byte)((seq >> 16) & 0xFF),
                (byte)((seq >>  8) & 0xFF),
                (byte)((seq      ) & 0xFF),
                (byte)0xCA,
                (byte)0xFE,
                (byte)0xBA,
                (byte)0xBE,
        };
        PACKET_BYTES[3] = (byte)(PACKET_BYTES.length - 4);
        Packet p;
        try {
            p = new Packet(PACKET_BYTES);
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            assertNotReached(e.getMessage());
            throw new Error(); // won't reach this
        }
        // do basic packet checks
        assertTrue( sHost == p.srcLink, "Bogus source link");
        assertTrue( dHost == p.dstLink, "Bogus dest link");
        assertTrue( p.contents.length == PACKET_BYTES[3],
                "content length mismatch");
        NachosMessage m;
        try {
            m = new NachosMessage(p);
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            assertNotReached(e.getMessage());
            throw new Error(); // won't reach this
        }
        assertTrue( dHost == m.getDestHost(), "d-host");
        assertTrue( dPort == m.getDestPort(), "d-port");
        assertTrue( sHost == m.getSourceHost(), "s-host");
        assertTrue( sPort == m.getSourcePort(), "s-port");
        assertTrue( !m.isACK(), "ACK?!");
        assertTrue( !m.isFIN(), "FIN?!");
        assertTrue( !m.isSTP(), "ATP?!");
        assertTrue(  m.isSYN(), "No SYN?!");
        assertTrue(seq == m.getSequence(), "Wrong sequence");
        final byte[] payload = m.getPayload();
        assertTrue( payload.length == 4, "wrong payload size");
        assertTrue( PACKET_BYTES[ PACKET_BYTES.length - 4 ] == payload[0], "wrong payload[0]");
        assertTrue( PACKET_BYTES[ PACKET_BYTES.length - 3 ] == payload[1], "wrong payload[1]");
        assertTrue( PACKET_BYTES[ PACKET_BYTES.length - 2 ] == payload[2], "wrong payload[2]");
        assertTrue( PACKET_BYTES[ PACKET_BYTES.length - 1 ] == payload[3], "wrong payload[3]");
    }

    public static void testSYN() {
        final int dHost = 15;
        final int dPort = 117;
        final int myLinkId = Machine.networkLink().getLinkAddress();
        final int srcPort = 109;

        NachosMessage syn;
        try {
            syn = NachosMessage.syn(dHost, dPort, myLinkId, srcPort);
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            assertNotReached(e.getMessage());
            throw new Error(); // won't reach this
        }
        assertTrue(dHost == syn.getDestHost(), "Wrong dest host");
        assertTrue(dPort == syn.getDestPort(), "Wrong dest port");
        assertTrue(myLinkId == syn.getSourceHost(), "Wrong source host");
        assertTrue(srcPort == syn.getSourcePort(), "Wrong source port");
        assertTrue(0 != syn.getSequence(), "No sequence?");
        assertTrue( !syn.isACK(), "ACK?!" );
        assertTrue( !syn.isFIN(), "FIN?!" );
        assertTrue( !syn.isSTP(), "STP?!" );
        assertTrue(  syn.isSYN(), "No SYN, huh?" );
    }

    public static void testSimple() {
        final int dHost = 1;
        final int dPort = 15;
        final int sHost = 0;
        final int sPort = 25;

        NachosMessage m;
        try {
            m = new NachosMessage(dHost, dPort, sHost, sPort, new byte[0]);
        } catch (MalformedPacketException e) {
            e.printStackTrace(System.err);
            assertNotReached(e.getMessage());
            throw new Error(); // won't reach this
        }

        assertTrue(dHost == m.getDestHost(), "Wrong dest host");
        assertTrue(dPort == m.getDestPort(), "Wrong dest port");
        assertTrue(sHost == m.getSourceHost(), "Wrong src host");
        assertTrue(sPort == m.getSourcePort(), "Wrong src port");
        assertTrue(m.getPayload().length == 0,
                "What kind of junk are you putting in my payload?");
        assertTrue( !m.isACK(), "ACK?!");
        assertTrue( !m.isFIN(), "FIN?!");
        assertTrue( !m.isSTP(), "STP?!");
        assertTrue( !m.isSYN(), "SYN?!");
        Packet p = m.toPacket();
        assertTrue(null != p, "NULL PACKET?!");
        assertTrue(p.srcLink == sHost, "Bogus packet src");
        assertTrue(p.dstLink == dHost, "Bogus packet dest");
        assertTrue(p.contents.length == 8, "Bogus packet contents");
        assertTrue(p.contents[0] == dPort, "Bogus packet d-port");
        assertTrue(p.contents[1] == sPort, "Bogus packet s-port");
        assertTrue(p.contents[2] == 0, "Bogus packet MBZ-HI");
        assertTrue(p.contents[3] == 0, "Bogus packet MBZ-LO");
        int seq = m.getSequence();
        final byte seq0 = (byte)((seq >> 24) & 0xFF);
        final byte seq1 = (byte)((seq >> 16) & 0xFF);
        final byte seq2 = (byte)((seq >>  8) & 0xFF);
        final byte seq3 = (byte)(seq         & 0xFF);
        assertTrue(p.contents[4] == seq0,
                "Bogus packet SEQ[0], wanted "
                        +toHexString(seq0)+", got "+toHexString(p.contents[4]));
        assertTrue(p.contents[5] == seq1,
                "Bogus packet SEQ[1], wanted "
                        +toHexString(seq1)+", got "+toHexString(p.contents[5]));
        assertTrue(p.contents[6] == seq2,
                "Bogus packet SEQ[2], wanted "
                        +toHexString(seq2)+", got "+toHexString(p.contents[6]));
        assertTrue(p.contents[7] == seq3,
                "Bogus packet SEQ[3], wanted "
                        +toHexString(seq3)+", got "+toHexString(p.contents[7]));
    }
}
