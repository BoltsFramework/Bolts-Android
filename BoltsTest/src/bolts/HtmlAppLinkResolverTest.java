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
import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import bolts.utils.TestUtils;

public class HtmlAppLinkResolverTest extends InstrumentationTestCase {

  public void testHtmlSimpleAppLinkParsing() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
        "al:android:url", "bolts://",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts",
        "al:android:class", "com.bolts.BoltsActivity");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testHtmlAppLinkParsingFailure() throws Exception {
    Task<AppLink> task = new HtmlAppLinkResolver()
        .getAppLinkFromUrlInBackground(Uri.parse("http://badurl"));
    task.waitForCompletion();
    Assert.assertNotNull(task.getError());
  }

  public void testHtmlSimpleAppLinkParsingZeroShouldFallback() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
        "al:android:url", "bolts://",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts",
        "al:android:class", "com.bolts.BoltsActivity",
        "al:web:should_fallback", "0");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertNull(link.getWebUrl());
  }

  public void testHtmlSimpleAppLinkParsingFalseShouldFallback() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
        "al:android:url", "bolts://",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts",
        "al:android:class", "com.bolts.BoltsActivity",
        "al:web:should_fallback", "fAlse"); // case insensitive
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertNull(link.getWebUrl());
  }

  public void testHtmlSimpleAppLinkParsingWithWebUrl() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
        "al:android:url", "bolts://",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts",
        "al:android:class", "com.bolts.BoltsActivity",
        "al:web:url", "http://www.example.com");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(1, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals(Uri.parse("bolts://"), target.getUrl());
    Assert.assertEquals("Bolts", target.getAppName());
    Assert.assertEquals("com.bolts.BoltsActivity", target.getClassName());
    Assert.assertEquals("com.bolts", target.getPackageName());
    Assert.assertEquals(Uri.parse("http://www.example.com"), link.getWebUrl());
  }

  public void testHtmlVersionedAppLinkParsing() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
        "al:android:url", "bolts://",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts",
        "al:android:class", "com.bolts.BoltsActivity",

        "al:android", null,
        "al:android:url", "bolts2://",
        "al:android:app_name", "Bolts2",
        "al:android:package", "com.bolts2",
        "al:android:class", "com.bolts.BoltsActivity2");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
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

  public void testHtmlVersionedAppLinkParsingOnlyPackages() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android:package", "com.bolts",
        "al:android:package", "com.bolts2");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
    AppLink link = task.getResult();

    Assert.assertEquals(2, link.getTargets().size());

    AppLink.Target target = link.getTargets().get(0);
    Assert.assertEquals("com.bolts", target.getPackageName());

    target = link.getTargets().get(1);
    Assert.assertEquals("com.bolts2", target.getPackageName());
    Assert.assertEquals(url, link.getWebUrl());
  }

  public void testHtmlVersionedAppLinkParsingPackagesAndNames() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android:package", "com.bolts",
        "al:android:package", "com.bolts2",
        "al:android:app_name", "Bolts",
        "al:android:package", "com.bolts3",
        "al:android:app_name", "Bolts2");
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
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

  public void testHtmlPlatformFiltering() throws Exception {
    String html = TestUtils.getHtmlWithMetaTags("al:android", null,
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
    Uri url = TestUtils.getURLForData(getInstrumentation().getTargetContext(), html);
    Task<AppLink> task = new HtmlAppLinkResolver().getAppLinkFromUrlInBackground(url);
    TestUtils.waitForTask(task);
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
}
