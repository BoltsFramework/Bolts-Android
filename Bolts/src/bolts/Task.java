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

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents the result of an asynchronous operation.
 * 
 * @param <TResult>
 *          The type of the result of the task.
 */
public class Task<TResult> {
  /**
   * An {@link java.util.concurrent.Executor} that executes tasks in parallel.
   */
  public static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool();

  /**
   * An {@link java.util.concurrent.Executor} that executes tasks in the current thread unless
   * the stack runs too deep, at which point it will delegate to {@link Task#BACKGROUND_EXECUTOR} in
   * order to trim the stack.
   */
  private static final Executor IMMEDIATE_EXECUTOR = new ImmediateExecutor();

  /**
   * An {@link java.util.concurrent.Executor} that executes tasks on the UI thread.
   */
  public static final Executor UI_THREAD_EXECUTOR = new UIThreadExecutor();

  private final Object lock = new Object();
  private boolean complete;
  private boolean cancelled;
  private TResult result;
  private Exception error;
  private List<Continuation<TResult, Void>> continuations;

  private Task() {
    continuations = new ArrayList<Continuation<TResult, Void>>();
  }

  /**
   * Creates a TaskCompletionSource that orchestrates a Task. This allows the creator of a task to
   * be solely responsible for its completion.
   *
   * @return A new TaskCompletionSource.
   */
  public static <TResult> Task<TResult>.TaskCompletionSource create() {
    Task<TResult> task = new Task<TResult>();
    return task.new TaskCompletionSource();
  }

  /**
   * @return {@code true} if the task completed (has a result, an error, or was cancelled.
   *         {@code false} otherwise.
   */
  public boolean isCompleted() {
    synchronized (lock) {
      return complete;
    }
  }

  /**
   * @return {@code true} if the task was cancelled, {@code false} otherwise.
   */
  public boolean isCancelled() {
    synchronized (lock) {
      return cancelled;
    }
  }

  /**
   * @return {@code true} if the task has an error, {@code false} otherwise.
   */
  public boolean isFaulted() {
    synchronized (lock) {
      return error != null;
    }
  }

  /**
   * @return The result of the task, if set. {@code null} otherwise.
   */
  public TResult getResult() {
    synchronized (lock) {
      return result;
    }
  }

  /**
   * @return The error for the task, if set. {@code null} otherwise.
   */
  public Exception getError() {
    synchronized (lock) {
      return error;
    }
  }

  /**
   * Blocks until the task is complete.
   */
  public void waitForCompletion() throws InterruptedException {
    synchronized (lock) {
      if (!isCompleted()) {
        lock.wait();
      }
    }
  }

  /**
   * Creates a completed task with the given value.
   */
  public static <TResult> Task<TResult> forResult(TResult value) {
    Task<TResult>.TaskCompletionSource tcs = Task.<TResult> create();
    tcs.setResult(value);
    return tcs.getTask();
  }

  /**
   * Creates a faulted task with the given error.
   */
  public static <TResult> Task<TResult> forError(Exception error) {
    Task<TResult>.TaskCompletionSource tcs = Task.<TResult> create();
    tcs.setError(error);
    return tcs.getTask();
  }

  /**
   * Creates a cancelled task.
   */
  public static <TResult> Task<TResult> cancelled() {
    Task<TResult>.TaskCompletionSource tcs = Task.<TResult> create();
    tcs.setCancelled();
    return tcs.getTask();
  }

  /**
   * Makes a fluent cast of a Task's result possible, avoiding an extra continuation just to cast
   * the type of the result.
   */
  public <TOut> Task<TOut> cast() {
    @SuppressWarnings("unchecked")
    Task<TOut> task = (Task<TOut>) this;
    return task;
  }

  /**
   * Turns a Task<T> into a Task<Void>, dropping any result.
   */
  public Task<Void> makeVoid() {
    return this.continueWithTask(new Continuation<TResult, Task<Void>>() {
      @Override
      public Task<Void> then(Task<TResult> task) throws Exception {
        if (task.isCancelled()) {
          return Task.cancelled();
        }
        if (task.isFaulted()) {
          return Task.forError(task.getError());
        }
        return Task.forResult(null);
      }
    });
  }

  /**
   * Invokes the callable on a background thread, returning a Task to represent the operation.
   */
  public static <TResult> Task<TResult> callInBackground(Callable<TResult> callable) {
    return call(callable, BACKGROUND_EXECUTOR);
  }

  /**
   * Invokes the callable using the given executor, returning a Task to represent the operation.
   */
  public static <TResult> Task<TResult> call(final Callable<TResult> callable, Executor executor) {
    final Task<TResult>.TaskCompletionSource tcs = Task.<TResult> create();
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          tcs.setResult(callable.call());
        } catch (Exception e) {
          tcs.setError(e);
        }
      }
    });
    return tcs.getTask();
  }

  /**
   * Invokes the callable on the current thread, producing a Task.
   */
  public static <TResult> Task<TResult> call(final Callable<TResult> callable) {
    return call(callable, IMMEDIATE_EXECUTOR);
  }

  /**
   * Creates a task that completes when all of the provided tasks are complete.
   */
  public static Task<Void> whenAll(Collection<? extends Task<?>> tasks) {
    if (tasks.size() == 0) {
      return Task.forResult(null);
    }

    final Task<Void>.TaskCompletionSource allFinished = Task.<Void> create();
    final ArrayList<Exception> errors = new ArrayList<Exception>();
    final Object errorLock = new Object();
    final AtomicInteger count = new AtomicInteger(tasks.size());
    final AtomicBoolean isCancelled = new AtomicBoolean(false);

    for (Task<?> task : tasks) {
      @SuppressWarnings("unchecked")
      Task<Object> t = (Task<Object>) task;
      t.continueWith(new Continuation<Object, Void>() {
        @Override
        public Void then(Task<Object> task) {
          if (task.isFaulted()) {
            synchronized (errorLock) {
              errors.add(task.getError());
            }
          }

          if (task.isCancelled()) {
            isCancelled.set(true);
          }

          if (count.decrementAndGet() == 0) {
            if (errors.size() != 0) {
              if (errors.size() == 1) {
                allFinished.setError(errors.get(0));
              } else {
                allFinished.setError(new AggregateException(errors));
              }
            } else if (isCancelled.get()) {
              allFinished.setCancelled();
            } else {
              allFinished.setResult(null);
            }
          }
          return null;
        }
      });
    }

    return allFinished.getTask();
  }

  /**
   * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is
   * a task continuation.
   */
  public Task<Void> continueWhile(Callable<Boolean> predicate,
      Continuation<Void, Task<Void>> continuation) {
    return continueWhile(predicate, continuation, IMMEDIATE_EXECUTOR);
  }

  /**
   * Continues a task with the equivalent of a Task-based while loop, where the body of the loop is
   * a task continuation.
   */
  public Task<Void> continueWhile(final Callable<Boolean> predicate,
      final Continuation<Void, Task<Void>> continuation, final Executor executor) {
    final Capture<Continuation<Void, Task<Void>>> predicateContinuation =
        new Capture<Continuation<Void, Task<Void>>>();
    predicateContinuation.set(new Continuation<Void, Task<Void>>() {
      @Override
      public Task<Void> then(Task<Void> task) throws Exception {
        if (predicate.call()) {
          return Task.<Void> forResult(null).onSuccessTask(continuation, executor)
              .onSuccessTask(predicateContinuation.get(), executor);
        }
        return Task.forResult(null);
      }
    });
    return makeVoid().continueWithTask(predicateContinuation.get(), executor);
  }

  /**
   * Adds a continuation that will be scheduled using the executor, returning a new task that
   * completes after the continuation has finished running. This allows the continuation to be
   * scheduled on different thread.
   */
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      final Continuation<TResult, TContinuationResult> continuation, final Executor executor) {
    boolean completed = false;
    final Task<TContinuationResult>.TaskCompletionSource tcs = Task.<TContinuationResult> create();
    synchronized (lock) {
      completed = this.isCompleted();
      if (!completed) {
        this.continuations.add(new Continuation<TResult, Void>() {
          @Override
          public Void then(Task<TResult> task) {
            completeImmediately(tcs, continuation, task, executor);
            return null;
          }
        });
      }
    }
    if (completed) {
      completeImmediately(tcs, continuation, this, executor);
    }
    return tcs.getTask();
  }

  /**
   * Adds a synchronous continuation to this task, returning a new task that completes after the
   * continuation has finished running.
   */
  public <TContinuationResult> Task<TContinuationResult> continueWith(
      Continuation<TResult, TContinuationResult> continuation) {
    return continueWith(continuation, IMMEDIATE_EXECUTOR);
  }

  /**
   * Adds an Task-based continuation to this task that will be scheduled using the executor,
   * returning a new task that completes after the task returned by the continuation has completed.
   */
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      final Continuation<TResult, Task<TContinuationResult>> continuation, final Executor executor) {
    boolean completed = false;
    final Task<TContinuationResult>.TaskCompletionSource tcs = Task.<TContinuationResult> create();
    synchronized (lock) {
      completed = this.isCompleted();
      if (!completed) {
        this.continuations.add(new Continuation<TResult, Void>() {
          @Override
          public Void then(Task<TResult> task) {
            completeAfterTask(tcs, continuation, task, executor);
            return null;
          }
        });
      }
    }
    if (completed) {
      completeAfterTask(tcs, continuation, this, executor);
    }
    return tcs.getTask();
  }

  /**
   * Adds an asynchronous continuation to this task, returning a new task that completes after the
   * task returned by the continuation has completed.
   */
  public <TContinuationResult> Task<TContinuationResult> continueWithTask(
      Continuation<TResult, Task<TContinuationResult>> continuation) {
    return continueWithTask(continuation, IMMEDIATE_EXECUTOR);
  }

  /**
   * Runs a continuation when a task completes successfully, forwarding along errors or
   * cancellation.
   */
  public <TContinuationResult> Task<TContinuationResult> onSuccess(
      final Continuation<TResult, TContinuationResult> continuation, Executor executor) {
    return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
      @Override
      public Task<TContinuationResult> then(Task<TResult> task) {
        if (task.isFaulted()) {
          return Task.<TContinuationResult> forError(task.getError());
        } else if (task.isCancelled()) {
          return Task.<TContinuationResult> cancelled();
        } else {
          return task.continueWith(continuation);
        }
      }
    }, executor);
  }

  /**
   * Runs a continuation when a task completes successfully, forwarding along errors or
   * cancellation.
   */
  public <TContinuationResult> Task<TContinuationResult> onSuccess(
      final Continuation<TResult, TContinuationResult> continuation) {
    return onSuccess(continuation, IMMEDIATE_EXECUTOR);
  }

  /**
   * Runs a continuation when a task completes successfully, forwarding along errors or
   * cancellation.
   */
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      final Continuation<TResult, Task<TContinuationResult>> continuation, Executor executor) {
    return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
      @Override
      public Task<TContinuationResult> then(Task<TResult> task) {
        if (task.isFaulted()) {
          return Task.<TContinuationResult> forError(task.getError());
        } else if (task.isCancelled()) {
          return Task.<TContinuationResult> cancelled();
        } else {
          return task.continueWithTask(continuation);
        }
      }
    }, executor);
  }

  /**
   * Runs a continuation when a task completes successfully, forwarding along errors or
   * cancellation.
   */
  public <TContinuationResult> Task<TContinuationResult> onSuccessTask(
      final Continuation<TResult, Task<TContinuationResult>> continuation) {
    return onSuccessTask(continuation, IMMEDIATE_EXECUTOR);
  }

  /**
   * Handles the non-async (i.e. the continuation doesn't return a Task) continuation case, passing
   * the results of the given Task through to the given continuation and using the results of that
   * call to set the result of the TaskContinuationSource.
   *
   * @param tcs
   *          The TaskContinuationSource that will be orchestrated by this call.
   * @param continuation
   *          The non-async continuation.
   * @param task
   *          The task being completed.
   * @param executor
   *          The executor to use when running the continuation (allowing the continuation to be
   *          scheduled on a different thread).
   */
  private static <TContinuationResult, TResult> void completeImmediately(
      final Task<TContinuationResult>.TaskCompletionSource tcs,
      final Continuation<TResult, TContinuationResult> continuation, final Task<TResult> task,
      Executor executor) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          TContinuationResult result = continuation.then(task);
          tcs.setResult(result);
        } catch (Exception e) {
          tcs.setError(e);
        }
      }
    });
  }

  /**
   * Handles the async (i.e. the continuation does return a Task) continuation case, passing the
   * results of the given Task through to the given continuation to get a new Task. The
   * TaskCompletionSource's results are only set when the new Task has completed, unwrapping the
   * results of the task returned by the continuation.
   *
   * @param tcs
   *          The TaskContinuationSource that will be orchestrated by this call.
   * @param continuation
   *          The async continuation.
   * @param task
   *          The task being completed.
   * @param executor
   *          The executor to use when running the continuation (allowing the continuation to be
   *          scheduled on a different thread).
   */
  private static <TContinuationResult, TResult> void completeAfterTask(
      final Task<TContinuationResult>.TaskCompletionSource tcs,
      final Continuation<TResult, Task<TContinuationResult>> continuation,
      final Task<TResult> task, final Executor executor) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        try {
          Task<TContinuationResult> result = continuation.then(task);
          if (result == null) {
            tcs.setResult(null);
          } else {
            result.continueWith(new Continuation<TContinuationResult, Void>() {
              @Override
              public Void then(Task<TContinuationResult> task) {
                if (task.isCancelled()) {
                  tcs.setCancelled();
                } else if (task.isFaulted()) {
                  tcs.setError(task.getError());
                } else {
                  tcs.setResult(task.getResult());
                }
                return null;
              }
            });
          }
        } catch (Exception e) {
          tcs.setError(e);
        }
      }
    });
  }

  private void runContinuations() {
    synchronized (lock) {
      for (Continuation<TResult, ?> continuation : continuations) {
        try {
          continuation.then(this);
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      continuations = null;
    }
  }

  /**
   * Allows safe orchestration of a task's completion, preventing the consumer from prematurely
   * completing the task. Essentially, it represents the producer side of a Task<TResult>, providing
   * access to the consumer side through the getTask() method while isolating the Task's completion
   * mechanisms from the consumer.
   */
  public class TaskCompletionSource {
    private TaskCompletionSource() {
    }

    /**
     * @return the Task associated with this TaskCompletionSource.
     */
    public Task<TResult> getTask() {
      return Task.this;
    }

    /**
     * Sets the cancelled flag on the Task if the Task hasn't already been completed.
     */
    public boolean trySetCancelled() {
      synchronized (lock) {
        if (complete) {
          return false;
        }
        complete = true;
        cancelled = true;
        lock.notifyAll();
        runContinuations();
        return true;
      }
    }

    /**
     * Sets the result on the Task if the Task hasn't already been completed.
     */
    public boolean trySetResult(TResult result) {
      synchronized (lock) {
        if (complete) {
          return false;
        }
        complete = true;
        Task.this.result = result;
        lock.notifyAll();
        runContinuations();
        return true;
      }
    }

    /**
     * Sets the error on the Task if the Task hasn't already been completed.
     */
    public boolean trySetError(Exception error) {
      synchronized (lock) {
        if (complete) {
          return false;
        }
        complete = true;
        Task.this.error = error;
        lock.notifyAll();
        runContinuations();
        return true;
      }
    }

    /**
     * Sets the cancelled flag on the task, throwing if the Task has already been completed.
     */
    public void setCancelled() {
      if (!trySetCancelled()) {
        throw new IllegalStateException("Cannot cancel a completed task.");
      }
    }

    /**
     * Sets the result of the Task, throwing if the Task has already been completed.
     */
    public void setResult(TResult result) {
      if (!trySetResult(result)) {
        throw new IllegalStateException("Cannot set the result of a completed task.");
      }
    }

    /**
     * Sets the error of the Task, throwing if the Task has already been completed.
     */
    public void setError(Exception error) {
      if (!trySetError(error)) {
        throw new IllegalStateException("Cannot set the error on a completed task.");
      }
    }
  }

  /**
   * An {@link java.util.concurrent.Executor} that runs tasks on the UI thread.
   */
  private static class UIThreadExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
      new Handler(Looper.getMainLooper()).post(command);
    }
  };

  /**
   * An {@link java.util.concurrent.Executor} that runs a runnable inline (rather than scheduling it
   * on a thread pool) as long as the recursion depth is less than MAX_DEPTH. If the executor has
   * recursed too deeply, it will instead delegate to the {@link Task#BACKGROUND_EXECUTOR} in order
   * to trim the stack.
   */
  private static class ImmediateExecutor implements Executor {
    private static final int MAX_DEPTH = 15;
    private ThreadLocal<Integer> executionDepth = new ThreadLocal<Integer>();

    /**
     * Increments the depth.
     *
     * @return the new depth value.
     */
    private int incrementDepth() {
      Integer oldDepth = executionDepth.get();
      if (oldDepth == null) {
        oldDepth = 0;
      }
      int newDepth = oldDepth + 1;
      executionDepth.set(newDepth);
      return newDepth;
    }

    /**
     * Decrements the depth.
     *
     * @return the new depth value.
     */
    private int decrementDepth() {
      Integer oldDepth = executionDepth.get();
      if (oldDepth == null) {
        oldDepth = 0;
      }
      int newDepth = oldDepth - 1;
      if (newDepth == 0) {
        executionDepth.remove();
      } else {
        executionDepth.set(newDepth);
      }
      return newDepth;
    }

    @Override
    public void execute(Runnable command) {
      int depth = incrementDepth();
      try {
        if (depth <= MAX_DEPTH) {
          command.run();
        } else {
          BACKGROUND_EXECUTOR.execute(command);
        }
      } finally {
        decrementDepth();
      }
    }
  };
}
