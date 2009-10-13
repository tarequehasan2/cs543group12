package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	fileDescriptors = new OpenFile[maxNumFiles];
	filePositions = new int[maxNumFiles];
	// set our current file position to "not open"
	for (int i = 0; i < maxNumFiles; i++) {
		filePositions[i] = -1;
	}
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	mutex.acquire();
	pid = currentPID;
	currentPID++;
	mutex.release();
    }
    
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(memory, vaddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0 || vaddr >= memory.length)
	    return 0;

	int amount = Math.min(length, memory.length-vaddr);
	System.arraycopy(data, offset, memory, vaddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}
	
	// open stdin and stdout
	fileDescriptors[0] = UserKernel.console.openForReading();
	filePositions[0] = 0;
	fileDescriptors[1] = UserKernel.console.openForWriting();
	filePositions[1] = 0;
	numOpenFiles = 2;

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;

		// for now, just assume virtual addresses=physical addresses
		section.loadPage(i, vpn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	//  TODO implement me
    	
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {
    	if (pid == 0){
    		Machine.halt();
	
    		Lib.assertNotReached("Machine.halt() did not halt machine!");
    	}
    	// silently fail
    	return 0;
    }


    private static final int
        syscallHalt = 0,
    	syscallExit = 1,
    	syscallExec = 2,
    	syscallJoin = 3,
    	syscallCreate = 4,
    	syscallOpen = 5,
    	syscallRead = 6,
    	syscallWrite = 7,
    	syscallClose = 8,
    	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
	    handleExit(a0);
	    break;
	case syscallExec:
	    return handleExec(a0,a1,a2);
	case syscallJoin:
	    return handleJoin(a0,a1);
	case syscallCreate:
	    return handleCreate(a0);
	case syscallOpen:
	    return handleOpen(a0);
	case syscallRead:
	    return handleRead(a0,a1,a2);
	case syscallWrite:
	    return handleWrite(a0,a1,a2);
	case syscallClose:
	    return handleClose(a0);
	case syscallUnlink:
    	return handleUnlink(a0);
	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    private int handleUnlink(int a0) {
		// TODO Auto-generated method stub
		return 0;
	}

	private int handleClose(int a0) {
		int status = 0;
		try
		{
			if (a0 < maxNumFiles && fileDescriptors[a0] != null)
			{
				fileDescriptors[a0].close();
				fileDescriptors[a0] = null;
				filePositions[a0] = -1;
				numOpenFiles--;
			}
		}
		catch (Exception ex)
		{
			status = -1;
		}
		return status;
	}

	private int handleWrite(int a0, int a1, int a2) {
		// TODO Auto-generated method stub
		return 0;
	}

	private int handleRead(int a0, int a1, int a2) {
		// TODO Auto-generated method stub
		return 0;
	}

	private int handleOpen(int a0) {
		int fd = 2;
		try
		{
			if (numOpenFiles < maxNumFiles)
			{
				byte[] data = new byte[256];
				int strLength = readVirtualMemory(a0, data);
				if (strLength == 0)
				{
					return -1;
				}
				String filename = new String(data);
				FileSystem fs = Machine.stubFileSystem();
				OpenFile file = fs.open(filename, false);
				if (file != null)
				{
					while (fileDescriptors[fd] != null)
					{
						fd++;
					}
					fileDescriptors[fd] = file;
					filePositions[fd] = 0;
					numOpenFiles++;
				}
				else 
				{
					fd = -1;
				}
			}
		}
		catch (Exception ex)
		{
			return -1;
		}
		return fd;
	}

	private int handleCreate(int a0) {
		int fd = 2;
		try
		{
			if (numOpenFiles < maxNumFiles)
			{
				byte[] data = new byte[256];
				int strLength = readVirtualMemory(a0, data);
				if (strLength == 0)
				{
					return -1;
				}
				String filename = new String(data);
				FileSystem fs = Machine.stubFileSystem();
				OpenFile file = fs.open(filename, true);
				if (file != null)
				{
					while (fileDescriptors[fd] != null)
					{
						fd++;
					}
					fileDescriptors[fd] = file;
					filePositions[fd] = 0;
					numOpenFiles++;
				}
				else 
				{
					fd = -1;
				}
			}
		}
		catch (Exception ex)
		{
			return -1;
		}
		return fd;
	}
	
	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * processID is the process ID of the child process, returned by exec().
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 */
	private int handleJoin(int a0, int a1) {
		instanceMutex.acquire();
		int index = -1;
		for (int i= 0; i < children.size() ;i++){
			UserProcess child =children.get(i);
			if (child.pid == a0){
				index = i;
				break;
			}
		}
		if (index == -1){
			instanceMutex.release();
			return index;
		}else{
			children.remove(index);
		}
		if (!terminatedChildren.containsKey(a0)){
			waitingToJoin = a0;
			joinCondition.sleep();
		}
		int sizeOfInt = 4;
		Integer status = terminatedChildren.get(a0);
		if (status != null){
			int numberOfBytesWritten = writeVirtualMemory(a1, Lib.bytesFromInt(status));
			if (numberOfBytesWritten != sizeOfInt){
				instanceMutex.release();
				return -1;
			}
		}
		instanceMutex.release();
		if (status != null && status.equals(0)){
			return 1;
		}else{
			return 0;
		}
	}

	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * file is a null-terminated string that specifies the name of the file
	 * containing the executable. Note that this string must include the ".coff"
	 * extension.
	 *
	 * argc specifies the number of arguments to pass to the child process. This
	 * number must be non-negative.
	 *
	 * argv is an array of pointers to null-terminated strings that represent the
	 * arguments to pass to the child process. argv[0] points to the first
	 * argument, and argv[argc-1] points to the last argument.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 */
	private int handleExec(int a0, int a1, int a2) {
		int sizeOfInt = 4;
		int error = -1;
		if (a0 < 0 || a1 < 0){
			return error;
		}
			// method adds 1 to numBytes for max 256
		String fileName = readVirtualMemoryString(a0, 255);
		if (fileName == null || !fileName.endsWith(".coff")){
			return error;
		}
		String[] arguments = new String[a1];
		int currentVaddr = a2;
		for (int i = 0; i < a2; i++){
			byte[] data = new byte[sizeOfInt];
			int numberOfBytesXferd = readVirtualMemory(currentVaddr, data, 0, sizeOfInt);
			if (numberOfBytesXferd != 4){
				return error;
			}
			String argument = readVirtualMemoryString(Lib.bytesToInt(data, 0),255);
			if (argument == null){
				return error;
			}
			arguments[i]=argument;
			currentVaddr += sizeOfInt;
		}
		UserProcess child = new UserProcess();
		boolean executed = child.execute(fileName, arguments);
		child.parentProcess = this;
		children.add(child);
		return (executed)? child.pid : error;
	}
	
	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall. A process exiting normally should
	 * (but is not required to) set status to 0.
	 *
	 * exit() never returns.
	 */
	private void handleExit(int a0) {
		for (int i = 0; i < maxNumFiles; i++){
			if (fileDescriptors[i] != null){
				fileDescriptors[i].close();
				fileDescriptors[i] = null;
				filePositions[i] = -1;
				numOpenFiles--;
			}
		}
		Lib.assertTrue(numOpenFiles == 0, "Something's awry with the files");
		
		for (UserProcess child : children){
			child.parentProcess = null;
		}
		if (parentProcess != null){
			parentProcess.instanceMutex.acquire();
			parentProcess.terminatedChildren.put(this.pid, a0);
			if (parentProcess.waitingToJoin == this.pid){
				parentProcess.joinCondition.wake();
			}
			parentProcess.instanceMutex.release();
		}
		unloadSections();
		UThread.finish();
	}

	/**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
    private OpenFile[] fileDescriptors;
    private int[] filePositions;
    private int numOpenFiles = 0;
    private int pid = -1;
    private Map<Integer, Integer> terminatedChildren = new HashMap<Integer, Integer>();
    private UserProcess parentProcess = null;
	private List<UserProcess> children = new ArrayList<UserProcess>();
	private Lock instanceMutex = new Lock();
	private Condition2 joinCondition = new Condition2(instanceMutex);
	private int waitingToJoin;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final int maxNumFiles = 16;
    private static int currentPID = 0;
    private static Lock mutex = new Lock();
}
