package nachos.vm;

import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;

class LiveMachine implements IMachine
{
    public static LiveMachine getInstance() {
        return self;
    }
    /** Use {@link LiveMachine#getInstance()}. */
    private LiveMachine() {

    }
    @Override
    public byte[] getMemory() {
        return Machine.processor().getMemory();
    }

    @Override
    public int getPageSize() {
        return Processor.pageSize;
    }

    @Override
    public int getNumPhysPages() {
        return Machine.processor().getNumPhysPages();
    }

    @Override
    public int getTlbSize() {
        return Machine.processor().getTLBSize();
    }

    @Override
    public TranslationEntry readTlbEntry(int i) {
        return Machine.processor().readTLBEntry(i);
    }

    @Override
    public void writeTlbEntry(int i, TranslationEntry entry) {
        Machine.processor().writeTLBEntry(i, entry);
    }

    private static final LiveMachine self = new LiveMachine();
}
