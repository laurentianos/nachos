package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

public class WaitingThread implements Comparable<WaitingThread> {
  private static final char dbgWaitingThread = 'w';
  private long wakeTime;
  private KThread thread;

  public WaitingThread(long wakeTime, KThread thread) {
    Lib.debug(dbgWaitingThread, Machine.timer().getTime() + " -- WaitingThread.WaitingThread(): START");
    this.wakeTime = wakeTime;
    this.thread = thread;
    Lib.debug(dbgWaitingThread, Machine.timer().getTime() + " -- WaitingThread.WaitingThread(): END");
  }

  public long getWakeTime() {
    return this.wakeTime;
  }

  public KThread getThread() {
    return this.thread;
  }

  @Override
  public int compareTo(WaitingThread that) {
    return (int) (this.wakeTime - that.wakeTime);
  }
}