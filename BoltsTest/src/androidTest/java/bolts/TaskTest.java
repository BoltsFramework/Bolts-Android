package bolts;

import android.os.Looper;
import android.test.InstrumentationTestCase;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public class TaskTest extends InstrumentationTestCase {

  public void testContinueOnUiThread() {
    assertNotSame(Looper.myLooper(), Looper.getMainLooper());

    runTaskTest(new Callable<Task<?>>() {
      @Override
      public Task<Void> call() throws Exception {
        return Task.call(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            assertSame(Looper.myLooper(), Looper.getMainLooper());
            return null;
          }
        }, Task.UI_THREAD_EXECUTOR).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertNotSame(Looper.myLooper(), Looper.getMainLooper());
            return null;
          }
        }, Task.BACKGROUND_EXECUTOR).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertSame(Looper.myLooper(), Looper.getMainLooper());
            return null;
          }
        }, Task.UI_THREAD_EXECUTOR);
      }
    });
  }

  private void runTaskTest(Callable<Task<?>> callable) {
    try {
      Task<?> task = callable.call();
      task.waitForCompletion();
      if (task.isFaulted()) {
        Exception error = task.getError();
        if (error instanceof RuntimeException) {
          throw (RuntimeException) error;
        }
        throw new RuntimeException(error);
      } else if (task.isCancelled()) {
        throw new RuntimeException(new CancellationException());
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
