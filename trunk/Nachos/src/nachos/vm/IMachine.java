package nachos.vm;

import nachos.machine.TranslationEntry;

interface IMachine
{
    public byte[] getMemory();
    public int getPageSize();
    public int getTlbSize();
    public TranslationEntry readTlbEntry(int i);
    public void writeTlbEntry(int i, TranslationEntry entry);
}
