package nachos.network;

public class ProtocolStateMachine {
    public static interface ProtocolEvent {
        /**
         * an app called connect().
         */
        public void onCONNECT();

        /**
         * an app called accept().
         */
        public void onACCEPT();

        /**
         * an app called read().
         */
        public void onRECV();

        /**
         * an app called write().
         */
        public void onSEND();

        /**
         * an app called close().
         */
        public void onCLOSE();

        /**
         * the retransmission timer ticked.
         */
        public void onTIMER();

        /**
         * a SYN packet is received (a packet with the SYN bit set).
         */
        public void onSYN();

        /**
         * a SYN/ACK packet is received (a packet with the SYN and ACK bits set).
         */
        public void onSYNACK();

        /**
         * a data packet is received (a packet with none of the SYN, ACK, STP, or FIN
         * bits set).
         */
        public void onDATA();

        /**
         * an ACK packet is received (a packet with the ACK bit set).
         */
        public void onACK();

        /**
         * a STP packet is received (a packet with the STP bit set).
         */
        public void onSTP();

        /**
         * a FIN packet is received (a packet with the FIN bit set).
         */
        public void onFIN();

        /**
         * a FIN/ACK packet is received (a packet with the FIN and ACK bits set).
         */
        public void onFINACK();
    }

    public ProtocolEvent getMachine(SocketKey key) {
        return null;
    }

}
