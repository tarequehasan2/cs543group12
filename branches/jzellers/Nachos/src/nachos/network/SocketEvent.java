package nachos.network;

public enum SocketEvent {
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
