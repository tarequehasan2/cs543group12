package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {

	/**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
	boolean intStatus = Machine.interrupt().disable();
		       
	int priority = getThreadState(thread).getPriority();
	Machine.interrupt().restore(intStatus);
	return priority;
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
	boolean intStatus = Machine.interrupt().disable();
		       
	int effectivePriority = getThreadState(thread).getEffectivePriority();
	Machine.interrupt().restore(intStatus);
	return effectivePriority;
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	boolean intStatus = Machine.interrupt().disable();
	
	getThreadState(thread).setPriority(priority);
	Machine.interrupt().restore(intStatus);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
	boolean status = true;
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    status = false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return status;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
	boolean status = true;
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    status = false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return status;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    


    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
    	boolean intStatus = Machine.interrupt().disable();
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	ThreadState state = (ThreadState) thread.schedulingState;
	Machine.interrupt().restore(intStatus);
	return state;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
		boolean intStatus = Machine.interrupt().disable();
	    getThreadState(thread).waitForAccess(this);
		Machine.interrupt().restore(intStatus);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
		boolean intStatus = Machine.interrupt().disable();
	    getThreadState(thread).acquire(this);
		Machine.interrupt().restore(intStatus);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
		boolean intStatus = Machine.interrupt().disable();
	    // assuming that we have everything in order, we should be able to poll the queue.
	    ThreadState threadState = (queue.peek() == null) ? null : queue.poll();
	    lockHolder = threadState;
	    KThread thread = (threadState == null) ? null : threadState.thread;
		Machine.interrupt().restore(intStatus);
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
		boolean intStatus = Machine.interrupt().disable();
	    //  Assuming that we have everything in order, we should be able to peek at the queue
	    ThreadState state = queue.peek();
		Machine.interrupt().restore(intStatus);
		return state;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    for (ThreadState threadState: queue){
	    	System.out.println(threadState.thread);
	    }
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    protected ThreadState lockHolder;
    protected java.util.PriorityQueue<ThreadState> queue = new java.util.PriorityQueue<ThreadState>();

    }
    
 

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState> {
	
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread){
	    this.thread = thread;
	    this.creationTime = Machine.timer().getTime();
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
		boolean intStatus = Machine.interrupt().disable();
	    int returnPriority = priority;
		Machine.interrupt().restore(intStatus);
		return returnPriority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
		boolean intStatus = Machine.interrupt().disable();
		int priority = 0;
		
		//  Effective priority should be the larger of the effective priority or the donated priority.
		if (this.donation > this.priority){
			priority = this.donation;
		}else{
			priority = this.priority;
		}
		Machine.interrupt().restore(intStatus);
		return priority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
		boolean intStatus = Machine.interrupt().disable();
	    if (this.priority == priority) {
			Machine.interrupt().restore(intStatus);
			return;
	    }
	    
	    
	    this.priority = priority;
	    
	   //implement me
	    
	    if (this.priority >= this.donation){
	    	this.donation = 0;
	    }
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
		boolean intStatus = Machine.interrupt().disable();
		if (waitQueue.transferPriority 
				&& (waitQueue.lockHolder.priority < (this.priority)
						|| waitQueue.lockHolder.priority < (this.donation))){
			waitQueue.lockHolder.donation = (this.priority > this.donation) 
												? this.priority : this.donation;
		}
		waitQueue.queue.offer(this);
		Machine.interrupt().restore(intStatus);
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
	public void acquire(PriorityQueue waitQueue) {
		boolean intStatus = Machine.interrupt().disable();
		// jnz-  there should be nothing in the queue, so the first thread through is the lockHolder.
	    waitQueue.lockHolder = this;
	    waitQueue.queue.offer(this);
		Machine.interrupt().restore(intStatus);
	    
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	protected int donation;
	protected long creationTime;
	@Override
	
	// jnz - comparator reverses the natural ordering so that the highest Effective Priority is at the head of the queue
	//  but within a given effective priority, the lowest creation time is at the head of the queue.
	public int compareTo(ThreadState threadState) {
		if (this.getEffectivePriority() == threadState.getEffectivePriority()){
			return new Long(creationTime).compareTo(new Long(threadState.creationTime));
		}else{
			return -(new Integer(getEffectivePriority()).compareTo(threadState.getEffectivePriority()));
		}
		
	}
    }
}
