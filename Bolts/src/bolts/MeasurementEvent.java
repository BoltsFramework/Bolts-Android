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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.util.Arrays;
import java.util.Collection;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import org.json.*;

import java.util.ArrayList;
import java.util.Map;

public class MeasurementEvent {
  public final static String MEASUREMENT_EVENT_NOTIFICATION_NAME = "com.parse.bolts.measurement_event";
  public final static String MEASUREMENT_EVENT_NAME_KEY = "event_name";
  public final static String MEASUREMENT_EVENT_ARGS_KEY = "event_args";

  private final static String MEASUREMENT_EVENT_SENT_TAGS_KEY = "bolts_events_sent";

  private String name;
  private Bundle args;

  public static void sendEventBroadcast(
      Context context,
      String name,
      Intent appLinkIntent,
      Map<String, String> extraLoggingData
  ) {

    if (!shouldSendBroadcast(appLinkIntent, name)) {
      return;
    }

    Bundle logData = new Bundle();
    if (appLinkIntent != null) {
      ComponentName resolvedActivity = appLinkIntent.resolveActivity(context.getPackageManager());
      if (resolvedActivity != null) {
        logData.putString("class", resolvedActivity.getShortClassName());
        logData.putString("package", resolvedActivity.getPackageName());
      }

      if (appLinkIntent.getData() != null) {
        logData.putString("outputURL", appLinkIntent.getData().toString());
      }

      if (appLinkIntent.getScheme() != null) {
        logData.putString("outputURLScheme", appLinkIntent.getScheme());
      }

      Bundle applinkData = AppLinks.getAppLinkData(appLinkIntent);
      if (applinkData != null) {
        Uri targetURI = AppLinks.getTargetUrl(context, appLinkIntent);
        logData.putString("targetURLHost", targetURI.getHost());
        for (String key : applinkData.keySet()) {
          Object o = applinkData.get(key);
          if (o instanceof Bundle) {
            for (String subKey : ((Bundle) o).keySet()) {
              String logValue = objectToJSONString(((Bundle) o).get(subKey));
              logData.putString(key + "/" + subKey, logValue);
            }
          } else {
            String logValue = objectToJSONString(o);
            logData.putString(key, logValue);
          }
        }
      }
    }

    if (extraLoggingData != null) {
      for (String key : extraLoggingData.keySet()) {
        logData.putString(key, extraLoggingData.get(key));
      }
    }

    MeasurementEvent event = new MeasurementEvent(name, logData);
    event.tagEventSent(appLinkIntent, name);
    event.sendBroadcast(context);
  }

  // Mark event that has been sent in the intent, so that shouldSendBroadcast can suppress duplicate event.
  private void tagEventSent(Intent appLinkIntent, String eventName) {
    if (appLinkIntent == null) {
      return;
    }
    ArrayList<String> eventsSent = appLinkIntent.getStringArrayListExtra(MEASUREMENT_EVENT_SENT_TAGS_KEY);
    if (eventsSent == null) {
      eventsSent = new ArrayList<String>();
      appLinkIntent.putExtra(MEASUREMENT_EVENT_SENT_TAGS_KEY, eventsSent);
    }
    eventsSent.add(eventName);
  }

  private static boolean shouldSendBroadcast(Intent appLinkIntent, String eventName) {
    if (appLinkIntent == null) {
      // extreme rare, so we log it.
      return true;
    }
    ArrayList<String> eventsSent = appLinkIntent.getStringArrayListExtra(MEASUREMENT_EVENT_SENT_TAGS_KEY);
    if (eventsSent == null) {
      eventsSent = new ArrayList<String>();
      appLinkIntent.putExtra(MEASUREMENT_EVENT_SENT_TAGS_KEY, eventsSent);
    }

    return !eventsSent.contains(eventName);
  }

  private MeasurementEvent(String eventName, Bundle eventArgs) {
    name = eventName;
    args = eventArgs;
  }

  private void sendBroadcast(Context context) {
    if (name == null) {
      Log.d(getClass().getName(), "Event name is required");
    }
    LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);
    Intent event = new Intent(MEASUREMENT_EVENT_NOTIFICATION_NAME);
    event.putExtra(MEASUREMENT_EVENT_NAME_KEY, name);
    event.putExtra(MEASUREMENT_EVENT_ARGS_KEY, args);
    manager.sendBroadcast(event);
  }

  private static String objectToJSONString(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof JSONArray || o instanceof JSONObject) {
      return o.toString();
    }

    try {
      if (o instanceof Collection) {
        return new JSONArray((Collection) o).toString();
      } else if (o.getClass().isArray()) {
        return new JSONArray(Arrays.asList(o)).toString();
      }
      if (o instanceof Map) {
        return new JSONObject((Map) o).toString();
      }
      if (o instanceof Boolean ||
          o instanceof Byte ||
          o instanceof Character ||
          o instanceof Double ||
          o instanceof Float ||
          o instanceof Integer ||
          o instanceof Long ||
          o instanceof Short ||
          o instanceof String) {
        return o.toString();
      }
      if (o.getClass().getPackage().getName().startsWith("java.")) {
        return o.toString();
      }
    } catch (Exception ignored) {
    }
    return null;
  }
}
