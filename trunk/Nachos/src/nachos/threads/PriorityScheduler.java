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
	mutex.acquire();
	int priority = 0;
		       
	priority = getThreadState(thread).getPriority();
	mutex.release();
	return priority;
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	mutex.acquire();
	
	getThreadState(thread).setPriority(priority);
	mutex.release();
    }

    public boolean increasePriority() {
	mutex.acquire();
	boolean intStatus = Machine.interrupt().disable();
	boolean status = true;
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    status = false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	status = true;
	mutex.release();
	return status;
    }

    public boolean decreasePriority() {
		mutex.acquire();
	boolean intStatus = Machine.interrupt().disable();
	boolean status = true;
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    status = false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	status = true;
	mutex.release();
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
	private Lock mutex = new Lock();


    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
    	mutex.acquire();
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	ThreadState state = (ThreadState) thread.schedulingState;
    mutex.release();
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
		mutex.acquire();
	    getThreadState(thread).waitForAccess(this);
	    mutex.release();
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
		mutex.acquire();
	    getThreadState(thread).acquire(this);
	    mutex.release();
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
		mutex.acquire();
	    // assuming that we have everything in order, we should be able to poll the queue.
	    ThreadState threadState = (queue.peek() == null) ? null : queue.poll();
	    lockHolder = threadState;
	    KThread threadToReturn = (threadState == null) ? null : threadState.thread;
	    mutex.release();
	    return threadToReturn;
	}

	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
	protected ThreadState pickNextThread() {
	    mutex.acquire();
	    //  Assuming that we have everything in order, we should be able to peek at the queue
	    ThreadState thread = queue.peek();
	    mutex.release();
	    return thread;
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
	private Lock mutex = new Lock();

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
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
		mutex.acquire();
		int effectivePriority;
		//  Effective priority should be the larger of the effective priority or the donated priority.
		if (this.donation > this.priority){
			effectivePriority = this.donation;
		}else{
			effectivePriority = this.priority;
		}
	    mutex.release();
	    return effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
		mutex.acquire();
	    if (this.priority == priority) {
		    mutex.release();
		    return;
	    }
	    
	    
	    this.priority = priority;
	    
	   //implement me
	    
	    if (this.priority >= this.donation){
	    	this.donation = 0;
	    }
	    mutex.release();
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
		mutex.acquire();
		if (waitQueue.transferPriority 
				&& (waitQueue.lockHolder.priority < (this.priority)
						|| waitQueue.lockHolder.priority < (this.donation))){
			waitQueue.lockHolder.donation = (this.priority > this.donation) 
												? this.priority : this.donation;
		}
		waitQueue.queue.offer(this);
	    mutex.release();
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
		mutex.acquire();
		// jnz-  there should be nothing in the queue, so the first thread through is the lockHolder.
	    waitQueue.lockHolder = this;
	    waitQueue.queue.offer(this);
	    mutex.release();
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	protected int donation;
	protected long creationTime;
	private Lock mutex = new Lock();
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
