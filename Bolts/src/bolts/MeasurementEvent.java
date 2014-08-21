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
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 *  Bolts raises events to the application by sending local broadcasts.
 *  Application integrated to Bolts can listen to events by registering BroadcastReceiver with
 *  intent filter {@link #MEASUREMENT_EVENT_NOTIFICATION_NAME}. When receive the event,
 *  a String field {@link #MEASUREMENT_EVENT_NAME_KEY} and A Bundle field @{link #MEASUREMENT_EVENT_ARGS_KEY}
 *  in Intent extra in indicate the event name and arguments with this event.
 */
public class MeasurementEvent {
  /**
   *  The action of the broadcast intent that represents Bolts measurement event.
   */
  public static final String MEASUREMENT_EVENT_NOTIFICATION_NAME = "com.parse.bolts.measurement_event";

  /**
   * The field name in the broadcast intent extra bundle that represents Bolts measurement event name.
   */
  public static final String MEASUREMENT_EVENT_NAME_KEY = "event_name";

  /**
   * The field name in the broadcast intent extra bundle that represents Bolts measurement event arguments.
   */
  public static final String MEASUREMENT_EVENT_ARGS_KEY = "event_args";

  // Events
  /**
   * The name for event of navigating out to other apps. Event raised in navigation methods, e.g.
   * {@link bolts.AppLinkNavigation#navigateInBackground(android.content.Context, String)}
   **/
  public static final String APP_LINK_NAVIGATE_OUT_EVENT_NAME = "al_nav_out";

  /**
   *  The name for event of handling incoming applink intent. Event raised in
   *  {@link bolts.AppLinks#getTargetUrlFromInboundIntent(android.content.Context, android.content.Intent)}
   **/
  public static final String APP_LINK_NAVIGATE_IN_EVENT_NAME = "al_nav_in";

  /**
   *  Broadcast Bolts measurement event.
   *  Bolts raises events to the application with this method by sending
   *  {@link #MEASUREMENT_EVENT_NOTIFICATION_NAME} broadcast.
   *
   *  @param context the context of activity or application who is going to send the event. required.
   *  @param name the event name that is going to be sent. required.
   *  @param intent the intent that carries the logging data in its extra bundle and data url. optional.
   *  @param extraLoggingData other logging data to be sent in events argument. optional.
   *
   */
  static void sendBroadcastEvent(
      Context context,
      String name,
      Intent intent,
      Map<String, String> extraLoggingData) {
    Bundle logData = new Bundle();
    if (intent != null) {
      Bundle applinkData = AppLinks.getAppLinkData(intent);
      if (applinkData != null) {
        logData = getApplinkLogData(context, name, applinkData, intent);
      } else {
        Uri intentUri = intent.getData();
        if (intentUri != null) {
          logData.putString("intentData", intentUri.toString());
        }
        Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
          for (String key : intentExtras.keySet()) {
            Object o = intentExtras.get(key);
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
    MeasurementEvent event = new MeasurementEvent(context, name, logData);
    event.sendBroadcast();
  }

  private Context appContext;
  private String name;
  private Bundle args;

  private MeasurementEvent(Context context, String eventName, Bundle eventArgs) {
    appContext = context.getApplicationContext();
    name = eventName;
    args = eventArgs;
  }

  private void sendBroadcast() {
    if (name == null) {
      Log.d(getClass().getName(), "Event name is required");
    }
    try {
      Class<?> clazz = Class.forName("android.support.v4.content.LocalBroadcastManager");
      Method methodGetInstance = clazz.getMethod("getInstance", Context.class);
      Method methodSendBroadcast = clazz.getMethod("sendBroadcast", Intent.class);
      Object localBroadcastManager = methodGetInstance.invoke(null, appContext);
      Intent event = new Intent(MEASUREMENT_EVENT_NOTIFICATION_NAME);
      event.putExtra(MEASUREMENT_EVENT_NAME_KEY, name);
      event.putExtra(MEASUREMENT_EVENT_ARGS_KEY, args);
      methodSendBroadcast.invoke(localBroadcastManager, event);
    } catch (Exception e) {
      Log.d(getClass().getName(),
          "LocalBroadcastManager in android support library is required to raise bolts event.");
    }
  }

  private static Bundle getApplinkLogData(Context context, String eventName, Bundle appLinkData, Intent applinkIntent) {
    Bundle logData = new Bundle();
    ComponentName resolvedActivity = applinkIntent.resolveActivity(context.getPackageManager());

    if (resolvedActivity != null) {
      logData.putString("class", resolvedActivity.getShortClassName());
    }

    if (APP_LINK_NAVIGATE_OUT_EVENT_NAME.equals(eventName)) {
      if (resolvedActivity != null) {
        logData.putString("package", resolvedActivity.getPackageName());
      }
      if (applinkIntent.getData() != null) {
        logData.putString("outputURL", applinkIntent.getData().toString());
      }
      if (applinkIntent.getScheme() != null) {
        logData.putString("outputURLScheme", applinkIntent.getScheme());
      }
    } else if (APP_LINK_NAVIGATE_IN_EVENT_NAME.equals(eventName)) {
      if (applinkIntent.getData() != null) {
        logData.putString("inputURL", applinkIntent.getData().toString());
      }
      if (applinkIntent.getScheme() != null) {
        logData.putString("inputURLScheme", applinkIntent.getScheme());
      }
    }

    for (String key : appLinkData.keySet()) {
      Object o = appLinkData.get(key);
      if (o instanceof Bundle) {
        for (String subKey : ((Bundle) o).keySet()) {
          String logValue = objectToJSONString(((Bundle) o).get(subKey));
          if (key.equals("referer_app_link")) {
            if (subKey.equalsIgnoreCase("url")) {
              logData.putString("refererURL", logValue);
              continue;
            } else if (subKey.equalsIgnoreCase("app_name")) {
              logData.putString("refererAppName", logValue);
              continue;
            } else if (subKey.equalsIgnoreCase("package")) {
              logData.putString("sourceApplication", logValue);
              continue;
            }
          }
          logData.putString(key + "/" + subKey, logValue);
        }
      } else {
        String logValue = objectToJSONString(o);
        if (key.equals("target_url")) {
          Uri targetURI = Uri.parse(logValue);
          logData.putString("targetURL", targetURI.toString());
          logData.putString("targetURLHost", targetURI.getHost());
          continue;
        }
        logData.putString(key, logValue);
      }
    }
    return logData;
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
        return (new JSONArray((Collection) o)).toString();
      }
      if (o instanceof Map) {
        return (new JSONObject((Map) o)).toString();
      }
      return o.toString();
    } catch (Exception ignored) {
    }
    return null;
  }
}
