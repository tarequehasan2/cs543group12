package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat
{

    static BoatGrader bg;
    private static Island oahu;
    private static Island molokai;
    
    private static final int STAY = 0;
    private static final int RETURN =1;
    private static KThread driver;
    private static KThread passenger;
    private static Lock boardingLock = new Lock();
    private static Lock boardedConditionLock = new Lock();
    private static Condition2 boardedCondition = new Condition2(boardedConditionLock);
    private static boolean listening = false; 
    private static Communicator molokaiCommunicator = new Communicator();
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();
	
//	System.out.println("\n ***Testing Boats with only 2 children***");
//	begin(0,2 , b);    // jnz - works

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
 // 	begin(1, 2, b);  // jnz - works

//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
 // 	begin(3, 3, b);  //jnz - works
    }

    public static void begin( int adults, int children, BoatGrader b )
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	bg = b;

	// Instantiate global variables here
	oahu = new Island();
	molokai = new Island();
	oahu.setBoatPresent(true);
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.

	for (int i =0; i < children; i ++){
		
		new KThread(new Runnable() {
			   public void run() {
				   oahu.childArrives();
				   KThread.yield();
				   ChildItinerary();
		            }
		        }).setName("Child Thread #" + i).fork();
	}

	for (int i =0; i < adults; i ++){
		new KThread(new Runnable() {
			public void run() {
				oahu.adultArrives();
				KThread.yield();
				AdultItinerary();
	            	}
	        	}).setName("Adult Thread #" + i).fork();

    }


    }

    static void AdultItinerary()
    {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
    	boolean done=false;
    	while (!done){
    	//System.out.println("Number of adults: "+oahu.getNumberOfAdults());
    		if (!oahu.isBoatPresent() || oahu.getNumberOfChildren() >= 2){
    			oahu.adultConditionLock.acquire();    //can't do anything if there isn't a boat
    			oahu.adultWaitCondition.sleep();      // two or more kids on the island, adults cannot go
    			oahu.adultConditionLock.release();
    		}else if(driver != null && oahu.getNumberOfChildren() ==1){
    			oahu.childConditionLock.acquire();
    			oahu.childWaitCondition.wake();
    			oahu.childConditionLock.release();
    			
    			oahu.adultConditionLock.acquire();   
    			oahu.adultWaitCondition.sleep();     
    			oahu.adultConditionLock.release();
    			
    		}else if (getOnTheBoat(KThread.currentThread())){
    			Lib.assertTrue(driver == KThread.currentThread() && passenger == null);
    			
    			oahu.adultLeaves();
    			boolean returnToOahu = (oahu.getNumberOfAdults() + oahu.getNumberOfChildren()) > 0;
    			bg.AdultRowToMolokai();
    			driver = null;
    			if (returnToOahu){
    				molokaiCommunicator.speak(RETURN);
    			}else{
    				molokaiCommunicator.speak(STAY);
    			}
    			done = true;
    		}else{
    			oahu.adultConditionLock.acquire();   
    			oahu.adultWaitCondition.sleep();     
    			oahu.adultConditionLock.release();

    		}
    	}
    	
    	
    }

    static void ChildItinerary()
    {
    	boolean done = false;
    	while (!done){
    	if (!oahu.isBoatPresent()){
    		oahu.childConditionLock.acquire();
    		oahu.childWaitCondition.sleep();  //can't do anything if there isn't a boat
    		oahu.childConditionLock.release();
    	}
    	if (passenger != KThread.currentThread() && driver != KThread.currentThread() && !getOnTheBoat(KThread.currentThread())){
    		oahu.childConditionLock.acquire();
    		oahu.childWaitCondition.sleep();
    		oahu.childConditionLock.release();
    	}else if (driver == KThread.currentThread()){
    		oahu.childLeaves();
    		boardedConditionLock.acquire();
    		if (passenger == null){
    			boardedCondition.sleep();
    		}
    		boardedCondition.wake();
    		boardedConditionLock.release();
    		boolean returnToOahu = (oahu.getNumberOfAdults() + oahu.getNumberOfChildren()) > 0;
    		int numberOfChildren = oahu.getNumberOfChildren();
    		oahu.setBoatPresent(false);
    		bg.ChildRowToMolokai();
    		molokai.childArrives();
    		if (returnToOahu){
        		boardedConditionLock.acquire();
        		boardedCondition.sleep();
        		boardedConditionLock.release();
        		molokai.childLeaves();
    			bg.ChildRowToOahu();
    			oahu.setBoatPresent(true);
    			oahu.childArrives();
    			if (oahu.getNumberOfChildren() >= 2){
    				oahu.childConditionLock.acquire();
    				oahu.childWaitCondition.wake();
    				oahu.childConditionLock.release();
    				boardedConditionLock.acquire();
    				boardedCondition.sleep();
    				boardedConditionLock.release();
    			}else if(oahu.getNumberOfAdults() > 0){
    				oahu.adultConditionLock.acquire();
    				oahu.adultWaitCondition.wake();
    				oahu.adultConditionLock.release();
    				driver = null;
    				oahu.childConditionLock.acquire();
    				oahu.childWaitCondition.sleep();
    				oahu.childConditionLock.release();
    				
    			}
    		}else{
    			if (molokai.getNumberOfChildren() == 2){
    				molokaiCommunicator.speak(STAY);
    			}
    			molokai.childLeaves();  // Thread exits, so not keeping track anymore.
    			done=true;
    		}
    	}else if (passenger == KThread.currentThread()){
    		oahu.childLeaves();
    		boardedConditionLock.acquire();
    		if (driver == null){
    			boardedCondition.sleep();
    		}
    		boardedCondition.wake();
    		boardedCondition.sleep();
    		boardedConditionLock.release();
    		boolean waitOnMolokai = (oahu.getNumberOfAdults()) > 0;
    		bg.ChildRideToMolokai();
    		molokai.childArrives();
    		passenger = null;
    		boardedConditionLock.acquire();
    		boardedCondition.wake();
    		boardedConditionLock.release();
    		//waitOnMolokai = waitOnMolokai && (molokai.numberOfChildren <= 2);
    		if (waitOnMolokai && !listening){
    			molokai.childArrives();
    			listening = true;
    			int response = molokaiCommunicator.listen();
    			listening = false;
    			if (response == STAY){
    				molokai.childLeaves();
    				done = true;
    			}else if (response == RETURN){
    				getOnTheBoat(KThread.currentThread());
    				Lib.assertTrue(driver == KThread.currentThread() && passenger == null);
    				molokai.childLeaves();
    				bg.ChildRowToOahu();
    				oahu.childArrives();
    				oahu.childConditionLock.acquire();
    				oahu.childWaitCondition.wake();
    				oahu.childConditionLock.release();
    				
    				
    			}
    		}else{
    			molokai.childLeaves();
    			done = true;
    		}

    		
    	}
    	}
    	

    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
    private static class Island {
    	
    	private int numberOfChildren = 0;
    	private int numberOfAdults = 0;
    	private boolean boatPresent = false;
    	private Lock adultConditionLock = new Lock();
    	private Condition2 adultWaitCondition = new Condition2(adultConditionLock);
    	private Lock childConditionLock = new Lock();
    	private Condition2 childWaitCondition = new Condition2(childConditionLock);
    	private Lock lock = new Lock();
    	public int getNumberOfChildren(){
    		lock.acquire();
    			int number = numberOfChildren;
    		lock.release();
    		return number;
    		
    	}

    	public int getNumberOfAdults(){
    		lock.acquire();
    			int number = numberOfAdults;
    		lock.release();
    		return number;
    	}
    	
    	public void childArrives(){
    		lock.acquire();
    			numberOfChildren++;
    		lock.release();
    	}
 
    	public void childLeaves(){
    		lock.acquire();
    			numberOfChildren--;
    		lock.release();
    	}
    	
    	public void adultArrives(){
    		lock.acquire();
    			numberOfAdults++;
    		lock.release();
    	}
    	
    	public void adultLeaves(){
    		lock.acquire();
    			numberOfAdults--;
    		lock.release();
    	}
    	
    	public boolean isBoatPresent(){
    		lock.acquire();
    			boolean result = boatPresent;
			lock.release();
			return result;
    	}
    	
    	public void setBoatPresent(boolean boatPresent){
    		lock.acquire();
    			this.boatPresent = boatPresent;
			lock.release();
    	}

    	

    }
    
    public static boolean getOnTheBoat(KThread thread){
    	boolean boarded = false;
    	boardingLock.acquire();
    		if (driver == null){
    			driver = thread;
    			boarded = true;
    		}else if (passenger == null){
    			passenger = thread;
    			boarded = true;
    		}
    	boardingLock.release();
    	return boarded;
    }
    
}
