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

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bolts.utils.TestUtils;

public class AppLinkNavigationTest extends InstrumentationTestCase {

  private List<Intent> openedIntents;
  private Context activityInterceptor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    openedIntents = new ArrayList<>();
    activityInterceptor = new ContextWrapper(getInstrumentation().getTargetContext()) {
      @Override
      public void startActivity(Intent intent) {
        openedIntents.add(intent);
      }
    };
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
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.BoltsActivity",
            "al:android:app_name", "Bolts");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testSimpleAppLinkURLNavigationImplicit() throws Exception {
    // Don't provide a class name so that implicit resolution occurs.
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsNoFallbackExplicit() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.BoltsActivity",
            "al:android:app_name", "Bolts",

            "al:android",
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:class", "bolts.utils.BoltsActivity2",
            "al:android:app_name", "Bolts 2");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsNoFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:app_name", "Bolts",

            "al:android",
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts 2");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsWithFallbackExplicit() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts://",
            "al:android:class", "bolts.utils.InvalidActivity",
            "al:android:app_name", "Bolts",

            "al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:class", "bolts.utils.BoltsActivity2",
            "al:android:app_name", "Bolts 2");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationMultipleTargetsWithFallbackImplicit() throws Exception {
    // Remove the class name to make it implicit
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "invalid://",
            "al:android:app_name", "Bolts",

            "al:android", null,
            "al:android:package", "bolts.android",
            "al:android:url", "bolts2://",
            "al:android:app_name", "Bolts 2");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.APP, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertEquals(uri, AppLinks.getTargetUrl(openedIntent));
    Assert.assertEquals("bolts2", openedIntent.getData().getScheme());
  }

  public void testAppLinkURLNavigationNoTargets() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags();
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith(uri.toString()));
  }

  public void testAppLinkURLNavigationWebOnly() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:web:url", "http://www.example.com/path");
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.WEB, navigationType);
    Assert.assertEquals(1, openedIntents.size());

    Intent openedIntent = openedIntents.get(0);
    Assert.assertTrue(openedIntent.getDataString().startsWith("http://www.example.com/path"));
  }

  public void testAppLinkURLNavigationFailure() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:web:should_fallback", "No"); // case insensitive
    Uri uri = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);

    Task<AppLinkNavigation.NavigationResult> task = AppLinkNavigation.navigateInBackground(
            activityInterceptor, uri);
    TestUtils.waitForTask(task);
    AppLinkNavigation.NavigationResult navigationType = task.getResult();

    Assert.assertEquals(AppLinkNavigation.NavigationResult.FAILED, navigationType);
    Assert.assertEquals(0, openedIntents.size());
  }

}
