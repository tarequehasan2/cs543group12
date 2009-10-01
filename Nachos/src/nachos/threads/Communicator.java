package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	lock.acquire();
    	while (spoken){
    		speakers++;
    		conditionSpeak.sleep();  // if a word was already spoken, wait here until ready to be heard.
    		speakers--;
    	}
    	this.word = word;      
    	spoken=true;              // spoken token  
    	conditionListen.wake();  // Wake a listener, if there is one.
    	conditionWait.sleep();   // Wait for a listener to wake the thread.
    	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */   
    public int listen() {
    	lock.acquire();
    	
    	while (!spoken){
    		conditionListen.sleep();  // make listeners wait until spoken to.
    	}
    	if (speakers == 0){
    		spoken = false;   // reset the spoken token if no more speakers exist.
    	}
    	int currentWord = word;    // transfer the word, another thread can modify "word"
    	conditionWait.wakeAll();  // should only be one, but wakeall to be safe
    	lock.release();   
    	return currentWord;
    }
    
    private int word;
    private Lock lock = new Lock();
    private Condition2 conditionSpeak = new Condition2(lock);
    private Condition2 conditionListen = new Condition2(lock);
    private Condition2 conditionWait = new Condition2(lock);
    
    private int speakers = 0;
    private boolean spoken = false;
}
