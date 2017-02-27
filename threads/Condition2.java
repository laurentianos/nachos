package nachos.threads;

import java.util.LinkedList;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
  private static final char dbgCond2 = 's';
  private Lock lock;
  private LinkedList<KThread> waitQueue;

  /**
   * Allocate a new condition variable.
   *
   * @param conditionLock
   *          the lock associated with this condition variable. The current
   *          thread must hold this lock whenever it uses <tt>sleep()</tt>,
   *          <tt>wake()</tt>, or <tt>wakeAll()</tt>.
   */
  public Condition2(Lock conditionLock) {
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.Condition2(): Start");
    this.lock = conditionLock;
    this.waitQueue = new LinkedList<KThread>();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.Condition2(): End");
  }

  /**
   * Atomically release the associated lock and go to sleep on this condition
   * variable until another thread wakes it using <tt>wake()</tt>. The current
   * thread must hold the associated lock. The thread will automatically
   * reacquire the lock before <tt>sleep()</tt> returns.
   */
  public void sleep() {
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): Start");
    Lib.assertTrue(this.lock.isHeldByCurrentThread());

    Machine.interrupt().disable();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): interrupts disabled");

    this.waitQueue.add(KThread.currentThread());
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): thread added to wait queue");

    this.lock.release();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): lock released");

    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): going to sleep");
    KThread.sleep();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): waking up");

    this.lock.acquire();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): lock aquired");
    Machine.interrupt().enable();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): interrupts enabled");
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.sleep(): End");
  }

  /**
   * Wake up at most one thread sleeping on this condition variable. The current
   * thread must hold the associated lock.
   */
  public void wake() {
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): Start");
    Lib.assertTrue(this.lock.isHeldByCurrentThread());

    Machine.interrupt().disable();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): interrupts disabled");

    if (!this.waitQueue.isEmpty()) {
      Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): wait queue is not empty");
      this.waitQueue.remove().ready();
      Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): first thread awake");
    }

    Machine.interrupt().enable();
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): interrupts enabled");

    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wake(): End");
  }

  /**
   * Wake up all threads sleeping on this condition variable. The current thread
   * must hold the associated lock.
   */
  public void wakeAll() {
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wakeAll(): Start");
    Lib.assertTrue(this.lock.isHeldByCurrentThread());

    while (!this.waitQueue.isEmpty()) {
      Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wakeAll(): wait queue is not empty");
      wake();
      Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wakeAll(): wake next thread");
    }
    Lib.debug(dbgCond2, Machine.timer().getTime() + " -- Condition2.wakeAll(): End");
  }
}