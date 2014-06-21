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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Provides a set of utility methods for working with incoming Intents that may contain App Link
 * data.
 */
public final class AppLinks {
  /* event name for broadcast sent when handle incoming applink intent */
  public static final String BF_APP_LINK_NAVIGATE_IN_EVENT_NAME = "al_nav_in";

  static final String KEY_NAME_APPLINK_DATA = "al_applink_data";
  static final String KEY_NAME_EXTRAS = "extras";
  static final String KEY_NAME_TARGET = "target_url";

  /**
   * Gets the App Link data for an intent, if there is any.
   *
   * @param intent the incoming intent.
   * @return a bundle containing the App Link data for the intent, or {@code null} if none
   * is specified.
   */
  public static Bundle getAppLinkData(Intent intent) {
    return intent.getBundleExtra(KEY_NAME_APPLINK_DATA);
  }

  /**
   * Gets the App Link extras for an intent, if there is any.
   *
   * @param intent the incoming intent.
   * @return a bundle containing the App Link extras for the intent, or {@code null} if none is
   * specified.
   */
  public static Bundle getAppLinkExtras(Intent intent) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData == null) {
      return null;
    }
    return appLinkData.getBundle(KEY_NAME_EXTRAS);
  }

  /**
   * Gets the target URL for an intent, regardless of whether the intent is from an App Link. If the
   * intent is from an App Link, this will be the App Link target. Otherwise, it will be the data
   * Uri from the intent itself.
   *
   * @param intent the incoming intent.
   * @return the target URL for the intent.
   */
  @Deprecated // since Jul 2014
  public static Uri getTargetUrl(Intent intent) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData != null) {
      String targetString = appLinkData.getString(KEY_NAME_TARGET);
      if (targetString != null) {
        return Uri.parse(targetString);
      }
    }
    return intent.getData();
  }

  /**
   * Gets the target URL for an intent. If the intent is from an App Link, this will be the App Link target.
   * Otherwise, it return null; For app link intent, this function will broadcast a 'parse' event.
   *
   * @param context the context where this function is called.
   * @param intent the incoming intent.
   * @return the target URL for the intent if applink intent; null otherwise.
   */
  public static Uri getTargetUrl(Context context, Intent intent) {
    return getTargetUrl(context, intent, false);
  }

  /**
   * Gets the target URL for an intent. If the intent is from an App Link, this will be the App Link target.
   * Otherwise, it return null; For app link intent, this function will broadcast a BF_APP_LINK_PARSE_EVENT_NAME event
   * and a BF_APP_LINK_NAVIGATE_IN_EVENT_NAME event.
   *
   * @param intent the incoming intent.
   * @return the target URL for the intent if applink intent; null otherwise.
   */
  public static Uri getTargetUrlFromInboundIntent(Context context, Intent intent) {
    return getTargetUrl(context, intent, true);
  }

  private static Uri getTargetUrl(
      Context context,
      Intent intent,
      Boolean shouldSendNavigateInEvent
  ) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData != null) {
      String targetString = appLinkData.getString(KEY_NAME_TARGET);
      if (targetString != null) {
        if (shouldSendNavigateInEvent) {
          MeasurementEvent.sendEventBroadcast(context, BF_APP_LINK_NAVIGATE_IN_EVENT_NAME, intent, null);
        }
        return Uri.parse(targetString);
      }
    }
    return null;
  }

}
