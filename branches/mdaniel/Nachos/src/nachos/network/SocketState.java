package nachos.network;

public enum SocketState {
    /**
     * no connection exists
     */
    CLOSED,
    /**
     * sent a SYN packet, waiting for a SYN/ACK packet.
     */
    SYN_SENT,
    /**
     * received a SYN packet, waiting for an app to call accept().
     */
    SYN_RCVD,
    /**
     * a full-duplex connection has been established, data transfer can take place.
     */
    ESTABLISHED,
    /**
     * received a STP packet, can still receive data but cannot send data.
     */
    STP_RCVD,
    /**
     * the app called close(), sent an STP packet but still need to retransmit
     * unacknowledged data.
     */
    STP_SENT,
    /**
     * send a FIN packet, waiting for a FIN/ACK packet.
     */
    CLOSING,
}
