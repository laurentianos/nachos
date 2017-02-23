package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
  private static final char DEBUG_FLAG = 'r';
  private PriorityQueue<WaitingThread> waitQueue = new PriorityQueue<WaitingThread>();

  /**
   * Allocate a new Alarm. Set the machine's timer interrupt handler to this
   * alarm's callback.
   *
   * <p>
   * <b>Note</b>: Nachos will not function correctly with more than one alarm.
   */
  public Alarm() {
    Machine.timer().setInterruptHandler(new Runnable() {
      @Override
      public void run() {
        timerInterrupt();
      }
    });
  }

  /**
   * The timer interrupt handler. This is called by the machine's timer
   * periodically (approximately every 500 clock ticks). Causes the current
   * thread to yield, forcing a context switch if there is another thread that
   * should be run.
   */
  public void timerInterrupt() {
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): Start");
    Machine.interrupt().disable();
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): interrupts disabled");
    while (!this.waitQueue.isEmpty() && this.waitQueue.peek().getWakeTime() > Machine.timer().getTime()) {
      Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): time to wake up");
      this.waitQueue.remove().getThread().ready();
      Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): thread awoken");
    }
    Machine.interrupt().enable();
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): interrupts enabled");
    KThread.currentThread().yield();
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.timerInterrupt(): End");
  }

  /**
   * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
   * in the timer interrupt handler. The thread must be woken up (placed in the
   * scheduler ready set) during the first timer interrupt where
   *
   * <p>
   * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
   *
   * @param x
   *          the minimum number of clock ticks to wait.
   *
   * @see nachos.machine.Timer#getTime()
   */
  public void waitUntil(long x) {
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.waitUntil(): Start");
    Machine.interrupt().disable();
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.waitUntil(): interrupts disabled");
    long wakeTime = Machine.timer().getTime() + x;
    this.waitQueue.add(new WaitingThread(wakeTime, KThread.currentThread()));
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.waitUntil(): current thread added to queue");
    KThread.sleep();
    Machine.interrupt().enable();
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.waitUntil(): interrupts enabled");
    Lib.debug(DEBUG_FLAG, Machine.timer().getTime() + " -- Alarm.waitUntil(): End");
  }
}
