package nachos.threads;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {

    /* The default priority for a new thread. Do not change this value.
    */
   public static final int priorityDefault = 1;
   /**
    * The minimum priority that a thread can have. Do not change this value.
    */
   public static final int priorityMinimum = 1;
   /**
    * The maximum priority that a thread can have. Do not change this value.
    */
   public static final int priorityMaximum = Integer.MAX_VALUE;  
   
   /**
    * Tests whether this module is working.
    */
  public static void selfTest() {
	Lib.debug(dbgThread, "Enter LotteryScheduler.selfTest");
	KThread[] threads = new KThread[20]; 
	for (int i = 1; i < 11; i++)
	{
		threads[i-1] = new KThread(new Runnable() {
			   public void run() {
				   int j = 0;
				   while (j < 20)
				   {
					   long currentTime = Machine.timer().getTime();
					   while (Machine.timer().getTime() < currentTime + 500)
					   {
						   KThread.yield();
					   }
					   System.out.println(KThread.currentThread().getName() + " loop # " + j);
					   j++;
				   }
				   }
			   }).setName("Thread #" + i);
	}
	for (int i = 0; i < 10; i++)
	{
		threads[i].fork();
		((LotteryScheduler.ThreadState)threads[i].schedulingState).setPriority(50*i);
	}
	KThread.yield();
  }

	
    @Override
	protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
        	  thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
            
	}

	@Override
	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		if (super.decreasePriority()){
			KThread thread = KThread.currentThread();
			((LotteryThreadState)thread.schedulingState).numberOfTickets--;
			if (((LotteryThreadState)thread.schedulingState).waitingInQueue != null){
				((LotteryThreadState)thread.schedulingState).calculateTickets(
						((LotteryThreadState)thread.schedulingState).waitingInQueue
						);
			}
		}else{
			Machine.interrupt().restore(intStatus);
			return false;
		}
		Machine.interrupt().restore(intStatus);
		return true;
	}

	@Override
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		if (super.increasePriority()){
			KThread thread = KThread.currentThread();
			((LotteryThreadState)thread.schedulingState).numberOfTickets++;
			if (((LotteryThreadState)thread.schedulingState).waitingInQueue != null){
				((LotteryThreadState)thread.schedulingState).calculateTickets(
						((LotteryThreadState)thread.schedulingState).waitingInQueue
						);
			}
		}else{
			Machine.interrupt().restore(intStatus);
			return false;
		}
		Machine.interrupt().restore(intStatus);
		return true;
		
	}

	@Override
	public void setPriority(KThread thread, int priority) {
		super.setPriority(thread, priority);
		
		//Super already handled assertions
		((LotteryThreadState)thread.schedulingState).numberOfTickets=priority;
		if (((LotteryThreadState)thread.schedulingState).waitingInQueue != null){
			((LotteryThreadState)thread.schedulingState).calculateTickets(
				((LotteryThreadState)thread.schedulingState).waitingInQueue
				);
		}
		
	}

	@Override
	public void setPriority(int priority) {
		setPriority(KThread.currentThread(), priority);
	}

	/**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	     return  new LotteryQueue(transferPriority);
    }
    
    protected class LotteryThreadState extends ThreadState {

 		public void setPriority(int priority) {
 			this.numberOfTickets = priority;
			super.setPriority(priority);
		}
 		
		@Override
		public void waitForAccess(PriorityQueue waitQueue) {
			super.waitForAccess(waitQueue);
			calculateTickets(waitQueue);
		}
		
		
		private int numberOfTickets = 0;
		private int donatedTickets = 0;
		public LotteryThreadState(KThread thread) {
			super(thread);
			this.numberOfTickets=priorityDefault;
		}
		
		
		public void calculateTickets(PriorityQueue waitQueue) {
			calculateTickets(waitQueue, new ArrayList<PriorityQueue>());
		}
		
		private void calculateTickets(PriorityQueue waitQueue,
				List<PriorityQueue> visitedQueues) {
			if (waitQueue.lockHolder == null || visitedQueues.contains(waitQueue))
				return;
			
			if (waitQueue.transferPriority){
				
		    	boolean intStatus = Machine.interrupt().disable(); 
		    	((LotteryThreadState)waitQueue.lockHolder).donatedTickets = getNumTicketsInThisQueue(waitQueue);
		    	Machine.interrupt().restore(intStatus);
				Lib.debug(dbgThread, "Donating priority of " + waitQueue.lockHolder.donation 
						+" to " + waitQueue.lockHolder.thread.getName());
			}
			
			visitedQueues.add(waitQueue);
			
			if (waitQueue.lockHolder.waitingInQueue != null){
				calculateTickets(waitQueue.lockHolder.waitingInQueue, visitedQueues);
			}
			
			 
			
		}
		private int getNumTicketsInThisQueue(PriorityQueue waitQueue) {
			int runningTotal = this.numberOfTickets;
			for (ThreadState threadState : waitQueue.queue){
				runningTotal += ((LotteryThreadState)threadState).numberOfTickets;
			}
			return runningTotal;

		}

    	
    }
    
    protected class LotteryQueue extends PriorityQueue{
		@Override
		public KThread nextThread() {
			if (queue.size() == 0)
				return null;
			//if (!transferPriority){
			//	return super.nextThread();
		//	}
		    Lib.assertTrue(Machine.interrupt().disabled());
		    // assuming that we have everything in order, we should be able to poll the queue.
		    int totalTickets = 0;
		    if (lockHolder!=null){
		     totalTickets=((LotteryThreadState)lockHolder).donatedTickets + ((LotteryThreadState)lockHolder).numberOfTickets;
		    }else{
		    	for (ThreadState currentThreadState : queue){
			    	Lib.assertTrue(currentThreadState instanceof LotteryThreadState);
			    	totalTickets += ((LotteryThreadState)currentThreadState).numberOfTickets;
		    	}
		    }
		    Random generator = new Random();
		    int lottery = (totalTickets==0) ? 0 : Math.abs(generator.nextInt()) % totalTickets;
	    	Lib.debug(dbgThread, queue.size() + " thread(s) in queue");
	    	Lib.debug(dbgThread, "Lottery Number is " + lottery + 
	    			" with total number of tickets " + totalTickets);
		    int ticketCount = 0;
		    ThreadState threadState = null;
		    for (ThreadState currentThreadState : queue){
		    	Lib.assertTrue(currentThreadState instanceof LotteryThreadState);
		    	ticketCount += ((LotteryThreadState)currentThreadState).numberOfTickets;
		    	if (ticketCount >= lottery){
		    		threadState = currentThreadState;
		    		queue.remove(threadState);
		    		break;
		    	}
		    }
		  
		    if (transferPriority) {
		    	if (lockHolder != null){
		    		((LotteryThreadState)lockHolder).donatedTickets = 0;
		    	}
		    	lockHolder = threadState;
		    	if (threadState != null){
		    		threadState.donatePriority(threadState.waitingInQueue);
		    		((LotteryThreadState)threadState).calculateTickets(threadState.waitingInQueue);
		    		threadState.waitingInQueue = null;
		    		
		    	}
		    	
		    }
		    KThread thread = (threadState == null) ? null : threadState.thread;
		    if (thread != null && threadState != null)
		    	Lib.debug(dbgThread, "Next thread is " + thread.getName() + 
		    			" with effective priority " + threadState.getEffectivePriority());
		    return thread;
		}

		@Override
		protected ThreadState pickNextThread() {
			// TODO Is there a valid pickNextThread for the lottery?   It certainly wouldn't return the same 
			// as nextThread, so what use is it?
			return super.pickNextThread();
		}

		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
		}
    }
    
        
}
