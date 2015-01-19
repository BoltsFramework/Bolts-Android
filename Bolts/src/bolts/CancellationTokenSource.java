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

/**
 * Signals to a {@link CancellationToken} that it should be canceled. To create a
 * {@code CancellationToken} first create a {@code CancellationTokenSource} then call
 * {@link #getToken()} to retrieve the token for the source.
 *
 * @see CancellationToken
 * @see CancellationTokenSource#getToken()
 */
public class CancellationTokenSource {

  private final CancellationToken token;

  /**
   * Create a new {@code CancellationTokenSource}.
   */
  public CancellationTokenSource() {
    token = new CancellationToken();
  }

  /**
   * @return {@code true} if cancellation has been requested for this {@code CancellationTokenSource}.
   */
  public boolean isCancellationRequested() {
    return token.isCancellationRequested();
  }

  /**
   * @return the token that can be passed to asynchronous method to control cancellation.
   */
  public CancellationToken getToken() {
    return token;
  }

  /**
   * Cancels the token if it has not already been cancelled.
   */
  public void cancel() {
    token.tryCancel();
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "%s@%s[cancellationRequested=%s]",
        getClass().getName(),
        Integer.toHexString(hashCode()),
        Boolean.toString(isCancellationRequested()));
  }
}
