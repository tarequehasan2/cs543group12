package nachos.network;

import nachos.machine.OpenFile;

public class SocketOpenFile extends OpenFile
{
    public SocketOpenFile(MailMessage msg) {
        super();
        _msg = msg;
    }

    @Override
    public void close() {
        closeErrno = ((NetKernel)NetKernel.kernel)
                .close(_msg.packet.srcLink, _msg.srcPort, _msg.dstPort);
    }

    @Override
    public int read(byte[] buf, int offset, int length) {
        return -1;
    }

    @Override
    public int write(byte[] buf, int offset, int length) {
        return ((NetKernel)NetKernel.kernel)
                .write(_msg.packet.srcLink, _msg.srcPort, _msg.dstPort,
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

    private MailMessage _msg;
    int closeErrno;
}
