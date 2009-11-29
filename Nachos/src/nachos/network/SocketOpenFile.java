package nachos.network;

import nachos.machine.OpenFile;

public class SocketOpenFile extends OpenFile
{
    public SocketOpenFile(NachosMessage msg) {
        super();
        _msg = msg;
    }

    @Override
    public void close() {
        closeErrno = ((NetKernel)NetKernel.kernel)
                .close(_msg.getSourceHost(), _msg.getSourcePort(), _msg.getDestPort());
    }

    @Override
    public int read(byte[] buf, int offset, int length) {
        return -1;
    }

    @Override
    public int write(byte[] buf, int offset, int length) {
        return ((NetKernel)NetKernel.kernel)
                .write(_msg.getSourceHost(), _msg.getSourcePort(), _msg.getDestPort(),
                        buf, offset, length);
    }

    @Override
    public int read(int pos, byte[] buf, int offset, int length) {
        return -1;
    }

    @Override
    public int write(int pos, byte[] buf, int offset, int length) {
        return -1;
    }

    @Override
    public int length() {
        return -1;
    }

    @Override
    public int tell() {
        return -1;
    }

    @Override
    public void seek(int pos) {
    }

    private NachosMessage _msg;
    int closeErrno;
}
