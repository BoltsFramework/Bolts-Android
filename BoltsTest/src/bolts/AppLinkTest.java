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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AppLinkTest extends InstrumentationTestCase {
  private List<Intent> openedIntents;
  private Context activityInterceptor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    openedIntents = new ArrayList<Intent>();
    activityInterceptor = new ContextWrapper(getInstrumentation().getTargetContext()) {
      @Override
      public void startActivity(Intent intent) {
        openedIntents.add(intent);
      }
    };
  }

  private void waitForTask(Task<?> t) throws InterruptedException {
    t.waitForCompletion();
    if (t.isFaulted()) {
      throw new RuntimeException(t.getError());
    }
  }

  /**
   * A helper method to get an HTML string with pre-populated meta tags.
   * values should contain pairs of "property" and "content" values to inject into
   * the meta tags.
   */
  private String getHtmlWithMetaTags(String... values) {
    StringBuilder sb = new StringBuilder("<html><head>");
    for (int i = 0; i < values.length; i += 2) {
      sb.append("<meta property=\"");
      sb.append(values[i]);
      sb.append("\"");
      if (i + 1 < values.length && values[i + 1] != null) {
        sb.append(" content=\"");
        sb.append(values[i + 1]);
        sb.append("\"");
      }
      sb.append(">");
    }
    sb.append("</head><body>Hello, world!</body></html>");
    return sb.toString();
  }

  private Uri getURLForData(String data) throws IOException {
    File result = File.createTempFile("temp",
            ".html",
            getInstrumentation().getTargetContext().getCacheDir());
    PrintWriter writer = new PrintWriter(result);
    writer.write(data);
    writer.close();
    result.deleteOnExit();
    return Uri.parse(result.toURI().toString());
  }

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

  public void testWebViewSimpleAppLinkParsing() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testWebViewAppLinkParsingFailure() throws Exception {
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(Uri.parse("http://badurl"));
    task.waitForCompletion();
    Assert.assertNotNull(task.getError());
  }

  public void testWebViewSimpleAppLinkParsingZeroShouldFallback() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity",
            "al:web:should_fallback", "0");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertNull(link.getWebUrl());
  }

  public void testWebViewSimpleAppLinkParsingFalseShouldFallback() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity",
            "al:web:should_fallback", "fAlse"); // case insensitive
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertNull(link.getWebUrl());
  }

  public void testWebViewSimpleAppLinkParsingWithWebUrl() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity",
            "al:web:url", "http://www.example.com");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertEquals(Uri.parse("http://www.example.com"), link.getWebUrl());
  }

  public void testWebViewVersionedAppLinkParsing() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity",

            "al:android", null,
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts2",
            "al:android:package", "com.bolts2",
            "al:android:class", "com.bolts.BoltsActivity2");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(2, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());

    target = link.getTargets().get(1);
    Assert.assertEquals(Uri.parse("bolts2://"), target.getUrl());
    Assert.assertEquals("Bolts2", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity2", target.getClassName());
    Assert.assertEquals("com.bolts2", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testWebViewVersionedAppLinkParsingOnlyPackages() throws Exception {
    String html = getHtmlWithMetaTags("al:android:package", "com.bolts",
            "al:android:package", "com.bolts2");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(2, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals("com.bolts", target.getPackageName());

    target = link.getTargets().get(1);
    Assert.assertEquals("com.bolts2", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testWebViewVersionedAppLinkParsingPackagesAndNames() throws Exception {
    String html = getHtmlWithMetaTags("al:android:package", "com.bolts",
            "al:android:package", "com.bolts2",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts3",
            "al:android:app_name", "Bolts2");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(3, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertEquals("Bolts", target.getAppName());

    target = link.getTargets().get(1);
    Assert.assertEquals("com.bolts2", target.getPackageName());
    Assert.assertEquals("Bolts2", target.getAppName());
    Assert.assertEquals(url, link.getWebUrl());

    target = link.getTargets().get(2);
    Assert.assertEquals("com.bolts3", target.getPackageName());
    Assert.assertNull(target.getAppName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testWebViewPlatformFiltering() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",
            "al:android:package", "com.bolts",
            "al:android:class", "com.bolts.BoltsActivity",

            "al:ios", null,
            "al:ios:url", "bolts://iphone",
            "al:ios:app_name", "Bolts",
            "al:ios:app_store_id", "123456",

            "al:android", null,
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts2",
            "al:android:package", "com.bolts2",
            "al:android:class", "com.bolts.BoltsActivity2");
    Uri url = getURLForData(html);
    Task<AppLink> task = new WebViewAppLinkResolver(getInstrumentation().getTargetContext())
            .getAppLinkFromUrlInBackground(url);
    waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(2, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());

    target = link.getTargets().get(1);
    Assert.assertEquals(Uri.parse("bolts2://"), target.getUrl());
    Assert.assertEquals("Bolts2", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity2", target.getClassName());
    Assert.assertEquals("com.bolts2", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testSimpleAppLinkNavigationExplicit() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testSimpleAppLinkNavigationImplicit() throws Exception {
    // Don't provide a class name so that implicit resolution occurs.
    AppLink.Target target = new AppLink.Target("bolts.android",
            null,
            Uri.parse("bolts://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testSimpleAppLinkNavigationWithExtras() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    Bundle extras = new Bundle();
    extras.putString("foo", "bar");

    AppLinkNavigation navigation = new AppLinkNavigation(appLink, extras, null);

    AppLinkNavigation.NavigationResult navigationType = navigation.navigate(activityInterceptor);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
    Assert.assertEquals("bar", AppLinks.getAppLinkExtras(openedIntent).getString("foo"));
  }

  public void testSimpleAppLinkNavigationWithAppLinkData() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    Bundle appLinkData = new Bundle();
    appLinkData.putString("foo", "bar");

    AppLinkNavigation navigation = new AppLinkNavigation(appLink, null, appLinkData);

    AppLinkNavigation.NavigationResult navigationType = navigation.navigate(activityInterceptor);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
    Assert.assertEquals("bar", AppLinks.getAppLinkData(openedIntent).getString("foo"));
  }

  public void testSimpleAppLinkNavigationWithExtrasAndAppLinkData() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    Bundle extras = new Bundle();
    extras.putString("foo", "bar1");

    Bundle appLinkData = new Bundle();
    appLinkData.putString("foo", "bar2");

    AppLinkNavigation navigation = new AppLinkNavigation(appLink, extras, appLinkData);

    AppLinkNavigation.NavigationResult navigationType = navigation.navigate(activityInterceptor);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
    Assert.assertEquals("bar1", AppLinks.getAppLinkExtras(openedIntent).getString("foo"));
    Assert.assertEquals("bar2", AppLinks.getAppLinkData(openedIntent).getString("foo"));
  }

  public void testSimpleAppLinkNavigationWithExtrasAndAppLinkDataFallBackToWeb()
          throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity3",
            Uri.parse("bolts3://"),
            "Bolts");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target),
            Uri.parse("http://www.example.com/path"));

    Bundle extras = new Bundle();
    extras.putString("foo", "bar1");

    Bundle appLinkData = new Bundle();
    appLinkData.putString("foo", "bar2");

    AppLinkNavigation navigation = new AppLinkNavigation(appLink, extras, appLinkData);

    AppLinkNavigation.NavigationResult navigationType = navigation.navigate(activityInterceptor);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith("http://www.example.com/path"));

    String appLinkDataString = openedIntent.getData().getQueryParameter("al_applink_data");
    JSONObject appLinkDataJSON = new JSONObject(appLinkDataString);
    JSONObject appLinkExtrasJSON = appLinkDataJSON.getJSONObject("extras");
    Assert.assertEquals("bar1", appLinkExtrasJSON.getString("foo"));
    Assert.assertEquals("bar2", appLinkData.getString("foo"));
  }

  public void testAppLinkNavigationMultipleTargetsNoFallbackExplicit() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink.Target target2 = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity2",
            Uri.parse("bolts2://"),
            "Bolts 2");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target, target2),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkNavigationMultipleTargetsNoFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    AppLink.Target target = new AppLink.Target("bolts.android",
            null,
            Uri.parse("bolts://"),
            "Bolts");
    AppLink.Target target2 = new AppLink.Target("bolts.android",
            null,
            Uri.parse("bolts2://"),
            "Bolts 2");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target, target2),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkNavigationMultipleTargetsWithFallbackExplicit() throws Exception {
    AppLink.Target target = new AppLink.Target("bolts.android",
            "bolts.utils.InvalidActivity",
            Uri.parse("bolts://"),
            "Bolts");
    AppLink.Target target2 = new AppLink.Target("bolts.android",
            "bolts.utils.BoltsActivity2",
            Uri.parse("bolts2://"),
            "Bolts 2");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target, target2),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkNavigationMultipleTargetsWithFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    AppLink.Target target = new AppLink.Target("bolts.android",
            null,
            Uri.parse("invalid://"),
            "Bolts");
    AppLink.Target target2 = new AppLink.Target("bolts.android",
            null,
            Uri.parse("bolts2://"),
            "Bolts 2");
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            Arrays.asList(target, target2),
            Uri.parse("http://www.example.com/path"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(Uri.parse("http://www.example.com/path"),
            AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkNavigationNoTargets() throws Exception {
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            null,
            Uri.parse("http://www.example.com/path2"));

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith("http://www.example.com/path2"));
  }

  public void testAppLinkNavigationFailure() throws Exception {
    AppLink appLink = new AppLink(Uri.parse("http://www.example.com/path"),
            null,
            null);

    AppLinkNavigation.NavigationResult navigationType = AppLinkNavigation.navigate(
            activityInterceptor, appLink);

    Assert.assertEquals(AppLinkNavigation.NavigationResult.FAILED, navigationType);
    Assert.assertEquals(0, openedIntents.size());
  }

  public void testSimpleAppLinkURLNavigationExplicit() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.BoltsActivity",
            "al:android:app_name", "Bolts");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testSimpleAppLinkURLNavigationImplicit() throws Exception {
    // Don't provide a class name so that implicit resolution occurs.
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsNoFallbackExplicit() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.BoltsActivity",
            "al:android:app_name", "Bolts",

            "al:android",
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:class", "bolts.utils.BoltsActivity2",
            "al:android:app_name", "Bolts 2");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsNoFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",

            "al:android",
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts 2");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsWithFallbackExplicit() throws Exception {
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.InvalidActivity",
            "al:android:app_name", "Bolts",

            "al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:class", "bolts.utils.BoltsActivity2",
            "al:android:app_name", "Bolts 2");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsWithFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    String html = getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "invalid://",
            "al:android:app_name", "Bolts",

            "al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts 2");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationNoTargets() throws Exception {
    String html = getHtmlWithMetaTags();
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith(uri.toString()));
  }

  public void testAppLinkURLNavigationWebOnly() throws Exception {
    String html = getHtmlWithMetaTags("al:web:url", "http://www.example.com/path");
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith("http://www.example.com/path"));
  }

  public void testAppLinkURLNavigationFailure() throws Exception {
    String html = getHtmlWithMetaTags("al:web:should_fallback", "No"); // case insensitive
    Uri uri = getURLForData(html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.FAILED, navigationType);
    Assert.assertEquals(0, openedIntents.size());
  }

}
