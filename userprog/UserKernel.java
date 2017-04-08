package nachos.userprog;

import java.util.LinkedList;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.Semaphore;
import nachos.threads.ThreadedKernel;

/* File Object which stores a file and all it's attributes*/

class File {

  Object OpenFile;
  int counter = 0;
  boolean isWriting = false;
  boolean isLinked = true;

  /* Constructor */
  public File() {

    this.counter = 1;
    this.isWriting = false;
    this.isLinked = true;
  }

  /* Returns the OpenFile inside the File class */
  public OpenFile getOpenFile() {
    return (OpenFile) this.OpenFile;
  }

  public int getCounter() {
    return this.counter;
  }

  /* Checks if file is open */
  public boolean isOpen() {
    if (this.counter > 0)
      return true;
    else
      return false;
  }

  public boolean getIsWriting() {
    return this.isWriting;
  }

  public boolean getIsLinked() {
    return this.isLinked;
  }

  /* Whenever the file is opened increment counter by one */
  public void incCounter() {
    ++this.counter;
  }

  /* Decrement counter by one */
  public void decCounter() {
    --this.counter;
  }

  public void setWriting(boolean writing) {
    this.isWriting = writing;
  }

  public void setIsLinked(boolean linked) {
    this.isLinked = linked;
  }

}

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {

  double[] globalPageTable = new double[64];

  /**
   * Allocate a new user kernel.
   */
  public UserKernel() {
    super();

  }

  /**
   * Initialize this kernel. Creates a synchronized console and sets the
   * processor's exception handler.
   */
  @Override
  public void initialize(String[] args) {
    super.initialize(args);

    console = new SynchConsole(Machine.console());

    Machine.processor().setExceptionHandler(new Runnable() {
      @Override
      public void run() {
        exceptionHandler();
      }
    });
  }

  /**
   * Test the console device.
   */
  @Override
  public void selfTest() {
    super.selfTest();

    System.out.println("Testing the console device. Typed characters");
    System.out.println("will be echoed until q is typed.");

    char c;

    do {
      c = (char) console.readByte(true);
      console.writeByte(c);
    } while (c != 'q');

    System.out.println("");
  }

  /**
   * Returns the current process.
   *
   * @return the current process, or <tt>null</tt> if no process is current.
   */
  public static UserProcess currentProcess() {
    if (!(KThread.currentThread() instanceof UThread))
      return null;

    return ((UThread) KThread.currentThread()).process;
  }

  /**
   * The exception handler. This handler is called by the processor whenever a
   * user instruction causes a processor exception.
   *
   * <p>
   * When the exception handler is invoked, interrupts are enabled, and the
   * processor's cause register contains an integer identifying the cause of the
   * exception (see the <tt>exceptionZZZ</tt> constants in the
   * <tt>Processor</tt> class). If the exception involves a bad virtual address
   * (e.g. page fault, TLB miss, read-only, bus error, or address error), the
   * processor's BadVAddr register identifies the virtual address that caused
   * the exception.
   */
  public void exceptionHandler() {
    Lib.assertTrue(KThread.currentThread() instanceof UThread);

    UserProcess process = ((UThread) KThread.currentThread()).process;
    int cause = Machine.processor().readRegister(Processor.regCause);
    process.handleException(cause);
  }

  /**
   * Start running user programs, by creating a process and running a shell
   * program in it. The name of the shell program it must run is returned by
   * <tt>Machine.getShellProgramName()</tt>.
   *
   * @see nachos.machine.Machine#getShellProgramName
   */
  @Override
  public void run() {
    super.run();

    UserProcess process = UserProcess.newUserProcess();

    String shellProgram = Machine.getShellProgramName();
    Lib.assertTrue(process.execute(shellProgram, new String[] {}));

    KThread.currentThread().finish();
  }

  /**
   * Terminate this kernel. Never returns.
   */
  @Override
  public void terminate() {
    super.terminate();
  }

  /** Globally accessible reference to the synchronized console. */
  public static SynchConsole console;

  // dummy variables to make javac smarter
  private static Coff dummy1 = null;
  public static int pid;
  public static int numProcesses;
  public static UserProcess rootProcess;
  public static LinkedList<Integer> ppgs;
  public static Semaphore ppMutex;
}
