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

import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Propagates notification that operations should be canceled.
 * <p/>
 * Create an instance of {@code CancellationTokenSource} and pass the token returned from
 * {@code CancellationTokenSource#getToken()} to the asynchronous operation(s).
 * Call {@code CancellationTokenSource#cancel()} to cancel the operations.
 * <p/>
 * A {@code CancellationToken} can only be cancelled once - it should not be passed to future operations
 * once cancelled.
 *
 * @see CancellationTokenSource
 * @see CancellationTokenSource#getToken()
 * @see CancellationTokenSource#cancel()
 */
public class CancellationToken {

  private final Object lock = new Object();
  private boolean cancellationRequested;

  /* package */ CancellationToken() {
  }

  /**
   * @return {@code true} if the cancellation was requested from the source, {@code false} otherwise.
   */
  public boolean isCancellationRequested() {
    synchronized (lock) {
      return cancellationRequested;
    }
  }

  /**
   * @throws CancellationException if this token has had cancellation requested.
   * May be used to stop execution of a thread or runnable.
   */
  public void throwIfCancellationRequested() throws CancellationException {
    synchronized (lock) {
      if (cancellationRequested) {
        throw new CancellationException();
      }
    }
  }

  /* package */ boolean tryCancel() {
    synchronized (lock) {
      if (cancellationRequested) {
        return false;
      }

      cancellationRequested = true;
    }
    return true;
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "%s@%s[cancellationRequested=%s]",
        getClass().getName(),
        Integer.toHexString(hashCode()),
        Boolean.toString(cancellationRequested));
  }
}
