package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.Lock;

public class SwapFile {
	
	private static final String SWAP_FILE_NAME = ".swap";
	private static final OpenFile swap;
	private static final Map<MemoryKey,Integer> position;
	private static final Lock lock;
	private static List<PageStatus> pages;
	private enum PageStatus{
		USED,UNUSED;
	}
	
	static {
		swap = Machine.stubFileSystem().open(SWAP_FILE_NAME, true);
		position = new HashMap<MemoryKey, Integer>();
		lock = new Lock();
		pages = new LinkedList<PageStatus>();
	}
	
	public static int read(MemoryKey key, int ppn){
		lock.acquire();
		int tmpRead = 0;
        Integer start = position.get(key);
        if(start != null){
        	// jnz - was going to retun a byte array, but decided to read directly in to physical memory.  
        	//  'cuz its easier.   Is that too dangerous?
        	tmpRead = swap.read(start * Processor.pageSize, Machine.processor().getMemory(), ppn * Processor.pageSize, Processor.pageSize);
        	if(tmpRead != Processor.pageSize){
        		lock.release();
        		return -1;
        	}
        }
        int read = tmpRead;
        position.remove(key);
        pages.set(start, PageStatus.UNUSED);
		lock.release();
		return read;
	}
	
	public static int write(MemoryKey key, int ppn){
		lock.acquire();
			int nextPosition = -1;
			for (int i=0; i<pages.size(); i++){
				if (pages.get(i).equals(PageStatus.UNUSED)){
					nextPosition = i;
					break;
				}
			}
			if (nextPosition == -1){
				nextPosition = pages.size();
				pages.add(PageStatus.UNUSED);
			}
			int written = swap.write(nextPosition * Processor.pageSize, Machine.processor().getMemory(), ppn * Processor.pageSize, Processor.pageSize);
			if (written != Processor.pageSize){
				lock.release();
				return -1;
			}
			pages.set(nextPosition, PageStatus.USED);
			position.put(key, Integer.valueOf(nextPosition));
			int result = written;
		lock.release();	
		return result;
	}
	
	public static void close(){
		lock.acquire();
			swap.close();
			Machine.stubFileSystem().remove(SWAP_FILE_NAME);
		lock.release();
	}
	

}
