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

	/**
	 *  gets a new LotterySthreadState class
	 */
    @Override
	protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
        	  thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
            
	}

    /**
     * Uses the superclass to decrease priority and, if successful, reduces the number of tickets
     * assigned to the current thread.   The donated tickets will then be recalculated.
     */
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
	
	/**
     * Uses the superclass to increase priority and, if successful, reduces the number of tickets
     * assigned to the current thread.   The donated tickets will then be recalculated.
     */
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

	/**
     * Uses the superclass to set the priority, then sets the number of tickets assigned to the current
     *  thread equal to that priority.   The donated tickets will then be recalculated.
     */
	@Override
	public void setPriority(KThread thread, int priority) {
		super.setPriority(thread, priority);

    	boolean intStatus = Machine.interrupt().disable(); 
		//Super already handled assertions
		((LotteryThreadState)thread.schedulingState).numberOfTickets=priority;
		if (((LotteryThreadState)thread.schedulingState).waitingInQueue != null){
			((LotteryThreadState)thread.schedulingState).calculateTickets(
				((LotteryThreadState)thread.schedulingState).waitingInQueue
				);
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Same as <code>setPriority(KThread, int)</code>, except that <code>KThread.currentThread()</code>
	 *  is the default. 
	 */
	@Override
	public void setPriority(int priority) {

    	boolean intStatus = Machine.interrupt().disable(); 
		setPriority(KThread.currentThread(), priority);
		Machine.interrupt().restore(intStatus);
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
    
    /**
     *Extended the threadstate in order to store the number of tickets, donated tickets
     *and impement a new method of calculating donation, since it is inherently different
     *than the PriorityScheduler 
     */
    protected class LotteryThreadState extends ThreadState {

    	/**
    	 * Calls the superclass to set the priority and sets the number of tickets equal to the priority
    	 * @see nachos.threads.PriorityScheduler.ThreadState#setPriority(int)
    	 */
    	@Override
    	public void setPriority(int priority) {
 			Lib.assertTrue(Machine.interrupt().disabled());
 			this.numberOfTickets = priority;
			super.setPriority(priority);
		}
 		
    	/**
    	 * Calls waitforAccess in the superclass and recalculates the ticket donation
    	 * @see nachos.threads.PriorityScheduler.ThreadState#waitForAccess(nachos.threads.PriorityScheduler.PriorityQueue)
    	 */
		@Override
		public void waitForAccess(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			super.waitForAccess(waitQueue);
			calculateTickets(waitQueue);
		}
		
		
		private int numberOfTickets = 0;
		private int donatedTickets = 0;
		public LotteryThreadState(KThread thread) {
			super(thread);
			this.numberOfTickets=priorityDefault;
		}
		
		/**
		 * The first method called in calculate tickets, will add all of the tickets in the queue to 
		 * the current lockolder of the waitQueue.   If the lockHolder is waiting in a queue, the tickets
		 * will be recalculated there as well.
		 * 
		 * @param waitQueue
		 */
		public void calculateTickets(PriorityQueue waitQueue) {
			calculateTickets(waitQueue, new ArrayList<PriorityQueue>());
		}
		
		//helper method that continues iterating through queues until a lockholder is not waiting in 
		//a queue or a quue has already been calculated.  donates the number of tickets of all waiters
		// to the lockholder.
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
		
		// gets the total number of tickets in a given queue.
		private int getNumTicketsInThisQueue(PriorityQueue waitQueue) {
			int runningTotal = this.numberOfTickets;
			for (ThreadState threadState : waitQueue.queue){
				runningTotal += ((LotteryThreadState)threadState).numberOfTickets + ((LotteryThreadState)threadState).donatedTickets;
			}
			return runningTotal;

		}

    	
    }
    
    /**
     * Extended the Priority queue to reimplement nextThread.  This is where the lottery actually happens.
     */
    protected class LotteryQueue extends PriorityQueue{
    	/**
    	 * Picks a next thread via a lottery.   If the total tickes in the queue == 0, then the lottery is not held.
    	 * Otherwise, the lottery is held by calculating the donated tickets and the actual tickets in the
    	 * queue.  It uses the psuedorandom generator to generate a random number mod the total tickets in the queue.
    	 * This provides a ticket threshold.   We iterate through the queue, counting the tickets until we reach
    	 * the threshold.  The winning thread is the one whose ticket count exceeds the number of the lottery.
    	 * 
    	 * It is removed from the queue and becomes the new lockholder.  Donations are removed and the tickets are
    	 * recalculated.
    	 * 
    	 * @see nachos.threads.PriorityScheduler.PriorityQueue#nextThread()
    	 */
		@Override
		public KThread nextThread() {
			if (queue.size() == 0)
				return null;
		    Lib.assertTrue(Machine.interrupt().disabled());
		    int totalTickets = 0;
		    if (lockHolder!=null){
		     totalTickets=((LotteryThreadState)lockHolder).donatedTickets + ((LotteryThreadState)lockHolder).numberOfTickets;
		    }else{
		    	for (ThreadState currentThreadState : queue){
			    	Lib.assertTrue(currentThreadState instanceof LotteryThreadState);
			    	totalTickets += ((LotteryThreadState)currentThreadState).numberOfTickets +((LotteryThreadState)currentThreadState).donatedTickets;
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
		    	ticketCount += ((LotteryThreadState)currentThreadState).numberOfTickets + ((LotteryThreadState)currentThreadState).donatedTickets;
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

		/**
		 * Decided that since this is a lottery, pickNextThread isn't much use, so it returns what would be retuned
		 * in the priority scheme.   Do not depend on this to tell you what will be returned by nextThread().
		 */
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
