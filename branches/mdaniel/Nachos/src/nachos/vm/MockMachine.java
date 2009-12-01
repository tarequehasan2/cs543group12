package nachos.vm;

import nachos.machine.Machine;
import nachos.machine.NetworkLink;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;

public class MockMachine implements IMachine
{
    protected static final int PhysicalPages = 32;
    protected static final int PageSize = Processor.pageSize;
    protected static final int TlbSize = 4;
    public MockMachine() {
        memory = new byte[PhysicalPages * PageSize];
        tlb = new TranslationEntry[ TlbSize ];
    }
    @Override
    public byte[] getMemory() {
        return memory;
    }

    @Override
    public int getPageSize() {
        return PageSize;
    }

    @Override
    public int getNumPhysPages() {
        return PhysicalPages;
    }

    @Override
    public int getTlbSize() {
        return TlbSize;
    }

    @Override
    public TranslationEntry readTlbEntry(int i) {
        return tlb[i];
    }

    @Override
    public void writeTlbEntry(int i, TranslationEntry entry) {
        tlb[i] = new TranslationEntry(entry);
    }

	@Override
	public NetworkLink getNetworkLink() {
		return null;
	}

    /**
     * Copies one page from the actual Machine into this IMachine's memory
     * at the target page.
     * @param ppn the source page number.
     * @param intoPpn the target page number.
     */
    public void importPage(int ppn, int intoPpn) {
        byte[] realMemory = Machine.processor().getMemory();
        System.arraycopy(
                realMemory, ppn * PageSize,
                memory, intoPpn * PageSize,
                PageSize);
    }

    private byte[] memory;
    private TranslationEntry[] tlb;
}
