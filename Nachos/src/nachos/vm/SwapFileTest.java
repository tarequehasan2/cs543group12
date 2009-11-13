package nachos.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

public class SwapFileTest
{
    static Coff exe;
    static OpenFile coffFile;
    static VMKernel kernel;
    public static void selfTest() {
        kernel = (VMKernel) Kernel.kernel;
        if (! openExe("echo.coff")) {
            System.err.println("1:Unable to open echo.coff");
            return;
        }
        testOnePage();
        exe.close();
        coffFile.close();

        // have to re-open it :-(
        if (! openExe("echo.coff")) {
            System.err.println("2:Unable to open echo.coff");
            return;
        }
        testPages();
        exe.close();
        coffFile.close();
    }

    private static boolean openExe(String filename) {
        boolean createIt = false;
        coffFile = Machine.stubFileSystem().open(filename, createIt);
        try {
            exe = new Coff(coffFile);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
        return true;
    }

    public static void testOnePage() {
        final String METHOD_NAME = "SwapFileTest::testOnePage";

        final MockMachine machine = new MockMachine();
        SwapFile.machine = machine;

        int sectionCount = exe.getNumSections();
        if (0 == sectionCount) {
            System.err.println("What kind of bogus COFF are you giving me?");
            return;
        }

        // section 0 is usually .text
        CoffSection text = exe.getSection(0);
        int sectionLen = text.getLength();
        if (0 == sectionLen) {
            System.err.println("What? Section 0 is empty?!");
            return;
        }
        text.loadPage(0, 0);
        machine.importPage(0, 2);
        final int ppn1 = 2;
        final int ppn2 = ppn1+1; // doesn't matter, just != ppn1
        int spn = SwapFile.rollOut(ppn1);
        SwapFile.rollIn(spn, ppn2);
        int ppn1offset = ppn1 * machine.getPageSize();
        int ppn2offset = ppn2 * machine.getPageSize();
        byte[] memory = machine.getMemory();
        for (int i = 0; i < machine.getPageSize(); i++) {
            if (memory[ppn1offset + i] != memory[ppn2offset + i]) {
                throw new AssertionError("Bogus byte at "+i);
            }
        }
        System.out.println(METHOD_NAME +" OK");
        // have to really free this,
        // since SwapFile will be used in the running Machine
        SwapFile.free(new int[] { spn });
    }

    public static void testPages() {
        final String METHOD_NAME = "SwapFileTest::testPages";

        final MockMachine machine = new MockMachine();
        SwapFile.machine = machine;

        byte[] memory = machine.getMemory();
        // zero the memory to start with a known state
        Arrays.fill(memory, (byte)0);

        int sectionCount = exe.getNumSections();
        if (0 == sectionCount) {
            System.err.println("What kind of bogus COFF are you giving me?");
            return;
        }
        SwapAwareTranslationEntry[] pageTable
                = new SwapAwareTranslationEntry[ MockMachine.PhysicalPages ];
        for (int s = 0; s < sectionCount; s++) {
            CoffSection section = exe.getSection(s);
            int sectionLen = section.getLength();
            for (int p = 0; p < sectionLen; p++) {
                final int vpn = section.getFirstVPN() + p;
                pageTable[vpn] = new SwapAwareTranslationEntry(vpn, section.isReadOnly(), s, p);
            }
        }

        // expressly randomize the VPN order
        // so we don't have VPN=SPN the whole way up
        ArrayList<Integer> pageTableIndex = new ArrayList<Integer>();
        for (int i = 0; i < pageTable.length; i++) {
            if (null != pageTable[i]) {
                pageTableIndex.add(i);
            }
        }
        Collections.shuffle(pageTableIndex);

        Map<Integer, Integer> vpnToPpn = new HashMap<Integer, Integer>();
        // I am my own malloc :-)
        for (int ppn = 0; ! pageTableIndex.isEmpty(); ppn++) {
            final Integer vpn = pageTableIndex.get(0);
            pageTableIndex.remove(vpn);
            vpnToPpn.put(vpn, ppn);
            final SwapAwareTranslationEntry entry = pageTable[vpn];
            entry.restoredToMemory(ppn);
            // load them into page(0) of real memory,
            // so we only need nachos.conf to have page size of 1
            exe.getSection(entry.getCoffSection())
                    .loadPage(entry.getCoffPage(), 0);
            // now pretend like coff loaded it into ppn like we wanted
            machine.importPage(0, ppn);
        }

        CRC32 crc32 = new CRC32();
        crc32.update(memory);
        final long hash1 = crc32.getValue();

        Map<Integer, Integer> vpnToSpn = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer,Integer> entry : vpnToPpn.entrySet()) {
            int vpn = entry.getKey();
            int ppn = entry.getValue();
            int spn = SwapFile.rollOut(ppn);
            vpnToSpn.put(vpn, spn);
        }

        // zero the memory to re-start with a known state
        Arrays.fill(memory, (byte)0);

        for (Map.Entry<Integer,Integer> entry : vpnToSpn.entrySet()) {
            int vpn = entry.getKey();
            int spn = entry.getValue();
            int ppn = pageTable[vpn].getPpn();
            SwapFile.rollIn(spn, ppn);
            SwapFile.free(new int[] { spn });
        }
        crc32.reset();
        crc32.update(memory);
        final long hash2 = crc32.getValue();
        Lib.assertTrue(hash1 == hash2,
                    "Mismatched hash");
        System.out.println(METHOD_NAME +" OK");
    }
}
