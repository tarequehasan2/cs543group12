package nachos.vm;

import nachos.machine.TranslationEntry;

public class ProcessorUtil
{
    /**
     * Searches through the Processor's TLB mapping to find the
     * valid entry who's vpn is the number provided.
     * @param vpn the virtual page number I should locate.
     * @return the valid TLB entry which is pointing to the provided VPN,
     * or null if unable to find any such entry.
     */
    public static TranslationEntry findProcTLBforVpn(int vpn) {
        final int tlbSize = InvertedPageTable.machine.getTlbSize();
        TranslationEntry result = null;
        for (int i = 0; i < tlbSize; i++) {
            final TranslationEntry entry = machine.readTlbEntry(i);
            if (entry.valid && entry.vpn == vpn) {
                result = entry;
                break;
            }
        }
        return result;
    }

    /**
     * Searches through the Processor's TLB mapping to find the
     * valid entry who's ppn is the number provided.
     * @param ppn the physical page number I should locate.
     * @return the valid TLB entry which is pointing to the provided PPN,
     * or null if unable to find any such entry.
     */
    public static TranslationEntry findProcTLBforPpn(int ppn) {
        final int tlbSize = machine.getTlbSize();
        TranslationEntry result = null;
        for (int i = 0; i < tlbSize; i++) {
            final TranslationEntry entry = machine.readTlbEntry(i);
            if (entry.valid && entry.ppn == ppn) {
                result = entry;
                break;
            }
        }
        return result;
    }

    /**
     * Initializes the provided physical page to all zero. You should be aware
     * that absolutely no checking is done on the provided value, so caveat
     * Javator. I do use the {@link VMKernel#lockMemory()} to avoid multiple
     * writes at once.
     * @param ppn the physical page to initialize.
     */
    public static void initializePage(int ppn) {
        VMKernel.lockMemory();
        final int pageSize = InvertedPageTable.machine.getPageSize();
		int offset = ppn * pageSize;
        byte[] memory = InvertedPageTable.machine.getMemory();
        for (int i = 0; i < pageSize; i++) {
            int addr = offset + i;
            memory[addr] = 0;
        }
        VMKernel.unlockMemory();
    }
    protected static IMachine machine = LiveMachine.getInstance();
}
