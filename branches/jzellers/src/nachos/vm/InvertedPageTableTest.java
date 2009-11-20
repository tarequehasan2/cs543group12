package nachos.vm;

import nachos.machine.Coff;
import nachos.machine.Kernel;
import nachos.machine.Machine;
import nachos.machine.OpenFile;

public class InvertedPageTableTest {
    static Coff exe;
    static OpenFile coffFile;
    static VMKernel kernel;

    public static void selfTest() {
        kernel = (VMKernel) Kernel.kernel;
        if (!openExe("echo.coff")) {
            System.err.println("1:Unable to open echo.coff");
            return;
        }
        testOne();
        exe.close();
        coffFile.close();
    }

    public static void testOne() {
        final String METHOD_NAME = "IPTTest::testOne";

        final MockMachine machine = new MockMachine();
        InvertedPageTable.machine = machine;

        int sectionCount = exe.getNumSections();
        if (0 == sectionCount) {
            System.err.println("What kind of bogus COFF are you giving me?");
            return;
        }
        InvertedPageTable.addCoff(new VMProcess2(), 8);
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

    private static class VMProcess2 extends VMProcess {
        public int getPid() {
            return 0;
        }
        public Coff getCoff() {
            return exe;
        }
    }
}
