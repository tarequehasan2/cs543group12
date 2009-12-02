package nachos.network;

import nachos.machine.Machine;
import nachos.machine.OpenFile;

public class SocketOpenFile extends OpenFile
{
    public SocketOpenFile(SocketKey key) {
        super();
        _key = key;
        _kernel = (NetKernel) NetKernel.kernel;
    }

    /**
     * Switch this Socket into a state of STP_SENT which has a SEQ = MAX(SEQ)+1.
     * The Socket waits for a FIN, and then will FIN/ACK
     * and go into a state of CLOSED.
     */
    @Override
    public void close() {
        _kernel.scheduleClose(this);
    }

    /**
     * Grab the next DATA packet from the buffer and fill the provided buffer
     * at the provided offset, not exceeding the length.
     * @param    buf    the buffer to store the bytes in.
     * @param    offset    the offset in the buffer to start storing bytes.
     * @param    length    the number of bytes to read.
     * @return the number of bytes loaded into the buffer
     */
    @Override
    public int read(byte[] buf, int offset, int length) {
        return _kernel.read(_key, buf, offset, length);
    }

    /**
     * Grabs all the bytes from the provided buffer, starting from the provided
     * offset, not exceeding the length. This will create a new DATA packet
     * for each chunk of data
     * up to {@link NachosMessage#MAX_CONTENTS_LENGTH} length.
     * @param    buf    the buffer to get the bytes from.
     * @param    offset    the offset in the buffer to start getting.
     * @param    length    the number of bytes to write.
     * @return the number of bytes written from the provided array
     */
    @Override
    public int write(byte[] buf, int offset, int length) {
    		return _kernel.write(_key, buf, offset, length);
    }

    /** Always returns -1, because I do not support seeked reads. */
    @Override
    public int read(int pos, byte[] buf, int offset, int length) {
        throw new UnsupportedOperationException("no seeked reads");
    }

    /** Always returns -1, because I do not support seeked writes. */
    @Override
    public int write(int pos, byte[] buf, int offset, int length) {
        throw new UnsupportedOperationException("no seeked writes");
    }

    /** Always returns -1, because I do not support length calls. */
    @Override
    public int length() {
        throw new UnsupportedOperationException("no length()");
    }

    /** Always returns -1, because I do not support telling position. */
    @Override
    public int tell() {
        throw new UnsupportedOperationException("no tell()");
    }

    @Override
    public void seek(int pos) {
        throw new UnsupportedOperationException("no seek("+pos+")");
    }

    SocketKey getKey() {
        return _key;
    }

    /**
     * Contains the remote host and port, along with the local address pair
     * for this Socket.
     */
    private SocketKey _key;
    /** Convenience variable for accessing the O.S.&nbsp;kernel. */
    NetKernel _kernel;
}
