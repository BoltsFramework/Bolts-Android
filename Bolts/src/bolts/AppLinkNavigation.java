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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.List;

public class AppLinkNavigation {
  private static final String USER_AGENT_KEY_NAME = "user_agent";
  private static final String VERSION_KEY_NAME = "version";
  private static final int VERSION = 1;

  private static AppLinkResolver defaultResolver;

  public static enum NavigationType {
    APP,
    WEB,
    FAILED
  }

  private final AppLink appLink;
  private final Bundle extras;
  private final Bundle appLinkData;

  public AppLinkNavigation(AppLink appLink, Bundle extras, Bundle appLinkData) {
    if (appLink == null) {
      throw new IllegalArgumentException("appLink must not be null.");
    }
    if (extras == null) {
      extras = new Bundle();
    }
    if (appLinkData == null) {
      appLinkData = new Bundle();
    }
    this.appLink = appLink;
    this.extras = extras;
    this.appLinkData = appLinkData;
  }

  public AppLink getAppLink() {
    return appLink;
  }

  public Bundle getAppLinkData() {
    return appLinkData;
  }

  public Bundle getExtras() {
    return extras;
  }

  private Bundle buildAppLinkData() {
    Bundle data = new Bundle();
    data.putAll(getAppLinkData());
    data.putString(AppLinks.TARGET_KEY_NAME, getAppLink().getSourceUrl().toString());
    data.putInt(VERSION_KEY_NAME, VERSION);
    data.putString(USER_AGENT_KEY_NAME, "Bolts Android " + Bolts.VERSION);
    data.putBundle(AppLinks.EXTRAS_KEY_NAME, getExtras());
    return data;
  }

  private Object getJSONValue(Object value) throws JSONException {
    if (value instanceof Bundle) {
      return getJSONForBundle((Bundle) value);
    } else if (value instanceof CharSequence) {
      return value.toString();
    } else if (value instanceof List) {
      JSONArray array = new JSONArray();
      for (Object listValue : (List<?>) value) {
        array.put(getJSONValue(listValue));
      }
      return array;
    } else if (value instanceof SparseArray) {
      JSONArray array = new JSONArray();
      SparseArray<?> sparseValue = (SparseArray<?>) value;
      for (int i = 0; i < sparseValue.size(); i++) {
        array.put(sparseValue.keyAt(i), getJSONValue(sparseValue.valueAt(i)));
      }
      return array;
    } else if (value instanceof Character) {
      return value.toString();
    } else if (value instanceof Boolean) {
      return value;
    } else if (value instanceof Number) {
      if (value instanceof Double || value instanceof Float) {
        return ((Number) value).doubleValue();
      } else {
        return ((Number) value).longValue();
      }
    } else if (value instanceof boolean[]) {
      JSONArray array = new JSONArray();
      for (boolean arrValue : (boolean[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof char[]) {
      JSONArray array = new JSONArray();
      for (char arrValue : (char[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof CharSequence[]) {
      JSONArray array = new JSONArray();
      for (CharSequence arrValue : (CharSequence[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof double[]) {
      JSONArray array = new JSONArray();
      for (double arrValue : (double[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof float[]) {
      JSONArray array = new JSONArray();
      for (float arrValue : (float[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof int[]) {
      JSONArray array = new JSONArray();
      for (int arrValue : (int[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof long[]) {
      JSONArray array = new JSONArray();
      for (long arrValue : (long[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof short[]) {
      JSONArray array = new JSONArray();
      for (short arrValue : (short[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    } else if (value instanceof String[]) {
      JSONArray array = new JSONArray();
      for (String arrValue : (String[]) value) {
        array.put(getJSONValue(arrValue));
      }
      return array;
    }
    return null;
  }

  private JSONObject getJSONForBundle(Bundle bundle) throws JSONException {
    JSONObject root = new JSONObject();
    for (String key : bundle.keySet()) {
      root.put(key, getJSONValue(bundle.get(key)));
    }
    return root;
  }

  public NavigationType navigate(Context context) {
    PackageManager pm = context.getPackageManager();

    Bundle finalAppLinkData = buildAppLinkData();

    Intent eligibleTargetIntent = null;
    for (AppLink.Target target : getAppLink().getTargets()) {
      Intent targetIntent = new Intent(Intent.ACTION_VIEW);
      if (target.getUrl() != null) {
        targetIntent.setData(target.getUrl());
      } else {
        targetIntent.setData(appLink.getSourceUrl());
      }
      targetIntent.setPackage(target.getPackageName());
      if (target.getClassName() != null) {
        targetIntent.setClassName(target.getPackageName(), target.getClassName());
      }
      targetIntent.putExtra(AppLinks.APPLINK_DATA_KEY_NAME, finalAppLinkData);

      ResolveInfo resolved = pm.resolveActivity(targetIntent, PackageManager.MATCH_DEFAULT_ONLY);
      if (resolved != null) {
        eligibleTargetIntent = targetIntent;
        break;
      }
    }

    if (eligibleTargetIntent != null) {
      context.startActivity(eligibleTargetIntent);
      return NavigationType.APP;
    }

    // Fall back to the web if it's available
    Uri webUrl = getAppLink().getWebUrl();
    if (webUrl != null) {
      JSONObject appLinkDataJson;
      try {
        appLinkDataJson = getJSONForBundle(finalAppLinkData);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
      webUrl = webUrl.buildUpon()
              .appendQueryParameter(AppLinks.APPLINK_DATA_KEY_NAME, appLinkDataJson.toString())
              .build();
      Intent launchBrowserIntent = new Intent(Intent.ACTION_VIEW, webUrl);
      context.startActivity(launchBrowserIntent);
      return NavigationType.WEB;
    }

    return NavigationType.FAILED;
  }

  public static void setDefaultResolver(AppLinkResolver resolver) {
    defaultResolver = resolver;
  }

  public static AppLinkResolver getDefaultResolver() {
    return defaultResolver;
  }

  private static AppLinkResolver getResolver(Context context) {
    if (getDefaultResolver() != null) {
      return getDefaultResolver();
    }
    return new WebViewAppLinkResolver(context);
  }

  public static NavigationType navigate(Context context, AppLink appLink) {
    return new AppLinkNavigation(appLink, null, null).navigate(context);
  }

  public static Task<NavigationType> navigateInBackground(final Context context,
                                                          Uri destination,
                                                          AppLinkResolver resolver) {
    return resolver.getAppLinkFromUrlInBackground(destination)
            .onSuccess(new Continuation<AppLink, NavigationType>() {
              @Override
              public NavigationType then(Task<AppLink> task) throws Exception {
                return navigate(context, task.getResult());
              }
            }, Task.UI_THREAD_EXECUTOR);
  }

  public static Task<NavigationType> navigateInBackground(Context context,
                                                          URL destination,
                                                          AppLinkResolver resolver) {
    return navigateInBackground(context, Uri.parse(destination.toString()), resolver);
  }

  public static Task<NavigationType> navigateInBackground(Context context,
                                                          String destinationUrl,
                                                          AppLinkResolver resolver) {
    return navigateInBackground(context, Uri.parse(destinationUrl), resolver);
  }

  public static Task<NavigationType> navigateInBackground(Context context,
                                                          Uri destination) {
    return navigateInBackground(context,
            destination,
            getResolver(context));
  }

  public static Task<NavigationType> navigateInBackground(Context context,
                                                          URL destination) {
    return navigateInBackground(context,
            destination,
            getResolver(context));
  }

  public static Task<NavigationType> navigateInBackground(Context context,
                                                          String destinationUrl) {
    return navigateInBackground(context,
            destinationUrl,
            getResolver(context));
  }
}
