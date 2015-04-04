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

/**
 * Represents a callback delegate that has been registered with a {@link CancellationToken}.
 *
 * @see CancellationToken#register(Runnable)
 */
public class CancellationTokenRegistration implements Closeable {

  private final Object lock = new Object();
  private CancellationTokenSource tokenSource;
  private Runnable action;
  private boolean closed;

  /* package */ CancellationTokenRegistration(CancellationTokenSource tokenSource, Runnable action) {
    this.tokenSource = tokenSource;
    this.action = action;
  }

  /**
   * Unregisters the callback runnable from the cancellation token.
   */
  @Override
  public void close() {
    synchronized (lock) {
      if (closed) {
        return;
      }

      closed = true;
      tokenSource.unregister(this);
      tokenSource = null;
      action = null;
    }
  }

  /* package */ void runAction() {
    synchronized (lock) {
      throwIfClosed();
      action.run();
      close();
    }
  }

  private void throwIfClosed() {
    if (closed) {
      throw new IllegalStateException("Object already closed");
    }
  }

}
