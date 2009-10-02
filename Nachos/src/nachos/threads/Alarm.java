package nachos.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		mutex.acquire();
		List<Long> keys = new ArrayList<Long>(wakeHash.keySet());
		if (keys.size() == 0) {
			mutex.release();
			return;
		}
		Collections.sort(keys);
		long wakeTime = keys.get(0);
		while (wakeTime <= Machine.timer().getTime())
		{
			List<KThread> threads = wakeHash.get(wakeTime);
			Iterator<KThread> it = threads.iterator();
			while (it.hasNext())
			{
				((KThread)it.next()).ready();
			}
		}
		mutex.release();
		KThread.yield();
    }

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		
    	boolean intStatus = Machine.interrupt().disable();
    	mutex.acquire();
    	long wakeTime = Machine.timer().getTime() + x;
    	if (wakeHash.containsKey(wakeTime))
    	{
    		wakeHash.get(wakeTime).add(KThread.currentThread());
    	}
    	else
    	{
    		List<KThread> threads = new ArrayList<KThread>();
    		threads.add(KThread.currentThread());
    		wakeHash.put(wakeTime, threads);
    	}
    	mutex.release();
    	KThread.sleep();

    	Machine.interrupt().restore(intStatus);
    }

    private static class AlarmTest implements Runnable {
    	AlarmTest(long wakeTime) {
    	    this.wakeTime = wakeTime;
    	}
    	
    	public void run() {
    		System.out.println("*** thread set to sleep for " + wakeTime + 
    				" starting at " + Machine.timer().getTime());
    		Alarm testAlarm = new Alarm();
    		testAlarm.waitUntil(wakeTime);
    		System.out.println("*** thread woken up at " + Machine.timer().getTime());
    	}

    	private long wakeTime;
        }

    
    /**
     * Tests whether this module is working.
     */
    public static void selfTest() {
	Lib.debug(dbgThread, "Enter Alarm.selfTest");
	
//	new KThread(new PingTest(1)).setName("forked thread").fork();
	new KThread(new AlarmTest(1000)).fork();
	new KThread(new AlarmTest(2000)).fork();
	new KThread(new AlarmTest(5000)).fork();
	new KThread(new AlarmTest(10000)).fork();
	new KThread(new AlarmTest(500)).fork();
	new KThread(new AlarmTest(7000)).fork();
	
//	new PingTest(0).run();
    }

    private static final char dbgThread = 't';
	private Map<Long, List<KThread>> wakeHash = new HashMap<Long, List<KThread>>();
	private Lock mutex = new Lock();
}
