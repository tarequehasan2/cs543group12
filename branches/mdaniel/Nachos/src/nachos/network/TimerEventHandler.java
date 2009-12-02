package nachos.network;


public class TimerEventHandler implements Runnable{

	private PostOfficeSender pos;
	private static final long TICKS = 20000L;
	TimerEventHandler(PostOfficeSender pos){
		this.pos = pos;
	}
	
	private static boolean running = true;
	@Override
	public void run() {		
		while (running){
			NetKernel.alarm.waitUntil(TICKS);
			pos.resendAllUnacked();
		}
		
	}

	/**
	 * Indicate to the driver that this thread should be terminated.  Called by the kernel.
	 */
	public static void terminate(){
		running = false;
	}

}