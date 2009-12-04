package nachos.network;

import java.util.HashMap;
import java.util.Map;

import nachos.threads.Lock;

public class SequenceUtil
{
    /**
     * Returns the current sequence number for the provided socket descriptor,
     * and then updates the sequence number by one.
     * I will return 1 if I have not given you a number before.
     * @param key the socket descriptor
     * @return the current sequence number for that descriptor, or 1 if its
     * new to me.
     */
    public static int nextSequence(SocketKey key) {
        _lock.acquire();
        if (! _seq.containsKey(key)) {
            _seq.put(key, 1);
        }
        int result = _seq.get(key);
        _seq.put(key, result + 1);
        _lock.release();
        return result;
    }

    public static int querySequence(SocketKey key) {
        _lock.acquire();
        int result = -1;
        if (_seq.containsKey(key)) {
            result = _seq.get(key);
        }
        _lock.release();
        return result;
    }

    public static boolean hasSentAMessage(SocketKey key) {
        _lock.acquire();
        boolean result = _sentSeq.containsKey(key);
        _lock.release();
        return result;
    }

    public static void sentSequence(SocketKey key, int seq) {
        _lock.acquire();
        _sentSeq.put(key, seq);
        _lock.release();
    }

    public static int getSentSequence(SocketKey key) {
        _lock.acquire();
        int result = -1;
        if (_sentSeq.containsKey(key)) {
            result = _sentSeq.get(key);
        }
        _lock.release();
        return result;
    }

    public static boolean hasReceivedAMessage(SocketKey key) {
        _lock.acquire();
        boolean result = _recvSeq.containsKey(key);
        _lock.release();
        return result;
    }

    public static void receivedSequence(SocketKey key, int seq) {
        _lock.acquire();
        _recvSeq.put(key, seq);
        _lock.release();
    }

    public static int getRecvSequence(SocketKey key) {
        _lock.acquire();
        int result = -1;
        if (_recvSeq.containsKey(key)) {
            result = _recvSeq.get(key);
        }
        _lock.release();
        return result;
    }

    public static void close(SocketKey key) {
        _lock.acquire();
        _seq.remove(key);
        _sentSeq.remove(key);
        _recvSeq.remove(key);
        _lock.release();
    }

    private static Map<SocketKey, Integer> _seq
            = new HashMap<SocketKey, Integer>();
    private static Map<SocketKey, Integer> _sentSeq
            = new HashMap<SocketKey, Integer>();
    private static Map<SocketKey, Integer> _recvSeq
            = new HashMap<SocketKey, Integer>();
    private static Lock _lock = new Lock();
}
