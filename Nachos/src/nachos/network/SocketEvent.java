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
	
	static SocketEvent getEvent(NachosMessage message){
		if (message.isACK()){
			return ACK;
		}
		else if (message.isFIN()){
			return FIN;
		}
		else if (message.isSTP()){
			return STP;
		}
		else if (message.isSYN()){
			return SYN;
		}else{
			return DATA;
		}
	}
}
