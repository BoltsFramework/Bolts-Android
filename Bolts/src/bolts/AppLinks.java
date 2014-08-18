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
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
/**
 * Provides a set of utility methods for working with incoming Intents that may contain App Link
 * data.
 */
public final class AppLinks {
  /* event name for broadcast sent when handle incoming applink intent */
  public static final String APP_LINK_NAVIGATE_IN_EVENT_NAME = "al_nav_in";

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
    Bundle applinkData = intent.getBundleExtra(KEY_NAME_APPLINK_DATA);
    if (applinkData != null) {
      return applinkData;
    }

    Uri intentUri = intent.getData();

    String applinkDataString = intentUri.getQueryParameter(KEY_NAME_APPLINK_DATA);
    if (applinkDataString == null) {
      return null;
    }
    try {
      JSONObject applinkJSONData = new JSONObject(applinkDataString);
      applinkData = JSONObjectToBundle(applinkJSONData);
      return applinkData;
    } catch (JSONException e) {
      return null;
    }
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
   * Otherwise, it return null; For app link intent, this function will broadcast APP_LINK_NAVIGATE_IN_EVENT_NAME event.
   *
   * @param context the context this function is called within.
   * @param intent the incoming intent.
   * @return the target URL for the intent if applink intent; null otherwise.
   */
  public static Uri getTargetUrlFromInboundIntent(Context context, Intent intent) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData != null) {
      String targetString = appLinkData.getString(KEY_NAME_TARGET);
      if (targetString != null) {
        MeasurementEvent.sendEventBroadcast(context, APP_LINK_NAVIGATE_IN_EVENT_NAME, intent, null);
        return Uri.parse(targetString);
      }
    }
    return null;
  }

  private static Bundle JSONObjectToBundle(JSONObject jsonObject) throws JSONException{
    Iterator<String> keys = jsonObject.keys();
    Bundle retBundle = new Bundle();
    while( keys.hasNext() ){
      String key = keys.next();
      Object value = jsonObject.get(key);
      if (value instanceof JSONObject) {
        retBundle.putBundle(key, JSONObjectToBundle((JSONObject)value));
      } else if(value instanceof String) {
        retBundle.putString(key, (String)value);
      }
    }
    return retBundle;
  }
}
