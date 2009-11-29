package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.SerialConsole;
import nachos.threads.KThread;

public class NetKernelTest
{
    public static void selfTest() {
        final SerialConsole console = Machine.console();
        KThread serverThread = new KThread(new Runnable() {
            public void run() {
                pingServer();
            }
        });

        serverThread.fork();

        System.out.println("Press any key to start the network test...");
        console.readByte();

        int local = Machine.networkLink().getLinkAddress();

        // ping this machine first
        ping(local);
        System.err.println("Self Ping OK");

        // if we're 0 or 1, ping the opposite
        ping(0 == local ? 1 : 0);
    }

    /**
     * Pings always go from port 0 to port 1, and then are reflected back.
     *
     * @param dstLink the network link to ping.
     */
    private static void ping(int dstLink) {
        final int srcLink = Machine.networkLink().getLinkAddress();
        final int srcPort = 0;
        final int dstPort = 1;

        System.out.println("PING " + dstLink + ":" + dstPort
                + " from " + srcLink + ":" + srcPort);

        long startTime = Machine.timer().getTime();

        NachosMessage ping;

        try {
            ping = new NachosMessage(dstLink, dstPort, srcLink, srcPort,
                    new byte[0]);
        }
        catch (MalformedPacketException e) {
            Lib.assertNotReached();
            return;
        }

        System.out.println("Sending PING");
        postOffice.send(ping);
        System.out.println("Waiting for ACK");
        NachosMessage ack = postOffice.receive(srcPort);
        System.out.println("Received ACK from " + ack.getSourceHost()+ ":" + ack.getSourcePort());

        long endTime = Machine.timer().getTime();

        System.out.println("time=" + (endTime - startTime) + " ticks");
    }

    private static void pingServer() {
        while (true) {
            System.out.println("Waiting on Ping(:1) ...");
            NachosMessage ping = postOffice.receive(1);

            NachosMessage ack;
            try {
                ack = NachosMessage.pong(ping);
            } catch (MalformedPacketException e) {
                Lib.assertNotReached("should never happen haha");
                continue;
            }
            System.out.println("Sending ACK to " + ack.getDestHost()+ ":" + ack.getDestPort());
            postOffice.send(ack);
            System.out.println("ACK away");
        }
    }
    private static PostOffice postOffice = new PostOffice();
}
