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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MeasurementEventTest extends InstrumentationTestCase {

  public void testGeneralMeasurementEventsBroadcast() throws Exception {
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
    i.putExtra("foo", "bar");
    ArrayList<String> arr = new ArrayList<String>();
    arr.add("foo2");
    arr.add("bar2");
    i.putExtra("foobar", arr);
    Map<String, String> other = new HashMap<String, String>();
    other.put("yetAnotherFoo", "yetAnotherBar");

    final CountDownLatch lock = new CountDownLatch(1);
    final String[] receivedStrings = new String[5];
    LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getInstrumentation().getTargetContext());
    manager.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String eventName = intent.getStringExtra("event_name");
            Bundle eventArgs = intent.getBundleExtra("event_args");
            receivedStrings[0] = eventName;
            receivedStrings[1] = eventArgs.getString("foo");
            receivedStrings[2] = eventArgs.getString("foobar");
            receivedStrings[3] = eventArgs.getString("yetAnotherFoo");
            receivedStrings[4] = eventArgs.getString("intentData");
            lock.countDown();
          }
        },
        new IntentFilter("com.parse.bolts.measurement_event")
    );

    MeasurementEvent.sendBroadcastEvent(getInstrumentation().getTargetContext(), "myEventName", i, other);
    lock.await(2000, TimeUnit.MILLISECONDS);

    assertEquals("myEventName", receivedStrings[0]);
    assertEquals("bar", receivedStrings[1]);
    assertEquals((new JSONArray(arr)).toString(), receivedStrings[2]);
    assertEquals("yetAnotherBar", receivedStrings[3]);
    assertEquals("http://www.example.com", receivedStrings[4]);
  }

}
