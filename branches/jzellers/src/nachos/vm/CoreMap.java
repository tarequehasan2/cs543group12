package nachos.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nachos.machine.Machine;
import nachos.threads.Lock;

public class CoreMap
{
    public static void addToCoreMap(int ppn, int pid, int vpn) {
        _lock.acquire();
        List<CoreMapEntry> pages;
        if (! TABLE.containsKey(ppn)) {
            pages = new ArrayList<CoreMapEntry>();
            TABLE.put(ppn, pages);
        } else {
            pages = TABLE.get(ppn);
        }
        pages.add(new CoreMapEntry(pid, vpn));
        _lock.release();
    }

    public static boolean containsPpn(int ppn) {
        boolean result;
        _lock.acquire();
        result = TABLE.containsKey(ppn);
        _lock.release();
        return result;
    }

    public static Iterable<CoreMapEntry> findEntriesForPpn(int ppn) {
        _lock.acquire();
        if (! TABLE.containsKey(ppn)) {
            _lock.release();
            return EMPTY_ITER;
        }
        Iterable<CoreMapEntry> results = TABLE.get(ppn);
        _lock.release();
        return results;
    }

    /**
     * <B>ENSURE</b> you update the core map with your (PID,VPN) tuple or
     * it'll double-allocate PPN.
     * @return the free ppn or -1 if no free memory.
     */
    public static int malloc() {
        _lock.acquire();
        int result = -1;
        final int numPhysPages = Machine.processor().getNumPhysPages();
        for (int i = 0; i < numPhysPages; i++) {
        	if (! TABLE.containsKey(i)) {
        		result = i;
                break;
        	}
        }
        _lock.release();
        return result;
    }

    public static void free(int ppn) {
        _lock.acquire();
        TABLE.remove(ppn);
        _lock.release();
    }

    /**
     * Indexes the PPN and the list of (pid,vpn) pairs stored there.
     * Since we did not implement shared loading, the size of the list will
     * always be 1.
     */
    private static Map<Integer, List<CoreMapEntry>>
        TABLE = new HashMap<Integer, List<CoreMapEntry>>();
    private static Iterable<CoreMapEntry> EMPTY_ITER = new EmptyIterator();
    private static Lock _lock = new Lock();
    public static class CoreMapEntry
    {
        public CoreMapEntry(int pid, int vpn) {
            this.pid = pid;
            this.vpn = vpn;
        }
        public int getPid() { return pid; }
        public int getVpn() { return vpn; }
        @Override
        public String toString() {
            return "CoreMapEntry[pid="+pid+" vpn="+vpn+"]";
        }
        private int pid;
        private int vpn;
    }
    private static class EmptyIterator
            implements Iterable<CoreMapEntry>, Iterator<CoreMapEntry>
    {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public CoreMapEntry next() {
            return null;
        }

        @Override
        public void remove() {
        }

        @Override
        public Iterator<CoreMapEntry> iterator() {
            return this;
        }
    }
}
