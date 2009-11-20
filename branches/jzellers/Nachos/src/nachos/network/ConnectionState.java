package nachos.network;

public enum ConnectionState {
	CLOSED,
	SYN_SENT,
	SYN_RCVD,
	ESTABLISHED,
	STP_RCVD,
	STP_SENT,
	CLOSING; 
}
