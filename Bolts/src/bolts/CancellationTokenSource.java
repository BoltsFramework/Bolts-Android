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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Signals to a {@link CancellationToken} that it should be canceled. To create a
 * {@code CancellationToken} first create a {@code CancellationTokenSource} then call
 * {@link #getToken()} to retrieve the token for the source.
 *
 * @see CancellationToken
 * @see CancellationTokenSource#getToken()
 */
public class CancellationTokenSource implements Closeable {

  private final Object lock = new Object();
  private final List<CancellationTokenRegistration> registrations = new ArrayList<>();
  private boolean cancellationRequested;
  private boolean closed;

  /**
   * Create a new {@code CancellationTokenSource}.
   */
  public CancellationTokenSource() {
  }

  /**
   * @return {@code true} if cancellation has been requested for this {@code CancellationTokenSource}.
   */
  public boolean isCancellationRequested() {
    synchronized (lock) {
      throwIfClosed();
      return cancellationRequested;
    }
  }

  /**
   * @return the token that can be passed to asynchronous method to control cancellation.
   */
  public CancellationToken getToken() {
    synchronized (lock) {
      throwIfClosed();
      return new CancellationToken(this);
    }
  }

  /**
   * Cancels the token if it has not already been cancelled.
   */
  public void cancel() {
    List<CancellationTokenRegistration> registrations;
    synchronized (lock) {
      throwIfClosed();
      if (cancellationRequested) {
        return;
      }
      cancellationRequested = true;
      registrations = new ArrayList<>(this.registrations);
    }
    notifyListeners(registrations);
  }

  /**
   * Releases all resources associated with this {@code CancellationTokenSource}.
   */
  @Override
  public void close() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      for (CancellationTokenRegistration registration : registrations) {
        registration.close();
      }
      registrations.clear();
      closed = true;
    }
  }

  /* package */ CancellationTokenRegistration register(Runnable action) {
    CancellationTokenRegistration ctr;
    synchronized (lock) {
      throwIfClosed();

      ctr = new CancellationTokenRegistration(this, action);
      if (cancellationRequested) {
        ctr.runAction();
      } else {
        registrations.add(ctr);
      }
    }
    return ctr;
  }

  /**
   * @throws CancellationException if this token has had cancellation requested.
   * May be used to stop execution of a thread or runnable.
   */
  /* package */ void throwIfCancellationRequested() throws CancellationException {
    synchronized (lock) {
      throwIfClosed();
      if (cancellationRequested) {
        throw new CancellationException();
      }
    }
  }

  /* package */ void unregister(CancellationTokenRegistration registration) {
    synchronized (lock) {
      throwIfClosed();
      registrations.remove(registration);
    }
  }

  // This method makes no attempt to perform any synchronization or state checks itself and once
  // invoked will notify all runnables unconditionally. As such if you require the notification event
  // to be synchronized with state changes you should provide external synchronization.
  // If this is invoked without external synchronization there is a probability the token becomes
  // cancelled concurrently.
  private void notifyListeners(List<CancellationTokenRegistration> registrations) {
    for (CancellationTokenRegistration registration : registrations) {
      registration.runAction();
    }
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "%s@%s[cancellationRequested=%s]",
        getClass().getName(),
        Integer.toHexString(hashCode()),
        Boolean.toString(isCancellationRequested()));
  }

  // This method makes no attempt to perform any synchronization itself - you should ensure
  // accesses to this method are synchronized if you want to ensure correct behaviour in the
  // face of a concurrent invocation of the close method.
  private void throwIfClosed() {
    if (closed) {
      throw new IllegalStateException("Object already closed");
    }
  }
}
