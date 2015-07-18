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
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AppLinkTest extends InstrumentationTestCase {

  public void testSimpleIntent() throws Exception {
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));

    Assert.assertEquals(i.getData(), AppLinks.getTargetUrl(i));
    Assert.assertNull(AppLinks.getAppLinkData(i));
    Assert.assertNull(AppLinks.getAppLinkExtras(i));
  }

  public void testSimpleIntentWithAppLink() throws Exception {
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
    Bundle appLinkData = new Bundle();
    appLinkData.putString("target_url", "http://www.example2.com");
    appLinkData.putInt("version", 1);
    Bundle extras = new Bundle();
    extras.putString("foo", "bar");
    appLinkData.putBundle("extras", extras);
    i.putExtra("al_applink_data", appLinkData);

    Assert.assertEquals(Uri.parse("http://www.example2.com"), AppLinks.getTargetUrl(i));
    Assert.assertNotNull(AppLinks.getAppLinkData(i));
    Assert.assertNotNull(AppLinks.getAppLinkExtras(i));
    Assert.assertEquals("bar", AppLinks.getAppLinkExtras(i).getString("foo"));
  }

  public void testAppLinkNavInEventBroadcast() throws Exception {
    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
    Bundle appLinkData = new Bundle();
    appLinkData.putString("target_url", "http://www.example2.com");
    Bundle appLinkRefererData = new Bundle();
    appLinkRefererData.putString("url", "referer://");
    appLinkRefererData.putString("app_name", "Referrer App");
    appLinkRefererData.putString("package", "com.bolts.referrer");
    appLinkData.putBundle("referer_app_link", appLinkRefererData);
    Bundle applinkExtras = new Bundle();
    applinkExtras.putString("token", "a_token");
    appLinkData.putBundle("extras", applinkExtras);
    i.putExtra("al_applink_data", appLinkData);

    final CountDownLatch lock = new CountDownLatch(1);
    final String[] receivedStrings = new String[7];
    LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getInstrumentation().getTargetContext());
    manager.registerReceiver(
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String eventName = intent.getStringExtra("event_name");
            Bundle eventArgs = intent.getBundleExtra("event_args");
            receivedStrings[0] = eventName;
            receivedStrings[1] = eventArgs.getString("targetURL");
            receivedStrings[2] = eventArgs.getString("inputURL");
            receivedStrings[3] = eventArgs.getString("refererURL");
            receivedStrings[4] = eventArgs.getString("refererAppName");
            receivedStrings[5] = eventArgs.getString("extras/token");
            receivedStrings[6] = eventArgs.getString("sourceApplication");
            lock.countDown();
          }
        },
        new IntentFilter("com.parse.bolts.measurement_event")
    );

    Uri targetUrl = AppLinks.getTargetUrlFromInboundIntent(getInstrumentation().getTargetContext(), i);
    lock.await(2000, TimeUnit.MILLISECONDS);

    assertEquals("al_nav_in", receivedStrings[0]);
    assertEquals("http://www.example2.com", receivedStrings[1]);
    assertEquals("http://www.example.com", receivedStrings[2]);
    assertEquals("referer://", receivedStrings[3]);
    assertEquals("Referrer App", receivedStrings[4]);
    assertEquals("a_token", receivedStrings[5]);
    assertEquals("com.bolts.referrer", receivedStrings[6]);
  }
}
