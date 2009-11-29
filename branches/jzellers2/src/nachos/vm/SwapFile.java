package nachos.vm;

import java.util.HashSet;
import java.util.Set;

import nachos.machine.FileSystem;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.Lock;

public class SwapFile {
    protected static IMachine machine = LiveMachine.getInstance();
	private static final String SWAP_FILE_NAME = "swap";
    /**
     * Contains the frame size in the swap file. This will always be
     * greater than or equal to {@link Processor#pageSize}.
     */
    private static final int SWAP_FRAME_SIZE = machine.getPageSize();
    private static final FileSystem fileSystem;
	private static final OpenFile swap;
	private static final Set<Integer> freePages;
	private static final Lock swapFileLock;

	static {
        fileSystem = nachos.machine.Machine.stubFileSystem();
        swap = fileSystem.open(SWAP_FILE_NAME, true);
        // don't add any, because the swap is initially zero sized
        // and thus no free pages; don't worry, we'll add some
		freePages = new HashSet<Integer>();
		swapFileLock = new Lock();
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
		swapFileLock.acquire();

        if (swap.length() < (spn * SWAP_FRAME_SIZE)) {
            error("Unable to find persisted page "+spn+"; file couldn't possibly contain it");
            swapFileLock.release();
            return false;
        }
        final int pos = spn * SWAP_FRAME_SIZE;
        final byte[] memory = machine.getMemory();
        final int memoryOffset = ppn * machine.getPageSize();
        /// WARNING: if you encoded metadata, ensure you bump the pos
        /// before calling this or you'll smash your metadata in the swap
        final int bytesRead =
                swap.read(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (bytesRead != SWAP_FRAME_SIZE) {
            error("Incorrect read size; expected "
                    +SWAP_FRAME_SIZE+" but read "+bytesRead);
            swapFileLock.release();
            return false;
        }

        swapFileLock.release();
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
        swapFileLock.acquire();
        if (freePages.isEmpty()) {
            // if the swap file is 0 length, then zero frame is available
            // if it's 4096 long, then 1*4096 will write to the end of the file
            int nextPage = swap.length() / SWAP_FRAME_SIZE;
            freePages.add(nextPage);
        }
        int nextSlot = freePages.iterator().next();
        freePages.remove(nextSlot);

        final int pos = nextSlot * SWAP_FRAME_SIZE;
        final byte[] memory = machine.getMemory();
        final int memoryOffset = ppn * machine.getPageSize();

        /// WARNING: if you encode metadata, ensure you bump the pos
        /// before calling this or you'll write metadata into main memory
        final int written = swap.write(pos, memory, memoryOffset, SWAP_FRAME_SIZE);
        if (written != SWAP_FRAME_SIZE) {
            error("Incorrect write size; expected "
                    +SWAP_FRAME_SIZE+" but wrote "+written);
		    swapFileLock.release();
            // return that slot to the free pool, since we didn't really use it
            free(new int[] { nextSlot });
			return -1;
		}
        swapFileLock.release();
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
        swapFileLock.acquire();
        for (int page : pages) {
            freePages.add(page);
        }
        swapFileLock.release();
    }
    /**
     * Closes and deletes the SwapFile.
     */
	public static void close(){
		swapFileLock.acquire();
        swap.close();
        boolean ok = fileSystem.remove(SWAP_FILE_NAME);
        if (!ok) {
            error("Unable to remove \"" + SWAP_FILE_NAME + "\"");
        }
        swapFileLock.release();
	}

    static void error(String msg) {
        System.err.println("ERROR:SwapFile:"+msg);
    }
}
