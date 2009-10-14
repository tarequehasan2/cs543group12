package nachos.userprog;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.FileSystem;
import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Condition2;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

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
	{
	    int vpn = i;
	    int ppn = i;
	    boolean valid = true;
	    boolean readOnly = false;
	    boolean used = false;
	    boolean dirty = false;
		pageTable[vpn] = new TranslationEntry(vpn, ppn, valid, readOnly, used, dirty);
	}
	mutex.acquire();
	pid = currentPID;
	currentPID++;
	numActiveProcesses++;
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
    	debug("Loading "+name+" ("+java.util.Arrays.toString(args)+")");
	if (!load(name, args)) 
	{
		debug("load() returned FALSE, so execute() is returning false");
	    return false;
	}
	debug( "Forking UThread("+name+")...");
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
	debug( "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    debug( "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    debug( "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		debug( "\tfragmented executable");
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
	    argsSize += (SIZEOF_INT + argv[i].length + 1);
	}
	if (argsSize > pageSize) {
	    coff.close();
	    debug( "\targuments too long");
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
	int stringOffset = entryOffset + args.length * SIZEOF_INT;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == SIZEOF_INT);
	    entryOffset += SIZEOF_INT;
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
	    debug( "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    debug( "\tinitializing " + section.getName()
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
	for (int i=0; i<Processor.numUserRegisters; i++)
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
    	int returnStatus;
    	try {
    		String filename = readVirtualMemoryString(a0, MAX_STRING_LENGTH);
    		FileSystem fs = Machine.stubFileSystem();
    		boolean status = fs.remove(filename);
    		if (status){
    			returnStatus = 0;
    		}
    		else{
    			returnStatus = -1;	
    		}
    	}
    	catch (Exception ex) {
    		returnStatus = -1;
    	}
    	return returnStatus;
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
		return -1;
	}

	private int handleRead(int a0, int a1, int a2) {
		// TODO Auto-generated method stub
		return -1;
	}

	private int handleOpen(int a0) {
		debug("handleOpen("+a0+")");
		int fd = 2;
		try
		{
			if (numOpenFiles < maxNumFiles)
			{
				String filename = readVirtualMemoryString(a0, MAX_STRING_LENGTH);
				FileSystem fs = Machine.stubFileSystem();
				boolean createOnOpen = false;
				OpenFile file = fs.open(filename, createOnOpen);
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
				String filename = readVirtualMemoryString(a0, MAX_STRING_LENGTH);
				if (null == filename || 0 == filename.length()) {
					return -1;
				}
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
		Integer status = terminatedChildren.get(a0);
		if (status != null){
			int numberOfBytesWritten = writeVirtualMemory(a1, Lib.bytesFromInt(status));
			if (numberOfBytesWritten != SIZEOF_INT){
				instanceMutex.release();
				return -1;
			}
		}
		instanceMutex.release();
		if (status != null && status.equals(-1)){
			return 0;
		}else{
			return 1;
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
		debug("handleExec("+a0+","+a1+","+a2+")");
		final int error = -1;
		if (a0 < 0 || a1 < 0){
			return error;
		}
			// method adds 1 to numBytes for max 256
		String fileName = readVirtualMemoryString(a0, MAX_STRING_LENGTH);
		if (fileName == null || !fileName.endsWith(".coff")){
			return error;
		}
		debug("argc="+a1);
		String[] arguments = new String[a1];
		int currentVaddr = a2;
		for (int i = 0; i < a1; i++) {
			byte[] data = new byte[SIZEOF_INT];
			int numberOfBytesXferd = readVirtualMemory(currentVaddr, data);
			if (numberOfBytesXferd != data.length){
				return error;
			}
			int ptrArgv = Lib.bytesToInt(data, 0);
			debug("&argc["+i+"]:= 0x"+Integer.toHexString(ptrArgv));
			String argument = null;
			if (0 != ptrArgv) {
				argument = readVirtualMemoryString(ptrArgv, MAX_STRING_LENGTH);
				// is this true?
				if (argument == null){
					return error;
				}
			}
			debug("argc["+i+"]:="+argument);
			arguments[i]=argument;
			currentVaddr += SIZEOF_INT;
		}
		UserProcess child = new UserProcess();
		debug("execte("+fileName+","+java.util.Arrays.toString(arguments)+")");
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

		mutex.acquire();
		numActiveProcesses--;
		final boolean noMoreProcesses = (numActiveProcesses == 0);
		mutex.release();
		if (noMoreProcesses){
			Kernel.kernel.terminate();
		}
		debug("calling finish()");
		UThread.finish();
		debug("bye!");
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
		int syscallNumber = processor.readRegister(Processor.regV0);
		debug("UserProcess::handleException,SYSCALL("+syscallNumber+")");
	    int result = handleSyscall(syscallNumber,
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    debug("Unexpected exception: " + Processor.exceptionNames[cause]);
	    handleExit(-1);
	    Lib.assertNotReached("Unexpected exception");
	}
    }
    
    public int getPid() {
    	return pid;
    }
    
    /**
     * Returns true iff the virtual address provided could be reasonable.
     * N.B. this does not check to ensure the UserProcess has access to the
     * memory, or any other semantic checks.
     * @param vaddr the virtual address relative to this UserProcess
     * @return true if the provided vaddr could be a valid address,
     * false otherwise.
     */
    private boolean rangeCheckMemoryAccess(int vaddr) {
		// TODO: better range checking
    	final int maximumMemory = Processor.pageSize 
								* Machine.processor().getNumPhysPages();
		return (vaddr >= 0 || vaddr <= maximumMemory);
	}

    /**
     * Ensures the provided file descriptor is a legitimate one for use by
     * this UserProcess. This goes one step further 
     * than {@link #rangeCheckFileDescriptor(int)} and actually checks to see
     * if the file descriptor is non-null. 
     * @param descriptorNumber the descriptor index which should be checked
     * @return true iff the file descriptor at that index may safely be
     * dereferenced.
     */
    private boolean checkForFileDescriptor(int descriptorNumber) {
    	boolean fdOk = rangeCheckFileDescriptor(descriptorNumber);
    	if (!fdOk)
    	{
    		return false;
    	}
    	// or whatever other exciting checks we want to include
    	return this.fileDescriptors[descriptorNumber] != null;
    }

    /**
     * Returns true iff the provided file descriptor index 
     * is zero to {@link UserProcess.maxNumFiles} (inclusive). N.B. this method
     * does not guarantee the file descriptor at that index is live (i.e. non-null).
     * For that, {@link #checkForFileDescriptor(int)}.
     * @see #checkForFileDescriptor(int)
     * @param descriptorNumber the number of the entry in the file descriptor table
     * @return true if that could be a valid file descriptor, false otherwise.
     */
    private boolean rangeCheckFileDescriptor(int descriptorNumber) {
		return (descriptorNumber > 0 || 
				descriptorNumber < this.fileDescriptors.length);
	}
    
    private void debugHex(String title, byte[] data) {
    	final int length = 72;
    	final String EOL = System.getProperty("line.separator");
    	StringBuilder sb = new StringBuilder();
    	sb.append(title).append(EOL);
    	sb.append("[data]").append(EOL);
    	if (null == data)
    	{
    		sb.append("null");
    	}
    	else
    	{
    		int count = 0;
    		for (int i = 0; i < data.length; i++)
    		{
    			int b = data[i] & 0xFF;
    			if (b < 0x10)
    			{
    				sb.append('0');
    			}
    			sb.append(Integer.toHexString(b));
    			sb.append(' ');
    			count += 3;
    			if (count + 3 > length)
    			{
    				count = 0;
    				sb.append(EOL);
    			}
    		}
    		sb.append(EOL);
    	}
    	sb.append("[end]").append(EOL);
    	debug(sb.toString());
    }
    
    private void debug(String msg) {
    	Lib.debug(dbgProcess, "UserProcess("+pid+"):"+msg);
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
    /**
     * Maps the process id of the terminated children
     * to their returned status code.
     */
    private Map<Integer, Integer> terminatedChildren = new HashMap<Integer, Integer>();
    private UserProcess parentProcess;
	private List<UserProcess> children = new ArrayList<UserProcess>();
	private Lock instanceMutex = new Lock();
	private Condition2 joinCondition = new Condition2(instanceMutex);
	/**
	 * Contains the PID of the child upon which we are waiting in handleJoin().
	 */
	private int waitingToJoin;
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    private static final int maxNumFiles = 16;
    /**
     * Maintains the global PID counter,
     * which is protected by <tt>mutex</tt>.
     */
    private static int currentPID = 0;
    private static int numActiveProcesses = 0;
    private static Lock mutex = new Lock();
    /**
     * Contains the maximum string length for a syscall.
     * As specified in the project 2 assignment, part 1.
     */
	private static final int MAX_STRING_LENGTH = 256;
	private static final int SIZEOF_INT = 4;
}
