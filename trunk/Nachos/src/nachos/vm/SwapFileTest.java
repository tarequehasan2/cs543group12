package nachos.vm;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Kernel;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;

public class SwapFileTest
{
    static Coff exe;
    static VMKernel kernel;
    public static void selfTest() {
        kernel = (VMKernel) Kernel.kernel;
        boolean createIt = false;
        OpenFile coffFile = Machine.stubFileSystem().open("echo.coff", createIt);
        try {
            exe = new Coff(coffFile);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return;
        }
        testOnePage();
    }

    public static void testOnePage() {
        final String METHOD_NAME = "SwapFileTest::testOnePage";
        java.util.Set<Integer> myPages = new java.util.HashSet<Integer>();
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
        int[] pages = kernel.malloc(1);
        if (null == pages || 0 == pages.length) {
            System.err.println(
                    "Unable to malloc enough space to run selfTest");
            for (Integer ppn : myPages) {
                kernel.free(new int[] { ppn });
            }
            return;
        }
        int ppn1 = pages[0];
        myPages.add(ppn1); // so we can free the later
        text.loadPage(0, ppn1);
        int spn = SwapFile.rollOut(ppn1);
        pages = kernel.malloc(1);
        if (null == pages || 0 == pages.length) {
            System.err.println(
                    "Unable to malloc enough space to run selfTest");
            for (Integer ppn : myPages) {
                kernel.free(new int[] { ppn });
            }
            return;
        }
        int ppn2 = pages[0];
        myPages.add(ppn2);
        SwapFile.rollIn(spn, ppn2);
        int ppn1offset = ppn1 * Processor.pageSize;
        int ppn2offset = ppn2 * Processor.pageSize;
        byte[] memory = Machine.processor().getMemory();
        for (int i = 0; i < Processor.pageSize; i++) {
            if (memory[ppn1offset + i] != memory[ppn2offset + i]) {
                throw new AssertionError("Bogus byte at "+i);
            }
        }
        System.out.println(METHOD_NAME +" OK");
        SwapFile.free(new int[] { spn });
        for (Integer ppn : myPages) {
            kernel.free(new int[] { ppn });
        }
    }
}
