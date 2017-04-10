package nachos.userprog;

import java.io.EOFException;
import java.util.Hashtable;
import java.util.NoSuchElementException;

import nachos.machine.Coff;
import nachos.machine.CoffSection;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.Condition;
import nachos.threads.Lock;
import nachos.threads.Semaphore;
import nachos.threads.ThreadedKernel;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
  private static final char dbgProcess = 'a';
  private static final int pageSize = Processor.pageSize;

  /** The number of pages in the program's stack. */
  protected final int stackPages = 8;
  private static final int syscallClose = 8;
  private static final int syscallCreate = 4;
  private static final int syscallExec = 2;
  private static final int syscallExit = 1;
  private static final int syscallHalt = 0;
  private static final int syscallJoin = 3;
  private static final int syscallOpen = 5;
  private static final int syscallRead = 6;
  private static final int syscallUnlink = 9;
  private static final int syscallWrite = 7;

  /** The program being run by this process. */
  protected Coff coff;

  /** This process's page table. */
  protected TranslationEntry[] pageTable;

  /** The number of contiguous pages occupied by the program. */
  protected int numPages;
  private int argc;
  private int argv;
  private int initialPC;
  private int initialSP;

  protected OpenFile[] fd;
  protected int pid;
  protected UserProcess parent;
  protected Semaphore pMutex = new Semaphore(1);
  protected Hashtable<Integer, UserProcess> children = new Hashtable<Integer, UserProcess>();
  protected Integer exitStatus;
  protected Lock statusLock;
  protected Condition joinCond;

  /**
   * Allocate a new process.
   */
  public UserProcess() {
    int numPhysPages = Machine.processor().getNumPhysPages();
    this.pageTable = new TranslationEntry[numPhysPages];

    for (int i = 0; i < numPhysPages; i++)
      this.pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
    this.pid = UserKernel.pid;
    ++UserKernel.pid;
    this.fd = new OpenFile[16];
    this.fd[0] = UserKernel.console.openForReading();
    this.fd[1] = UserKernel.console.openForWriting();
    this.statusLock = new Lock();
    this.joinCond = new Condition(this.statusLock);
    this.exitStatus = null;
  }

  /**
   * Execute the specified program with the specified arguments. Attempts to
   * load the program, and then forks a thread to run it.
   *
   * @param name
   *          the name of the file containing the executable.
   * @param args
   *          the arguments to pass to the executable.
   * @return <tt>true</tt> if the program was successfully executed.
   */
  public boolean execute(String name, String[] args) {
    if (!load(name, args))
      return false;
    new UThread(this).setName(name).fork();
    return true;
  }

  /**
   * Save the state of this process in preparation for a context switch. Called
   * by <tt>UThread.saveState()</tt>.
   */
  public void saveState() {
  }

  /**
   * Restore the state of this process after a context switch. Called by
   * <tt>UThread.restoreState()</tt>.
   */
  public void restoreState() {
    Machine.processor().setPageTable(this.pageTable);
  }

  /**
   * Read a null-terminated string from this process's virtual memory. Read at
   * most <tt>maxLength + 1</tt> bytes from the specified address, search for
   * the null terminator, and convert it to a <tt>java.lang.String</tt>, without
   * including the null terminator. If no null terminator is found, returns
   * <tt>null</tt>.
   *
   * @param vaddr
   *          the starting virtual address of the null-terminated string.
   * @param maxLength
   *          the maximum number of characters in the string, not including the
   *          null terminator.
   * @return the string read, or <tt>null</tt> if no null terminator was found.
   */
  public String readVirtualMemoryString(int vaddr, int maxLength) {
    Lib.assertTrue(maxLength >= 0);
    byte[] bytes = new byte[maxLength + 1];
    int bytesRead = readVirtualMemory(vaddr, bytes);

    for (int length = 0; length < bytesRead; length++)
      if (bytes[length] == 0)
        return new String(bytes, 0, length);
    return null;
  }

  /**
   * Transfer data from this process's virtual memory to all of the specified
   * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
   *
   * @param vaddr
   *          the first byte of virtual memory to read.
   * @param data
   *          the array where the data will be stored.
   * @return the number of bytes successfully transferred.
   */
  public int readVirtualMemory(int vaddr, byte[] data) {
    return readVirtualMemory(vaddr, data, 0, data.length);
  }

  /**
   * Transfer data from this process's virtual memory to the specified array.
   * This method handles address translation details. This method must
   * <i>not</i> destroy the current process if an error occurs, but instead
   * should return the number of bytes successfully copied (or zero if no data
   * could be copied).
   *
   * @param vaddr
   *          the first byte of virtual memory to read.
   * @param data
   *          the array where the data will be stored.
   * @param offset
   *          the first byte to write in the array.
   * @param length
   *          the number of bytes to transfer from virtual memory to the array.
   * @return the number of bytes successfully transferred.
   */
  public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    int bytesWritten;
    int leftToWrite = length;
    int bufOffset = offset;
    byte[] memory = Machine.processor().getMemory();
    int vpn = Processor.pageFromAddress(vaddr);
    int vpnOffset = Processor.offsetFromAddress(vaddr);
    int pageOffset = vpnOffset;
    TranslationEntry entry = this.pageTable[vpn];
    int realAddr = entry.ppn * pageSize + vpnOffset;
    int currAddr = realAddr;
    int currVpn = vpn;
    int currPpn = entry.ppn;
    entry.used = true;
    bytesWritten = 0;

    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
    if (data == null || offset < 0 || length < 0 || offset + length > data.length)
      return 0;

    while (bytesWritten < length)
      if (pageOffset + leftToWrite > pageSize) {
        int amountToWrite = pageSize - pageOffset;
        System.arraycopy(memory, currAddr, data, bufOffset, amountToWrite);
        bytesWritten += amountToWrite;
        bufOffset += amountToWrite;
        leftToWrite = length - bytesWritten;
        ++currVpn;
        if (currVpn >= this.pageTable.length)
          break;
        else {
          this.pageTable[currVpn - 1].used = false;
          TranslationEntry currEntry = this.pageTable[currVpn];
          if (!currEntry.valid)
            break;
          currEntry.used = true;
          pageOffset = 0;
          currPpn = currEntry.ppn;
          currAddr = currPpn * pageSize;
        }
      } else {
        System.arraycopy(memory, currAddr, data, bufOffset, leftToWrite);
        bytesWritten += leftToWrite;
        bufOffset += leftToWrite;
      }
    this.pageTable[currVpn].used = false;
    return bytesWritten;
  }

  /**
   * Transfer all data from the specified array to this process's virtual
   * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
   *
   * @param vaddr
   *          the first byte of virtual memory to write.
   * @param data
   *          the array containing the data to transfer.
   * @return the number of bytes successfully transferred.
   */
  public int writeVirtualMemory(int vaddr, byte[] data) {
    return writeVirtualMemory(vaddr, data, 0, data.length);
  }

  /**
   * Transfer data from the specified array to this process's virtual memory.
   * This method handles address translation details. This method must
   * <i>not</i> destroy the current process if an error occurs, but instead
   * should return the number of bytes successfully copied (or zero if no data
   * could be copied).
   *
   * @param vaddr
   *          the first byte of virtual memory to write.
   * @param data
   *          the array containing the data to transfer.
   * @param offset
   *          the first byte to transfer from the array.
   * @param length
   *          the number of bytes to transfer from the array to virtual memory.
   * @return the number of bytes successfully transferred.
   */
  public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    int bytesWritten;
    int leftToWrite = length;
    int bufOffset = offset;
    byte[] memory = Machine.processor().getMemory();
    int vpn = Processor.pageFromAddress(vaddr);
    int vpnOffset = Processor.offsetFromAddress(vaddr);
    int pageOffset = vpnOffset;
    TranslationEntry entry = this.pageTable[vpn];
    int realAddr = entry.ppn * pageSize + vpnOffset;
    int currAddr = realAddr;
    int currVpn = vpn;
    int currPpn = entry.ppn;
    entry.used = true;
    bytesWritten = 0;

    Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
    if (data == null || offset < 0 || length < 0 || offset + length > data.length)
      return 0;

    // for now, just assume that virtual addresses equal physical addresses
    if (vaddr < 0 || vaddr >= memory.length)
      return 0;

    while (bytesWritten < length)
      if (pageOffset + leftToWrite > pageSize) {
        int amountToWrite = pageSize - pageOffset;
        System.arraycopy(memory, currAddr, data, bufOffset, amountToWrite);
        bytesWritten += amountToWrite;
        bufOffset += amountToWrite;
        leftToWrite = length - bytesWritten;
        ++currVpn;
        if (currVpn >= this.pageTable.length)
          break;
        else {
          this.pageTable[currVpn - 1].used = false;
          TranslationEntry currEntry = this.pageTable[currVpn];
          if (!currEntry.valid)
            break;
          currEntry.used = true;
          pageOffset = 0;
          currPpn = currEntry.ppn;
          currAddr = currPpn * pageSize;
        }
      } else {
        System.arraycopy(memory, currAddr, data, bufOffset, leftToWrite);
        bytesWritten += leftToWrite;
        bufOffset += leftToWrite;
      }
    this.pageTable[currVpn].used = false;
    return bytesWritten;
  }

  /**
   * Load the executable with the specified name into this process, and prepare
   * to pass it the specified arguments. Opens the executable, reads its header
   * information, and copies sections and arguments into this process's virtual
   * memory.
   *
   * @param name
   *          the name of the file containing the executable.
   * @param args
   *          the arguments to pass to the executable.
   * @return <tt>true</tt> if the executable was successfully loaded.
   */
  private boolean load(String name, String[] args) {
    Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
    OpenFile executable = ThreadedKernel.fileSystem.open(name, false);

    if (executable == null) {
      Lib.debug(dbgProcess, "\topen failed");
      return false;
    }
    try {
      this.coff = new Coff(executable);
    } catch (EOFException e) {
      executable.close();
      Lib.debug(dbgProcess, "\tcoff load failed");
      return false;
    }
    this.numPages = 0;
    for (int s = 0; s < this.coff.getNumSections(); s++) {
      CoffSection section = this.coff.getSection(s);
      if (section.getFirstVPN() != this.numPages) {
        this.coff.close();
        Lib.debug(dbgProcess, "\tfragmented executable");
        return false;
      }
      this.numPages += section.getLength();
    }
    byte[][] argv = new byte[args.length][];
    int argsSize = 0;
    for (int i = 0; i < args.length; i++) {
      argv[i] = args[i].getBytes();
      argsSize += 4 + argv[i].length + 1;
    }
    if (argsSize > pageSize) {
      this.coff.close();
      Lib.debug(dbgProcess, "\targuments too long");
      return false;
    }
    this.initialPC = this.coff.getEntryPoint();
    this.numPages += this.stackPages;
    this.initialSP = this.numPages * pageSize;
    ++this.numPages;
    if (!loadSections())
      return false;
    int entryOffset = (this.numPages - 1) * pageSize;
    int stringOffset = entryOffset + args.length * 4;
    this.argc = args.length;
    this.argv = entryOffset;
    for (int i = 0; i < argv.length; i++) {
      byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
      Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
      entryOffset += 4;
      Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
      stringOffset += argv[i].length;
      Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
      stringOffset += 1;
    }
    ++UserKernel.numProcesses;
    return true;
  }

  /**
   * Allocates memory for this process, and loads the COFF sections into memory.
   * If this returns successfully, the process will definitely be run (this is
   * the last step in process initialization that can fail).
   *
   * @return <tt>true</tt> if the sections were successfully loaded.
   */
  protected boolean loadSections() {
    if (this.numPages > Machine.processor().getNumPhysPages()) {
      this.coff.close();
      Lib.debug(dbgProcess, "\tinsufficient physical memory");
      return false;
    }
    for (int s = 0; s < this.coff.getNumSections(); s++) {
      CoffSection section = this.coff.getSection(s);
      Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");
      for (int i = 0; i < section.getLength(); i++) {
        int vpn = section.getFirstVPN() + i;
        TranslationEntry entry = this.pageTable[vpn];
        UserKernel.ppMutex.P();
        Integer thePage = null;
        try {
          thePage = UserKernel.ppgs.removeFirst();
        } catch (NoSuchElementException e) {
          unloadSections();
          return false;
        }
        UserKernel.ppMutex.V();
        entry.ppn = thePage;
        entry.valid = true;
        entry.readOnly = section.isReadOnly();
        section.loadPage(i, entry.ppn);
      }
    }
    for (int i = this.numPages - 9; i < this.numPages; i++) {
      TranslationEntry entry = this.pageTable[i];
      UserKernel.ppMutex.P();
      Integer pageNumber = null;
      try {
        pageNumber = UserKernel.ppgs.removeFirst();
      } catch (NoSuchElementException e) {
        unloadSections();
        return false;
      }
      UserKernel.ppMutex.V();
      entry.ppn = pageNumber;
      entry.valid = true;
    }
    return true;
  }

  /**
   * Release any resources allocated by <tt>loadSections()</tt>.
   */
  protected void unloadSections() {
    for (int i = 0; i < this.pageTable.length; i++) {
      TranslationEntry entry = this.pageTable[i];
      if (entry.valid) {
        UserKernel.ppMutex.P();
        UserKernel.ppgs.add(entry.ppn);
        UserKernel.ppMutex.V();
      }
    }
  }

  /**
   * Initialize the processor's registers in preparation for running the program
   * loaded into this process. Set the PC register to point at the start
   * function, set the stack pointer register to point at the top of the stack,
   * set the A0 and A1 registers to argc and argv, respectively, and initialize
   * all other registers to 0.
   */
  public void initRegisters() {
    Processor processor = Machine.processor();
    for (int i = 0; i < processor.numUserRegisters; i++)
      processor.writeRegister(i, 0);
    processor.writeRegister(Processor.regPC, this.initialPC);
    processor.writeRegister(Processor.regSP, this.initialSP);
    processor.writeRegister(Processor.regA0, this.argc);
    processor.writeRegister(Processor.regA1, this.argv);
  }

  private int isInTable(String filename) {
    for (int i = 0; i < this.fd.length; i++) {
      OpenFile currFile = this.fd[i];
      if (currFile != null && filename == currFile.getName())
        return i;
    }
    return -1;
  }

  /**
   * Handle the halt() system call.
   */
  private int handleHalt() {
    if (this != UserKernel.rootProcess)
      return 0;
    Machine.halt();
    Lib.assertNotReached("Machine.halt() did not halt machine!");
    return 0;
  }

  /**
   * Handle the close() system call.
   */
  private int handleClose(int file) {
    if (file < 0 || file > 15) {
      Lib.debug(dbgProcess,
          "\thandleClose: Trying to close the file descriptor " + file + " which is outside the range");
      return -1;
    }
    OpenFile thisFile = this.fd[file];
    if (thisFile == null) {
      Lib.debug(dbgProcess, "\thandleClose: Trying to close a file that does not exist");
      return -1;
    } else {
      thisFile.close();
      this.fd[file] = null;
      return 0;
    }
  }

  /**
   * Handle the create() system call.
   */
  private int handleCreate(int file) {
    String filename = null;
    filename = readVirtualMemoryString(file, 256);
    if (filename == null) {
      Lib.debug(dbgProcess, "\thandleCreate: Could not read filename from Virtual Memory");
      return -1;
    }
    OpenFile thisFile = ThreadedKernel.fileSystem.open(filename, true);
    if (thisFile == null) {
      Lib.debug(dbgProcess, "\thandleCreate: Could not open file from filesystem");
      return -1;
    } else {
      int i;
      for (i = 2; i < this.fd.length; i++)
        if (this.fd[i] == null) {
          this.fd[i] = thisFile;
          return i;
        }
      if (i == this.fd.length) {
        Lib.debug(dbgProcess, "\thandleCreate: No more space in file descriptor");
        return -1;
      }
    }
    return -1;
  }

  /**
   * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
   * The <i>cause</i> argument identifies which exception occurred; see the
   * <tt>Processor.exceptionZZZ</tt> constants.
   *
   * @param cause
   *          the user exception that occurred.
   */
  public void handleException(int cause) {
    Processor processor = Machine.processor();
    switch (cause) {
    case Processor.exceptionSyscall:
      int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
          processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
          processor.readRegister(Processor.regA3));
      processor.writeRegister(Processor.regV0, result);
      processor.advancePC();
      break;
    default:
      Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
      Lib.assertNotReached("Unexpected exception");
    }
  }

  /**
   * Handle the exit() system call.
   */
  private int handleExec(int file, int argc, int argv) {
    String filename = null;
    filename = readVirtualMemoryString(file, 256);
    if (filename == null) {
      Lib.debug(dbgProcess, "\thandleExec: Could not read filename from Virtual Memory");
      return -1;
    }
    if (argc < 0) {
      Lib.debug(dbgProcess, "\thandleExec: argc < 0");
      return -1;
    }
    String[] args = new String[argc];
    byte[] buffer = new byte[4];
    for (int i = 0; i < argc; i++) {
      Lib.assertTrue(readVirtualMemory(argv + i * 4, buffer) == buffer.length);
      args[i] = readVirtualMemoryString(Lib.bytesToInt(buffer, 0), 256);
      if (args[i] == null) {
        Lib.debug(dbgProcess, "\thandleExec: Error reading arg " + i + " from virtual memory");
        return -1;
      }
    }
    UserProcess child = newUserProcess();
    this.children.put(child.pid, child);
    child.parent = this;
    boolean insertProgram = child.execute(filename, args);
    if (insertProgram)
      return child.pid;
    return -1;
  }

  /**
   * Handle the exit() system call.
   */
  private int handleExit(int status) {
    unloadSections();

    for (int i = 2; i < this.fd.length; i++)
      if (this.fd[i] != null)
        this.fd[i].close();
    this.statusLock.acquire();
    this.exitStatus = status;
    this.statusLock.release();
    this.pMutex.P();
    if (this.parent != null) {
      this.parent.statusLock.acquire();
      this.parent.joinCond.wakeAll();
      this.parent.statusLock.release();
    }
    this.pMutex.V();
    for (UserProcess chld : this.children.values()) {
      chld.pMutex.P();
      chld.parent = null;
      chld.pMutex.V();
    }
    UserKernel.numProcesses--;
    // if (UserKernel.processCount == 0)
    // TK terminate();
    UThread.finish();
    return status;
  }

  /**
   * Handle the join() system call.
   */
  private int handleJoin(int processID, int status) {
    if (!this.children.containsKey(processID)) {
      Lib.debug(dbgProcess,
          "\thandleJoin: Attempting to join a non-child process or" + " this is child this parent has already joined");
      return -1;
    }
    UserProcess child = this.children.get(processID);
    child.statusLock.acquire();
    Integer childStat = child.exitStatus;
    if (childStat == null) {
      this.statusLock.acquire();
      child.statusLock.release();
      this.joinCond.sleep();
      this.statusLock.release();
      child.statusLock.acquire();
      childStat = child.exitStatus;
    }
    child.statusLock.release();
    this.children.remove(processID);
    byte[] stat = Lib.bytesFromInt(childStat.intValue());
    writeVirtualMemory(status, stat);
    if (childStat.intValue() == 0)
      return 1;
    else
      return 0;
  }

  /**
   * Handle the open() system call.
   */
  private int handleOpen(int file) {
    String filename = null;
    filename = readVirtualMemoryString(file, 256);
    if (filename == null) {
      Lib.debug(dbgProcess, "\thandleOpen: Could not read filename from Virtual Memory");
      return -1;
    }
    OpenFile thisFile = ThreadedKernel.fileSystem.open(filename, false);
    if (thisFile == null) {
      Lib.debug(dbgProcess, "\thandleOpen: Could not open file from filesystem");
      return -1;
    } else {
      int i;
      for (i = 2; i < this.fd.length; i++)
        if (this.fd[i] == null) {
          this.fd[i] = thisFile;
          return i;
        }
      if (i == this.fd.length) {
        Lib.debug(dbgProcess, "\thandleOpen: No more space in file descriptor");
        return -1;
      }
    }
    return -1;
  }

  /**
   * Handle the read() system call.
   */
  private int handleRead(int file, int buffer, int count) {
    if (file < 0 || file == 1 || file > 15) {
      Lib.debug(dbgProcess, "\thandleRead: Trying to read a file that does not exist, fd out of range " + file);
      return -1;
    }
    OpenFile thisFile = this.fd[file];
    if (thisFile == null) {
      Lib.debug(dbgProcess, "\thandleRead: Trying to read a file that does not exist, file is null");
      return -1;
    }
    byte[] buff = new byte[pageSize];
    int leftToRead = count;
    int totalRead = 0;
    int readBytes = 0;

    while (leftToRead > pageSize) {
      readBytes = thisFile.read(buff, 0, pageSize);
      if (readBytes == -1) {
        Lib.debug(dbgProcess, "\thandleRead: Failed to read file");
        return -1;
      } else if (readBytes == 0)
        return totalRead;
      int redBytes = writeVirtualMemory(buffer, buff, 0, readBytes);
      if (readBytes != redBytes) {
        Lib.debug(dbgProcess, "\thandleRead: Read and write amounts did not match");
        return -1;
      }
      buffer += redBytes;
      totalRead += redBytes;
      leftToRead -= redBytes;
    }
    readBytes = thisFile.read(buff, 0, leftToRead);
    if (readBytes == -1) {
      Lib.debug(dbgProcess, "\thandleRead: Failed to read file");
      return -1;
    }
    int redBytes = writeVirtualMemory(buffer, buff, 0, readBytes);
    if (readBytes != redBytes) {
      Lib.debug(dbgProcess, "\thandleRead: Read and write amounts did not match");
      return -1;
    }
    totalRead += redBytes;
    return totalRead;
  }

  /**
   * Handle a syscall exception. Called by <tt>handleException()</tt>. The
   * <i>syscall</i> argument identifies which syscall the user executed:
   *
   * <table>
   * <tr>
   * <td>syscall#</td>
   * <td>syscall prototype</td>
   * </tr>
   * <tr>
   * <td>0</td>
   * <td><tt>void halt();</tt></td>
   * </tr>
   * <tr>
   * <td>1</td>
   * <td><tt>void exit(int status);</tt></td>
   * </tr>
   * <tr>
   * <td>2</td>
   * <td><tt>int  exec(char *name, int argc, char **argv);
   * 								</tt></td>
   * </tr>
   * <tr>
   * <td>3</td>
   * <td><tt>int  join(int pid, int *status);</tt></td>
   * </tr>
   * <tr>
   * <td>4</td>
   * <td><tt>int  creat(char *name);</tt></td>
   * </tr>
   * <tr>
   * <td>5</td>
   * <td><tt>int  open(char *name);</tt></td>
   * </tr>
   * <tr>
   * <td>6</td>
   * <td><tt>int  read(int fd, char *buffer, int size);
   *								</tt></td>
   * </tr>
   * <tr>
   * <td>7</td>
   * <td><tt>int  write(int fd, char *buffer, int size);
   *								</tt></td>
   * </tr>
   * <tr>
   * <td>8</td>
   * <td><tt>int  close(int fd);</tt></td>
   * </tr>
   * <tr>
   * <td>9</td>
   * <td><tt>int  unlink(char *name);</tt></td>
   * </tr>
   * </table>
   *
   * @param syscall
   *          the syscall number.
   * @param a0
   *          the first syscall argument.
   * @param a1
   *          the second syscall argument.
   * @param a2
   *          the third syscall argument.
   * @param a3
   *          the fourth syscall argument.
   * @return the value to be returned to the user.
   */
  public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    switch (syscall) {
    case syscallHalt:
      return handleHalt();
    case syscallExec:
      return handleExec(a0, a1, a2);
    case syscallJoin:
      return handleJoin(a0, a1);
    case syscallExit:
      return handleExit(a0);
    case syscallCreate:
      return handleCreate(a0);
    case syscallOpen:
      return handleOpen(a0);
    case syscallClose:
      return handleClose(a0);
    case syscallRead:
      return handleRead(a0, a1, a2);
    case syscallWrite:
      return handleWrite(a0, a1, a2);
    case syscallUnlink:
      return handleUnlink(a0);
    default:
      Lib.debug(dbgProcess, "Unknown syscall " + syscall);
      Lib.assertNotReached("Unknown system call!");
    }
    return 0;
  }

  /**
   * Handle the unlink() system call.
   */
  private int handleUnlink(int file) {
    String filename = readVirtualMemoryString(file, 256);
    if (filename == null) {
      Lib.debug(dbgProcess, "\thandleUnlink: Could not read filename from Virtual Memory");
      return -1;
    }
    int idxTable = isInTable(filename);
    if (idxTable != -1)
      handleClose(idxTable);
    if (ThreadedKernel.fileSystem.remove(filename))
      return 0;
    return -1;
  }

  /**
   * Handle the write() system call.
   */
  private int handleWrite(int file, int buffer, int count) {
    if (file == 0) {
      Lib.debug(dbgProcess, "\thandleRead: Trying to write to stdin");
      return -1;
    }
    if (file < 1 || file > 15) {
      Lib.debug(dbgProcess, "\thandleRead: Trying to write to a file that does not exist");
      return -1;
    }
    OpenFile thisFile = this.fd[file];
    if (thisFile == null) {
      Lib.debug(dbgProcess, "\thandleRead: Trying to write to a file that does not exist");
      return -1;
    }
    byte[] buff = new byte[pageSize];
    int leftToWrite = count;
    int totalWritten = 0;
    int writtenBytes = 0;
    while (leftToWrite > pageSize) {
      writtenBytes = readVirtualMemory(buffer, buff);
      int writeBytes = thisFile.write(buff, 0, writtenBytes);
      if (writtenBytes != writeBytes)
        Lib.debug(dbgProcess, "\tIn handleWrite and not all bytes written");
      if (writeBytes == -1) {
        Lib.debug(dbgProcess, "\thandleWrite: Failed to write to file");
        return -1;
      } else if (writeBytes == 0)
        return totalWritten;
      buffer += writeBytes;
      totalWritten += writeBytes;
      leftToWrite -= writeBytes;
    }
    writtenBytes = readVirtualMemory(buffer, buff, 0, leftToWrite);
    int writeBytes = thisFile.write(buff, 0, writtenBytes);
    if (writtenBytes != writeBytes)
      Lib.debug(dbgProcess, "\tIn handleWrite and not all bytes written");
    if (writeBytes == -1) {
      Lib.debug(dbgProcess, "\thandleWrite: Failed to write to file");
      return -1;
    }
    totalWritten += writeBytes;
    return totalWritten;
  }

  /**
   * Allocate and return a new process of the correct class. The class name is
   * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
   *
   * @return a new process of the correct class.
   */
  public static UserProcess newUserProcess() {
    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
  }
}
