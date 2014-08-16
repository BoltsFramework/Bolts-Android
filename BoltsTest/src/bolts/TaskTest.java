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

import android.os.Looper;
import android.test.InstrumentationTestCase;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskTest extends InstrumentationTestCase {
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
    Task.enableStackTracking(false);
  }

  public void testPrimitives() {
    Task<Integer> complete = Task.forResult(5);
    Task<Integer> error = Task.forError(new RuntimeException());
    Task<Integer> cancelled = Task.cancelled();

    assertTrue(complete.isCompleted());
    assertEquals(5, complete.getResult().intValue());
    assertFalse(complete.isFaulted());
    assertFalse(complete.isCancelled());

    assertTrue(error.isCompleted());
    assertTrue(error.getError() instanceof RuntimeException);
    assertTrue(error.isFaulted());
    assertFalse(error.isCancelled());

    assertTrue(cancelled.isCompleted());
    assertFalse(cancelled.isFaulted());
    assertTrue(cancelled.isCancelled());
  }

  public void testSynchronousContinuation() {
    final Task<Integer> complete = Task.forResult(5);
    final Task<Integer> error = Task.forError(new RuntimeException());
    final Task<Integer> cancelled = Task.cancelled();

    complete.continueWith(new Continuation<Integer, Void>() {
      public Void then(Task<Integer> task) {
        assertEquals(complete, task);
        assertTrue(task.isCompleted());
        assertEquals(5, task.getResult().intValue());
        assertFalse(task.isFaulted());
        assertFalse(task.isCancelled());
        return null;
      }
    });

    error.continueWith(new Continuation<Integer, Void>() {
      public Void then(Task<Integer> task) {
        assertEquals(error, task);
        assertTrue(task.isCompleted());
        assertTrue(task.getError() instanceof RuntimeException);
        assertTrue(task.isFaulted());
        assertFalse(task.isCancelled());
        return null;
      }
    });

    cancelled.continueWith(new Continuation<Integer, Void>() {
      public Void then(Task<Integer> task) {
        assertEquals(cancelled, task);
        assertTrue(cancelled.isCompleted());
        assertFalse(cancelled.isFaulted());
        assertTrue(cancelled.isCancelled());
        return null;
      }
    });
  }

  public void testSynchronousChaining() {
    Task<Integer> first = Task.forResult(1);
    Task<Integer> second = first.continueWith(new Continuation<Integer, Integer>() {
      public Integer then(Task<Integer> task) {
        return 2;
      }
    });
    Task<Integer> third = second.continueWithTask(new Continuation<Integer, Task<Integer>>() {
      public Task<Integer> then(Task<Integer> task) {
        return Task.forResult(3);
      }
    });
    assertTrue(first.isCompleted());
    assertTrue(second.isCompleted());
    assertTrue(third.isCompleted());
    assertEquals(1, first.getResult().intValue());
    assertEquals(2, second.getResult().intValue());
    assertEquals(3, third.getResult().intValue());
  }

  public void testBackgroundCall() {
    runTaskTest(new Callable<Task<?>>() {
      public Task<?> call() throws Exception {
        return Task.callInBackground(new Callable<Integer>() {
          public Integer call() throws Exception {
            Thread.sleep(100);
            return 5;
          }
        }).continueWith(new Continuation<Integer, Void>() {
          public Void then(Task<Integer> task) {
            assertEquals(5, task.getResult().intValue());
            return null;
          }
        });
      }
    });
  }

  public void testBackgroundCallWaiting() throws Exception {
    Task<Integer> task = Task.callInBackground(new Callable<Integer>() {
      public Integer call() throws Exception {
        Thread.sleep(100);
        return 5;
      }
    });
    task.waitForCompletion();
    assertTrue(task.isCompleted());
    assertEquals(5, task.getResult().intValue());
  }

  public void testBackgroundCallWaitingOnError() throws Exception {
    Task<Integer> task = Task.callInBackground(new Callable<Integer>() {
      public Integer call() throws Exception {
        Thread.sleep(100);
        throw new RuntimeException();
      }
    });
    task.waitForCompletion();
    assertTrue(task.isCompleted());
    assertTrue(task.isFaulted());
  }

  public void testBackgroundCallWaitOnCancellation() throws Exception {
    Task<Integer> task = Task.callInBackground(new Callable<Integer>() {
      public Integer call() throws Exception {
        Thread.sleep(100);
        return 5;
      }
    }).continueWithTask(new Continuation<Integer, Task<Integer>>() {

      public Task<Integer> then(Task<Integer> task) {
        return Task.cancelled();
      }
    });
    task.waitForCompletion();
    assertTrue(task.isCompleted());
    assertTrue(task.isCancelled());
  }

  public void testBackgroundError() {
    runTaskTest(new Callable<Task<?>>() {
      public Task<?> call() throws Exception {
        return Task.callInBackground(new Callable<Integer>() {
          public Integer call() throws Exception {
            throw new IllegalStateException();
          }
        }).continueWith(new Continuation<Integer, Void>() {
          public Void then(Task<Integer> task) {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof IllegalStateException);
            return null;
          }
        });
      }
    });
  }

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

  public void testWhenAllSuccess() {
    runTaskTest(new Callable<Task<?>>() {
      @Override
      public Task<?> call() throws Exception {
        final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
        for (int i = 0; i < 20; i++) {
          Task<Void> task = Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              Thread.sleep((long) (Math.random() * 100));
              return null;
            }
          });
          tasks.add(task);
        }
        return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) {
            assertTrue(task.isCompleted());
            assertFalse(task.isFaulted());
            assertFalse(task.isCancelled());

            for (Task<Void> t : tasks) {
              assertTrue(t.isCompleted());
            }
            return null;
          }
        });
      }
    });
  }

  public void testWhenAllOneError() {
    final Exception error = new RuntimeException("This task failed.");

    runTaskTest(new Callable<Task<?>>() {
      @Override
      public Task<?> call() throws Exception {
        final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
        for (int i = 0; i < 20; i++) {
          final int number = i;
          Task<Void> task = Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              Thread.sleep((long) (Math.random() * 100));
              if (number == 10) {
                throw error;
              }
              return null;
            }
          });
          tasks.add(task);
        }
        return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) {
            assertTrue(task.isCompleted());
            assertTrue(task.isFaulted());
            assertFalse(task.isCancelled());

            assertFalse(task.getError() instanceof AggregateException);
            assertEquals(error, task.getError());

            for (Task<Void> t : tasks) {
              assertTrue(t.isCompleted());
            }
            return null;
          }
        });
      }
    });
  }

  public void testWhenAllTwoErrors() {
    final Exception error = new RuntimeException("This task failed.");

    runTaskTest(new Callable<Task<?>>() {
      @Override
      public Task<?> call() throws Exception {
        final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
        for (int i = 0; i < 20; i++) {
          final int number = i;
          Task<Void> task = Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              Thread.sleep((long) (Math.random() * 100));
              if (number == 10 || number == 11) {
                throw error;
              }
              return null;
            }
          });
          tasks.add(task);
        }
        return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) {
            assertTrue(task.isCompleted());
            assertTrue(task.isFaulted());
            assertFalse(task.isCancelled());

            assertTrue(task.getError() instanceof AggregateException);
            assertEquals(2, ((AggregateException) task.getError()).getErrors().size());
            assertEquals(error, ((AggregateException) task.getError()).getErrors().get(0));
            assertEquals(error, ((AggregateException) task.getError()).getErrors().get(1));

            for (Task<Void> t : tasks) {
              assertTrue(t.isCompleted());
            }
            return null;
          }
        });
      }
    });
  }

  public void testWhenAllCancel() {
    runTaskTest(new Callable<Task<?>>() {
      @Override
      public Task<?> call() throws Exception {
        final ArrayList<Task<Void>> tasks = new ArrayList<Task<Void>>();
        for (int i = 0; i < 20; i++) {
          final Task<Void>.TaskCompletionSource tcs = Task.create();

          final int number = i;
          Task.callInBackground(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
              Thread.sleep((long) (Math.random() * 100));
              if (number == 10) {
                tcs.setCancelled();
              } else {
                tcs.setResult(null);
              }
              return null;
            }
          });

          tasks.add(tcs.getTask());
        }
        return Task.whenAll(tasks).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) {
            assertTrue(task.isCompleted());
            assertFalse(task.isFaulted());
            assertTrue(task.isCancelled());

            for (Task<Void> t : tasks) {
              assertTrue(t.isCompleted());
            }
            return null;
          }
        });
      }
    });
  }

  public void testAsyncChaining() {
    runTaskTest(new Callable<Task<?>>() {
      public Task<?> call() throws Exception {
        final ArrayList<Integer> sequence = new ArrayList<Integer>();
        Task<Void> result = Task.forResult(null);
        for (int i = 0; i < 20; i++) {
          final int taskNumber = i;
          result = result.continueWithTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) {
              return Task.callInBackground(new Callable<Void>() {
                public Void call() throws Exception {
                  sequence.add(taskNumber);
                  return null;
                }
              });
            }
          });
        }
        result = result.continueWith(new Continuation<Void, Void>() {
          public Void then(Task<Void> task) {
            assertEquals(20, sequence.size());
            for (int i = 0; i < 20; i++) {
              assertEquals(i, sequence.get(i).intValue());
            }
            return null;
          }
        });
        return result;
      }
    });
  }

  public void testOnSuccess() {
    Continuation<Integer, Integer> continuation = new Continuation<Integer, Integer>() {
      public Integer then(Task<Integer> task) {
        return task.getResult().intValue() + 1;
      }
    };
    Task<Integer> complete = Task.forResult(5).onSuccess(continuation);
    Task<Integer> error = Task.<Integer> forError(new IllegalStateException()).onSuccess(
        continuation);
    Task<Integer> cancelled = Task.<Integer> cancelled().onSuccess(continuation);

    assertTrue(complete.isCompleted());
    assertEquals(6, complete.getResult().intValue());
    assertFalse(complete.isFaulted());
    assertFalse(complete.isCancelled());

    assertTrue(error.isCompleted());
    assertTrue(error.getError() instanceof RuntimeException);
    assertTrue(error.isFaulted());
    assertFalse(error.isCancelled());

    assertTrue(cancelled.isCompleted());
    assertFalse(cancelled.isFaulted());
    assertTrue(cancelled.isCancelled());
  }

  public void testOnSuccessTask() {
    Continuation<Integer, Task<Integer>> continuation = new Continuation<Integer, Task<Integer>>() {
      public Task<Integer> then(Task<Integer> task) {
        return Task.forResult(task.getResult().intValue() + 1);
      }
    };
    Task<Integer> complete = Task.forResult(5).onSuccessTask(continuation);
    Task<Integer> error = Task.<Integer> forError(new IllegalStateException()).onSuccessTask(
        continuation);
    Task<Integer> cancelled = Task.<Integer> cancelled().onSuccessTask(continuation);

    assertTrue(complete.isCompleted());
    assertEquals(6, complete.getResult().intValue());
    assertFalse(complete.isFaulted());
    assertFalse(complete.isCancelled());

    assertTrue(error.isCompleted());
    assertTrue(error.getError() instanceof RuntimeException);
    assertTrue(error.isFaulted());
    assertFalse(error.isCancelled());

    assertTrue(cancelled.isCompleted());
    assertFalse(cancelled.isFaulted());
    assertTrue(cancelled.isCancelled());
  }

  public void testContinueWhile() {
    final AtomicInteger count = new AtomicInteger(0);
    runTaskTest(new Callable<Task<?>>() {
      public Task<?> call() throws Exception {
        return Task.forResult(null).continueWhile(new Callable<Boolean>() {
          public Boolean call() throws Exception {
            return count.get() < 10;
          }
        }, new Continuation<Void, Task<Void>>() {
          public Task<Void> then(Task<Void> task) throws Exception {
            count.incrementAndGet();
            return null;
          }
        }).continueWith(new Continuation<Void, Void>() {
          public Void then(Task<Void> task) throws Exception {
            assertEquals(10, count.get());
            return null;
          }
        });
      }
    });
  }

  public void testContinueWhileAsync() {
    final AtomicInteger count = new AtomicInteger(0);
    runTaskTest(new Callable<Task<?>>() {
      public Task<?> call() throws Exception {
        return Task.forResult(null).continueWhile(new Callable<Boolean>() {
          public Boolean call() throws Exception {
            return count.get() < 10;
          }
        }, new Continuation<Void, Task<Void>>() {
          public Task<Void> then(Task<Void> task) throws Exception {
            count.incrementAndGet();
            return null;
          }
        }, Executors.newCachedThreadPool()).continueWith(new Continuation<Void, Void>() {
          public Void then(Task<Void> task) throws Exception {
            assertEquals(10, count.get());
            return null;
          }
        });
      }
    });
  }
  
  public void testStackTrackerWithContinueWithTaskSetError() {
    Task.enableStackTracking(true);

    /*
     * Add a set of function to track on the stack across multiple continuations.
     */
    class Funcs {
      public Task<Void> fooInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return barInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> barInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return bazInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> bazInBackground() {
        return Task.forError(new RuntimeException("An error to track."));
      }
    };
    final Funcs funcs = new Funcs();
    
    runTaskTest(new Callable<Task<?>>() {
      public Task<Void> call() throws Exception {
        return funcs.fooInBackground().continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof RuntimeException);
            RuntimeException error = (RuntimeException) task.getError();
            StackTraceElement[] stackTrace = error.getStackTrace();
            assertTrue(stackTrace.length > 0);
            boolean fooFound = false;
            boolean barFound = false;
            boolean bazFound = false;
            for (StackTraceElement line : stackTrace) {
              if ("fooInBackground".equals(line.getMethodName())) {
                fooFound = true;
              }
              if ("barInBackground".equals(line.getMethodName())) {
                barFound = true;
              }
              if ("bazInBackground".equals(line.getMethodName())) {
                bazFound = true;
              }
            }
            assertTrue(fooFound);
            assertTrue(barFound);
            assertTrue(bazFound);
            return null;
          }
        });
      }
    });
  }

  public void testStackTrackerWithContinueWithTaskThrows() {
    Task.enableStackTracking(true);

    /*
     * Add a set of function to track on the stack across multiple continuations.
     */
    class Funcs {
      public Task<Void> fooInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return barInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> barInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return bazInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> bazInBackground() {
        throw new RuntimeException("An error to track.");
      }
    };
    final Funcs funcs = new Funcs();
    
    runTaskTest(new Callable<Task<?>>() {
      public Task<Void> call() throws Exception {
        return funcs.fooInBackground().continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof RuntimeException);
            RuntimeException error = (RuntimeException) task.getError();
            StackTraceElement[] stackTrace = error.getStackTrace();
            assertTrue(stackTrace.length > 0);
            boolean fooFound = false;
            boolean barFound = false;
            boolean bazFound = false;
            for (StackTraceElement line : stackTrace) {
              if ("fooInBackground".equals(line.getMethodName())) {
                fooFound = true;
              }
              if ("barInBackground".equals(line.getMethodName())) {
                barFound = true;
              }
              if ("bazInBackground".equals(line.getMethodName())) {
                bazFound = true;
              }
            }
            assertTrue(fooFound);
            assertTrue(barFound);
            assertTrue(bazFound);
            return null;
          }
        });
      }
    });
  }

  public void testStackTrackerWithContinueWithThrows() {
    Task.enableStackTracking(true);
    
    /*
     * Add a set of function to track on the stack across multiple continuations.
     */
    class Funcs {
      public Task<Void> fooInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return barInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> barInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return bazInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> bazInBackground() {
        return Task.<Void> forResult(null).continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            throw new RuntimeException("An error to track.");
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
    };
    final Funcs funcs = new Funcs();
    
    runTaskTest(new Callable<Task<?>>() {
      public Task<Void> call() throws Exception {
        return funcs.fooInBackground().continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof RuntimeException);
            RuntimeException error = (RuntimeException) task.getError();
            StackTraceElement[] stackTrace = error.getStackTrace();
            assertTrue(stackTrace.length > 0);
            boolean fooFound = false;
            boolean barFound = false;
            boolean bazFound = false;
            for (StackTraceElement line : stackTrace) {
              if ("fooInBackground".equals(line.getMethodName())) {
                fooFound = true;
              }
              if ("barInBackground".equals(line.getMethodName())) {
                barFound = true;
              }
              if ("bazInBackground".equals(line.getMethodName())) {
                bazFound = true;
              }
            }
            assertTrue(fooFound);
            assertTrue(barFound);
            assertTrue(bazFound);
            return null;
          }
        });
      }
    });
  }

  public void testStackTrackerWithCallInBackgroundSetError() {
    Task.enableStackTracking(true);

    /*
     * Add a set of function to track on the stack across multiple continuations.
     */
    class Funcs {
      public Task<Void> fooInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return barInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> barInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return bazInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> bazInBackground() {
        return Task.callInBackground(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            return null;
          }
        }).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return Task.forError(new RuntimeException("An error to track."));          }
        });
      }
    };
    final Funcs funcs = new Funcs();
    
    runTaskTest(new Callable<Task<?>>() {
      public Task<Void> call() throws Exception {
        return funcs.fooInBackground().continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof RuntimeException);
            RuntimeException error = (RuntimeException) task.getError();
            StackTraceElement[] stackTrace = error.getStackTrace();
            assertTrue(stackTrace.length > 0);
            boolean fooFound = false;
            boolean barFound = false;
            boolean bazFound = false;
            for (StackTraceElement line : stackTrace) {
              if ("fooInBackground".equals(line.getMethodName())) {
                fooFound = true;
              }
              if ("barInBackground".equals(line.getMethodName())) {
                barFound = true;
              }
              if ("bazInBackground".equals(line.getMethodName())) {
                bazFound = true;
              }
            }
            assertTrue(fooFound);
            assertTrue(barFound);
            assertTrue(bazFound);
            return null;
          }
        });
      }
    });
  }

  public void testStackTrackerWithCallInBackgroundThrows() {
    Task.enableStackTracking(true);
    
    /*
     * Add a set of function to track on the stack across multiple continuations.
     */
    class Funcs {
      public Task<Void> fooInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return barInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> barInBackground() {
        return Task.<Void> forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
          @Override
          public Task<Void> then(Task<Void> task) throws Exception {
            return bazInBackground();
          }
        }, Task.BACKGROUND_EXECUTOR);
      }
      
      public Task<Void> bazInBackground() {
        return Task.callInBackground(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            throw new RuntimeException("An error to track.");
          }
        });
      }
    };
    final Funcs funcs = new Funcs();
    
    runTaskTest(new Callable<Task<?>>() {
      public Task<Void> call() throws Exception {
        return funcs.fooInBackground().continueWith(new Continuation<Void, Void>() {
          @Override
          public Void then(Task<Void> task) throws Exception {
            assertTrue(task.isFaulted());
            assertTrue(task.getError() instanceof RuntimeException);
            RuntimeException error = (RuntimeException) task.getError();
            StackTraceElement[] stackTrace = error.getStackTrace();
            assertTrue(stackTrace.length > 0);
            boolean fooFound = false;
            boolean barFound = false;
            boolean bazFound = false;
            for (StackTraceElement line : stackTrace) {
              if ("fooInBackground".equals(line.getMethodName())) {
                fooFound = true;
              }
              if ("barInBackground".equals(line.getMethodName())) {
                barFound = true;
              }
              if ("bazInBackground".equals(line.getMethodName())) {
                bazFound = true;
              }
            }
            assertTrue(fooFound);
            assertTrue(barFound);
            assertTrue(bazFound);
            return null;
          }
        });
      }
    });
  }}
