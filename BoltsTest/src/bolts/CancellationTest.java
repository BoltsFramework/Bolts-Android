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

import android.test.InstrumentationTestCase;

import java.util.Objects;
import java.util.concurrent.CancellationException;

public class CancellationTest extends InstrumentationTestCase {
  public void testTokenIsCancelled() {
    CancellationTokenSource cts = new CancellationTokenSource();
    CancellationToken token = cts.getToken();

    assertFalse(token.isCancellationRequested());

    cts.cancel();

    assertTrue(token.isCancellationRequested());
  }

  public void testTokenThrowsWhenCancelled() {
    CancellationTokenSource cts = new CancellationTokenSource();
    CancellationToken token = cts.getToken();

    try {
      token.throwIfCancellationRequested();
    } catch (CancellationException e) {
      fail("Token has not been cancelled yet, " + CancellationException.class.getSimpleName()
          + " should not be thrown");
    }

    cts.cancel();

    try {
      token.throwIfCancellationRequested();
      fail(CancellationException.class.getSimpleName() + " should be thrown");
    } catch (CancellationException e) {
      // Do nothing
    }
  }

  public void testTokenCallsRegisteredActionWhenCancelled() {
    CancellationTokenSource cts = new CancellationTokenSource();
    CancellationToken token = cts.getToken();
    final Capture<Object> result = new Capture<>();

    token.register(new Runnable() {
      @Override
      public void run() {
        result.set("Run!");
      }
    });

    assertNull(result.get());

    cts.cancel();

    assertNotNull(result.get());
  }

  public void testCancelledTokenCallsRegisteredActionImmediately() {
    CancellationTokenSource cts = new CancellationTokenSource();
    CancellationToken token = cts.getToken();
    final Capture<Object> result = new Capture<>();

    cts.cancel();

    token.register(new Runnable() {
      @Override
      public void run() {
        result.set("Run!");
      }
    });

    assertNotNull(result.get());
  }

  public void testTokenDoesNotCallUnregisteredAction() {
    CancellationTokenSource cts = new CancellationTokenSource();
    CancellationToken token = cts.getToken();
    final Capture<Object> result1 = new Capture<>();
    final Capture<Object> result2 = new Capture<>();

    CancellationTokenRegistration registration1 = token.register(new Runnable() {
      @Override
      public void run() {
        result1.set("Run!");
      }
    });
    token.register(new Runnable() {
      @Override
      public void run() {
        result2.set("Run!");
      }
    });

    registration1.close();

    cts.cancel();

    assertNull(result1.get());
    assertNotNull(result2.get());
  }
}
