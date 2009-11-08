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
    /** Contains the file offset for each page, mapped by swap page number. */
	private static final Map<Integer, Integer> positions;
	private static final Lock lock;
    /** Contains a list of integers which,
     * when multiplied by {@link Processor#pageSize},
     * will yield the available offsets in the pagefile.
     */
	private static final LinkedList<Integer> freePages;

	static {
        fileSystem = Machine.stubFileSystem();
        swap = fileSystem.open(SWAP_FILE_NAME, true);
		positions = new HashMap<Integer, Integer>();
		lock = new Lock();
		freePages = new LinkedList<Integer>();
        // don't add any, because the swap is initially zero sized
        // and thus no free pages; don't worry, we'll add some
	}

    /**
     * Reads the specified physical frame
     * (used by the specified process) back into main memory.
     * <b>WARNING</b>: it is expected you have protected the page in memory
     * into which this function will write.
     * I do not lock memory, but I do lock myself.
     * @param spn the swap page frame to roll-into memory.
     * @param ppn the physical frame into which it should roll-in.
     * @return true if ok, false otherwise.
     */
	public static boolean rollIn(int spn, int ppn) {
		lock.acquire();

        if (! positions.containsKey(spn)) {
            error("Unable to find persisted page "+spn);
            lock.release();
            return false;
        }
        final int pos = positions.get(spn);
        final byte[] memory = Machine.processor().getMemory();
        final int memoryOffset = ppn * Processor.pageSize;
        /// WARNING: if you encoded metadata, ensure you bump the pos
        /// before calling this or you'll smash your metadata in the swap
        final int bytesRead =
                swap.read(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (bytesRead != SWAP_FRAME_SIZE) {
            error("Incorrect read size; expected "
                    +SWAP_FRAME_SIZE+" but read "+bytesRead);
            lock.release();
            return false;
        }

        // now free up that slot for someone else, since the process shouldn't
        // be asking for the same frame twice
        freePages.add(spn);

		lock.release();
		return true;
	}

    /**
     * Writes the specified in-memory physical frame to persistent storage.
     * <b>WARNING</b>: it is expected you have protected the page in memory
     * from which this function will read.
     * I do not lock memory, but I do lock myself.
     * @param ppn the physical frame to roll-out.
     * @return the swap page number, or -1 on error.
     */
	public static int rollOut(int ppn) {
		lock.acquire();
        if (freePages.isEmpty()) {
            // it's like printing money, eh?
            int inUse = swap.length() / SWAP_FRAME_SIZE;
            freePages.add(inUse + 1);
        }
        int nextSlot = freePages.removeFirst();
        final int pos = nextSlot * SWAP_FRAME_SIZE;
        final byte[] memory = Machine.processor().getMemory();
        final int memoryOffset = ppn * Processor.pageSize;
        /// WARNING: if you encode metadata, ensure you bump the pos
        /// before calling this or you'll write metadata into main memory
        final int written = swap.write(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (written != SWAP_FRAME_SIZE) {
            error("Incorrect write size; expected "
                    +SWAP_FRAME_SIZE+" but wrote "+written);
		    lock.release();
			return -1;
		}
        positions.put(nextSlot, pos);
        lock.release();
		return nextSlot;
	}

    /**
     * Allows one to release swap pages without rolling them back into memory.
     * @param pages the list of swap pages to free.
     */
    public static void free(int[] pages) {
        if (null == pages || 0 == pages.length) {
            return;
        }
        lock.acquire();
        for (int page : pages) {
            freePages.add(page);
        }
        lock.release();
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

    static void error(String msg) {
        System.err.println("ERROR:SwapFile:"+msg);
    }
}
