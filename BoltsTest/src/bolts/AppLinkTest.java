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

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.test.InstrumentationTestCase;
import android.util.Log;

import junit.framework.Assert;

import java.net.URL;

public class AppLinkTest extends InstrumentationTestCase {
  public void testSample() throws Exception {
    WebViewAppLinkResolver resolver = new WebViewAppLinkResolver(this.getInstrumentation().getTargetContext());
    Task<?> t = resolver.getAppLinkFromUrlInBackground(Uri.parse("http://fancy.com/things/250974783/Raindance-Royal-350-AIR-Shower-Head?utm=timeline_featured"));
    waitForTask(t);
  }

  private static <T> void waitForTask(final Task<T> task) throws InterruptedException {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      task.waitForCompletion();
      return;
    }
    final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Dispatch setting up the continuation so that it happens within the loop() call.
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        task.continueWith(new Continuation<T, Void>() {
          @Override
          public Void then(Task<T> task) throws Exception {
            mainHandler.sendMessageAtFrontOfQueue(new Message());
            return null;
          }
        });
      }
    });
    try {
      Looper.getMainLooper().loop();
    } catch (Exception e) {
      Log.d("Test", "Test");
    }
  }
}
