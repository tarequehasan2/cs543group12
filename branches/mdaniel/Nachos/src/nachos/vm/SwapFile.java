package nachos.vm;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import nachos.machine.FileSystem;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.Lock;

public class SwapFile {
	private static final String SWAP_FILE_NAME = ".swap";
    /**
     * Contains the frame size in the swap file. This will always be 
     * greater than or equal to {@link Processor#pageSize}.
     */
    private static final int SWAP_FRAME_SIZE = Processor.pageSize;
    private static final FileSystem fileSystem;
	private static final OpenFile swap;
    /** Contains the file offset for each page, mapped by per-process vpn. */
	private static final Map<Integer, Map<Integer, Integer>> positions;
	private static final Lock lock;
    /** Contains a list of integers which,
     * when multiplied by {@link Processor#pageSize},
     * will yield the available offsets in the pagefile.
     */
	private static final LinkedList<Integer> freePages;

	static {
        fileSystem = Machine.stubFileSystem();
        swap = fileSystem.open(SWAP_FILE_NAME, true);
		positions = new HashMap<Integer, Map<Integer, Integer>>();
		lock = new Lock();
		freePages = new LinkedList<Integer>();
	}

	public boolean contains(int pid, int ppn) {
        return positions.containsKey(pid) &&
                positions.get(pid).containsKey(ppn);
    }

    /**
     * Reads the specified physical frame
     * (used by the specified process) back into main memory.
     * @param pid the process for whom we are swapping the frame.
     * @param ppn the physical frame to roll-in.
     * @return true if ok, false otherwise.
     */
	public static boolean rollIn(int pid, int ppn) {
		lock.acquire();

        Map<Integer, Integer> pageMap = positions.get(pid);
        if (null == pageMap) {
            error("Unable to find persisted page "+ppn+" for pid "+pid);
            lock.release();
            return false;
        }
        final int slotNumber = pageMap.get(ppn);
        final int pos = slotNumber * SWAP_FRAME_SIZE;
        final byte[] memory = Machine.processor().getMemory();
        final int memoryOffset = ppn * SWAP_FRAME_SIZE;
        /// WARNING: if you encoded metadata, ensure you bump the pos
        /// before calling this or you'll smash your metadata in the swap
        final int bytesRead =
                swap.read(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (bytesRead != SWAP_FRAME_SIZE) {
            error("Incorrect read size; expected "
                    +SWAP_FRAME_SIZE+" but wrote "+bytesRead);
            lock.release();
            return false;
        }

        // now free up that slot for someone else, since the process shouldn't
        // be asking for the same frame twice
        freePages.add(slotNumber);

		lock.release();
		return true;
	}

    /**
     * Writes the specified in-memory physical frame
     * (used by the specified process) to persistent storage.
     * @param pid the process for whom we are swapping the frame.
     * @param ppn the physical frame to roll-out.
     * @return true if ok, false otherwise.
     */
	public static boolean rollOut(int pid, int ppn) {
		lock.acquire();
        if (freePages.isEmpty()) {
            // it's like printing money, eh?
            int inUse = swap.length() / SWAP_FRAME_SIZE;
            freePages.add(inUse + 1);
        }
        int nextPosition = freePages.removeFirst();
        final int pos = nextPosition * SWAP_FRAME_SIZE;
        final byte[] memory = Machine.processor().getMemory();
        final int memoryOffset = ppn * SWAP_FRAME_SIZE;
        /// WARNING: if you encode metadata, ensure you bump the pos
        /// before calling this or you'll write metadata into main memory
        final int written = swap.write(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (written != SWAP_FRAME_SIZE) {
            error("Incorrect write size; expected "
                    +SWAP_FRAME_SIZE+" but wrote "+written);
		    lock.release();
			return false;
		}
        
        Map<Integer, Integer> pageMap = positions.get(pid);
        if (null == pageMap) {
            pageMap = new HashMap<Integer, Integer>();
            positions.put(pid, pageMap);
        }
        pageMap.put(ppn, nextPosition);
        
        lock.release();
		return true;
	}

    /**
     * Closes and deletes the SwapFile.
     */
	public static void close(){
		lock.acquire();
        swap.close();
        boolean ok = fileSystem.remove(SWAP_FILE_NAME);
        if (!ok) {
            error("Unable to remove \"" + SWAP_FILE_NAME + "\"");
        }
        lock.release();
	}

    /**
     * Frees the pages which were persisted for the process identified
     * by the given pid.
     * @param pid the process whose pages should be freed from the swap.
     */
	public static void free(int pid){
		lock.acquire();
        final Map<Integer, Integer> pageMap = positions.get(pid);
        if (null == pageMap || pageMap.isEmpty()){
			lock.release();
			return;
		}
        for (Integer offset : pageMap.values()) {
            freePages.add(offset);
        }
		lock.release();
	}

    static void error(String msg) {
        System.err.println("ERROR:SwapFile:"+msg);
    }
}
