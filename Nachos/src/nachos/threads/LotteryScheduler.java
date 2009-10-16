package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
public class LotteryScheduler extends Scheduler {
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
	return new LotteryQueue(transferPriority);
    }

    public int getNumTickets(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getNumTickets();
    }

    public int getEffectiveNumTickets(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectiveNumTickets();
    }

    public void setNumTickets(KThread thread, int tickets) {
    	Lib.assertTrue(Machine.interrupt().disabled());
    		       
    	Lib.assertTrue(tickets >= minimumNumberOfTickets &&
    		   tickets <= maximumNumberOfTickets);
    	
    	ThreadState threadState = getThreadState(thread); 
    	threadState.setNumTickets(tickets);
        }

    public boolean increaseTickets() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int tickets = getNumTickets(thread);
	if (tickets == maximumNumberOfTickets)
	    return false;

	setNumTickets(thread, tickets+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreaseTickets() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int tickets = getNumTickets(thread);
	if (tickets == minimumNumberOfTickets)
	    return false;

	setNumTickets(thread, tickets-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }
    
    private static final char dbgThread = 't';

    
    /**
     * The default number of tickets held bye a new thread. Do not change this value.
     */
    public static final int defaultNumTickets = 1;
    /**
     * The minimum number of tickets that a thread can have. Do not change this value.
     */
    public static final int minimumNumberOfTickets = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int maximumNumberOfTickets = Integer.MAX_VALUE;    


    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that picks the next thread by a lottery.
     */
    protected class LotteryQueue extends ThreadQueue {
	LotteryQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // Get the next thread out of the queue
	    ThreadState threadState = null;
	    KThread thread = null;
	    // if we need to pick a next thread and there's threads in the queue
	    if (nextThreadPosition == -1 && queue.size() != 0)
	    {
	    	holdLottery();
	    }
	    if (nextThreadPosition != -1)
	    {
	    	threadState = queue.get(nextThreadPosition);
	    	queue.remove(nextThreadPosition);
		    if (transferPriority) {
		    	if (lockHolder != null){
		    		lockHolder.numTicketsDonated = 0;
		    	}
		    	lockHolder = threadState;
		    	if (threadState != null){
		    		threadState.donateTickets(threadState.waitingInQueue);
		    		threadState.waitingInQueue = null;
		    	}
		    	
		    }
		    thread = (threadState == null) ? null : threadState.thread;
		    numTicketsHeld = numTicketsHeld - threadState.getEffectiveNumTickets();
	    }
	    if (thread != null && threadState != null)
	    	Lib.debug(dbgThread, "Next thread is " + thread.getName() + 
	    			" with effective number of tickets " + threadState.getEffectiveNumTickets());
	    if (queue.size() != 0)
	    {
	    	holdLottery();
	    }
	    return thread;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
		// if we need to pick the next thread, hold a lottery
		if (nextThreadPosition == -1 && queue.size() != 0)
		{
	    	boolean intStatus = Machine.interrupt().disable(); 
			holdLottery();
	    	Machine.interrupt().restore(intStatus);
		}
		// return the next thread
		if (nextThreadPosition != -1)
		{
			return queue.get(nextThreadPosition);
		}
		else
		{
			return null;
		}
	}

	/**
	 * Get the next thread to run using a lottery system.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected void holdLottery()
	{
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // If we have threads in the queue
	    if (queue.size() > 0)
	    {
	    	// generate a random number
		    Random generator = new Random();
		    int lottery = generator.nextInt() % numTicketsHeld;
		    int ticketCount = 0;
		    for (int i = 0; i < queue.size(); i++)
		    {
		    	// as you get to a thread, add its tickets to the counter
		    	ticketCount += queue.get(i).getEffectiveNumTickets();
		    	// if we go over our lottery number, then we've found our thread
		    	if (ticketCount >= lottery)
		    	{
		    		nextThreadPosition = i;
		    		break;
		    	}
		    }
	    }
	    // If there's no threads, there's no next thread
	    else
	    {
	    	nextThreadPosition = -1;
	    }
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    for (ThreadState threadState: queue){
	    	System.out.println(threadState.thread + " -- Number of Tickets " + threadState.getNumTickets() + 
	    			" -- Effective Number of Tickets " + threadState.getEffectiveNumTickets());
	    }
	}

	/**
	 * <tt>true</tt> if this queue should transfer tickets from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	public int numTicketsHeld = 0;
    protected ThreadState lockHolder;
    protected int nextThreadPosition = -1;
    protected ArrayList<ThreadState> queue = new ArrayList<ThreadState>();

    }
    
 

    /**
     * The scheduling state of a thread. This should include the thread's
     * number of tickets, its effective number of tickets, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState{
	
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread){
	    this.thread = thread;
	    setNumTickets(defaultNumTickets);
	    isDonating = false;
	    changeInDonation = 0;
	    numTicketsDonated = 0;
	}

	/**
	 * Return the number of tickets held by the associated thread.
	 *
	 * @return	the number of tickets held by the associated thread.
	 */
	public int getNumTickets() {
	    return numTicketsHeld;
	}

	/**
	 * Return the effective number of tickets held by the associated thread.
	 *
	 * @return	the effective number of tickets held by the associated thread.
	 */
	public int getEffectiveNumTickets() {
		//  Effective number of tickets should be the sum of tickets held and tickets donated
		return this.numTicketsHeld + this.numTicketsDonated;
		
	}

	/**
	 * Set the number of tickets held by the associated thread to the specified value.
	 *
	 * @param	tickets	the new number of tickets.
	 */
	public void setNumTickets(int tickets) {
	    if (this.numTicketsHeld == tickets)
		return;
	    
	    if (isDonating)
	    {
	    	changeInDonation = tickets - numTicketsHeld;
	    }
	    this.numTicketsHeld = tickets;
	    
	    if (this.waitingInQueue != null){
	    	donateTickets(this.waitingInQueue);
	    }
	}

	/**
	 * Receive specified number of donated tickets
	 *
	 * @param	tickets	the number of donated tickets.
	 */
	public void addDonatedTickets(int tickets) {
		// recursing is handled by the donateTickets function
	    this.numTicketsDonated += tickets;
	    this.waitingInQueue.numTicketsHeld += tickets;
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified lottery queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(LotteryQueue waitQueue) {
		this.waitingInQueue = waitQueue;
		waitQueue.queue.add(this);
		waitQueue.numTicketsHeld += this.getEffectiveNumTickets();
		donateTickets(waitQueue);
	}

	private void donateTickets(LotteryQueue waitQueue) {
		donateTickets(waitQueue, new ArrayList<LotteryQueue>());
	}

	private void donateTickets(LotteryQueue waitQueue,
			List<LotteryQueue> visitedQueues) {
		// If there's no lockholder or we've been here before, stop recursing
		if (waitQueue.lockHolder == null || visitedQueues.contains(waitQueue))
		{
			return;
		}

    	boolean donated = false;
		// if we have to transfer priority
		if (waitQueue.transferPriority ){
	    	boolean intStatus = Machine.interrupt().disable(); 
	    	int ticketsToDonate = 0;
	    	// check if the thread is already donating tickets
	    	if (isDonating)
	    	{
	    		// if so, we only need to donate the difference
	    		ticketsToDonate = changeInDonation;
	    	}
	    	else
	    	{
	    		// if not, then we can clear the difference and donate them all
	    		ticketsToDonate = getEffectiveNumTickets();
	    		changeInDonation = 0;
	    	}
    		waitQueue.lockHolder.addDonatedTickets(ticketsToDonate);
    		donated = true;
	    	Machine.interrupt().restore(intStatus);
	    	
			Lib.debug(dbgThread, "Donating " + ticketsToDonate + " tickets " 
					+ " to " + waitQueue.lockHolder.thread.getName());
		}
		
		visitedQueues.add(waitQueue);
		
		if (waitQueue.lockHolder.waitingInQueue != null){
			donateTickets(waitQueue.lockHolder.waitingInQueue, visitedQueues);
		}
		// store whether we donated and set change to 0;
		isDonating = donated;
		changeInDonation = 0;
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>wait</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(LotteryQueue waitQueue) {
		// jnz-  there should be nothing in the queue, so the first thread through is the lockHolder.
	    
		if (waitQueue.transferPriority){
		waitQueue.lockHolder = this;
		}
	    
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	protected LotteryQueue waitingInQueue;
	protected int numTicketsHeld;
	protected boolean isDonating;
	protected int numTicketsDonated;
	protected int changeInDonation;
    }
}
