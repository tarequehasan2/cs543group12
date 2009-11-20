package nachos.network;

public enum ConnectionEvent {
	CONNECT,
	ACCEPT,
	RECV,
	SEND,
	CLOSE,
	TIMER,
	SYN,
	SYNACK,
	DATA,
	ACK,
	STP,
	FIN,
	FINACK;
}
