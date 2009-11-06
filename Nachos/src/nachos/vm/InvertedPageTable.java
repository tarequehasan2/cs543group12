package nachos.vm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nachos.machine.Machine;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class InvertedPageTable {
	
	private static final Lock lock;
	private static PageStatus[] pages;
	private static final Map<MemoryKey,Integer> position;
	// In case we need to get all memory addresses by pid - just set, not read yet
	private static final Map<Integer,Set<Integer>> positionByPid;
	private static TranslationEntry[] pageTable;
	private enum PageStatus{
		USED,UNUSED;
	}
	
	static {
		lock = new Lock();
		pages = new PageStatus[Machine.processor().getNumPhysPages()];
		for(int i=0; i< Machine.processor().getNumPhysPages(); i++){
			pages[i] = PageStatus.UNUSED;
		}
		position = new HashMap<MemoryKey, Integer>();
		positionByPid = new HashMap<Integer, Set<Integer>>();
		pageTable = new TranslationEntry[Machine.processor().getNumPhysPages()];
	}
	
	public static TranslationEntry read(MemoryKey key){
		lock.acquire();
			TranslationEntry translationEntry = null;
	        Integer location = position.get(key);
	        if(location != null){
	        	translationEntry = pageTable[location];
	        }
	        TranslationEntry result = translationEntry;
		lock.release();
		return result;
	}
	
	public static boolean write(TranslationEntry translationEntry, MemoryKey key){
		return write(translationEntry, key.getPid());
	}
	
	public static boolean write(TranslationEntry translationEntry, int pid){
		lock.acquire();
			int nextPosition = -1;
			for (int i=0; i<pages.length; i++){
				if (pages[i].equals(PageStatus.UNUSED)){
					nextPosition = i;
					break;
				}
			}
			
			//memory full, notify caller so that they can do some swapping.
			//-- we will probably need another method to choose someone to go to swap
			if (nextPosition == -1){
				return false;
			}
			
			MemoryKey key = new MemoryKey(pid, translationEntry.vpn);
			
			position.put(key, nextPosition);
			if (positionByPid.containsKey(pid)){
				positionByPid.get(pid).add(nextPosition);
			}else{
				Set<Integer> set = new HashSet<Integer>();
				set.add(nextPosition);
				positionByPid.put(pid, set);
			}
			pages[nextPosition] = PageStatus.USED;
			pageTable[nextPosition] = translationEntry;
		lock.release();	
		return true;
	}	
	
	public static void remove(MemoryKey key){
		lock.acquire();
			Integer location = position.get(key);
			if (location != null){
				pages[location] = PageStatus.UNUSED;
				pageTable[location] = null;
				position.remove(key);
				positionByPid.get(key.getPid()).remove(location);
			}
		lock.release();
	}
	
	public static void free(int pid){
		lock.acquire();
		Set<Integer> positions = positionByPid.get(pid);
    	for (Integer page : positions) {
    		//TODO: actual cleanup
    	}
		lock.release();
	}


}
