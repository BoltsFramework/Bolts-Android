/*
 *  Copyright (c) 2014, Facebook, Inc.
 *  All rights reserved.
 *
 *  This source code is licensed under the BSD-style license found in the
 *  LICENSE file in the root directory of this source tree. An additional grant
 *  of patent rights can be found in the PATENTS file in the same directory.
 *
 */
package bolts;

import android.os.Build;
import android.test.InstrumentationTestCase;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

public class AndroidExecutorsTest extends InstrumentationTestCase {

  private static final int CORE_POOL_SIZE = AndroidExecutors.CORE_POOL_SIZE;
  private static final int MAX_POOL_SIZE = AndroidExecutors.MAX_POOL_SIZE;
  private static final int THREAD_TIMEOUT = (int)(AndroidExecutors.KEEP_ALIVE_TIME * 1000 * 1.1);

  LinkedList<CountDownLatch> corePoolAwaitLatchStack;
  LinkedList<CountDownLatch> corePoolEndLatchStack;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    corePoolAwaitLatchStack = new LinkedList<CountDownLatch>();
    corePoolEndLatchStack = new LinkedList<CountDownLatch>();
  }

  /**
   * Test whether or not the core pool will time out.
   *
   * Core pool only times out to 0 on android-9+ only
   *
   * @throws InterruptedException
   */
  public void testNewCachedThreadPoolTimeout() throws InterruptedException {
    ThreadPoolExecutor executor = (ThreadPoolExecutor) AndroidExecutors.newCachedThreadPool();

    // Make sure we start at 0
    assertEquals(0, executor.getPoolSize());

    // Fill core pool
    pushTasks(executor, CORE_POOL_SIZE, true);
    assertEquals(CORE_POOL_SIZE, executor.getPoolSize());
    assertEquals(0, executor.getQueue().size());

    // Empty core pool
    popTasks(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
      assertEquals(0, executor.getPoolSize());
    } else {
      assertEquals(CORE_POOL_SIZE, executor.getPoolSize());
    }
  }

  /**
   * Push or start X operations on to the executor that will not complete until they are popped off.
   *
   * @see AndroidExecutorsTest#popTasks(boolean)
   *
   * @param executor
   *          The executor to execute operations on.
   * @param count
   *          How many operations to execute.
   * @param waitForStart
   *          Control whether or not to wait until all the operations start to complete before
   *          returning from this method.
   * @throws InterruptedException
   */
  private void pushTasks(ExecutorService executor, int count, boolean waitForStart)
      throws InterruptedException {
    CountDownLatch startLatch = waitForStart ? new CountDownLatch(count) : null;
    CountDownLatch awaitLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(count);

    corePoolAwaitLatchStack.add(0, awaitLatch);
    corePoolEndLatchStack.add(0, endLatch);

    for (int i = 0; i < count; i++) {
      executor.execute(newWaitRunnable(startLatch, awaitLatch, endLatch));
    }

    if (startLatch != null) {
      startLatch.await();
    }
  }

  /**
   * Pop or end the last group of pushed operations.
   *
   * @param waitForEnd
   *          Control whether or not to wait until all the operations complete before returning from
   *          this method.
   * @throws InterruptedException
   */
  private void popTasks(boolean waitForEnd) throws InterruptedException {
    CountDownLatch awaitLatch = corePoolAwaitLatchStack.remove(0);
    CountDownLatch endLatch = corePoolEndLatchStack.remove(0);

    awaitLatch.countDown();
    if (waitForEnd) {
      endLatch.await();
    }
    // Wait for threads to die
    Thread.sleep(THREAD_TIMEOUT);
  }

  /**
   * Creates a new waitable runnable.
   *
   * @param startLatch
   *          Latch that alerts the operation has started.
   * @param awaitLatch
   *          Latch that keeps the operation running.
   * @param endLatch
   *          Latch that alerts the operation has completed.
   * @return
   */
  private Runnable newWaitRunnable(final CountDownLatch startLatch,
      final CountDownLatch awaitLatch, final CountDownLatch endLatch) {
    return new Runnable() {
      @Override
      public void run() {
        if (startLatch != null) {
          startLatch.countDown();
        }

        if (awaitLatch != null) {
          try {
            awaitLatch.await();
          } catch (InterruptedException e) {
            // do nothing
          }
        }

        if (endLatch != null) {
          endLatch.countDown();
        }
      }
    };
  }
}
