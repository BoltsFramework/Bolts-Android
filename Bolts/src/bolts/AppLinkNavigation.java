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

/**
 * Represents a pending request to navigate to an App Link. Most developers will simply use
 * {@link #navigateInBackground(android.content.Context, android.net.Uri)} to open a URL, but
 * developers can build custom requests with additional navigation and app data attached to them
 * by creating AppLinkNavigations themselves.
 */
public class AppLinkNavigation {
  private static final String KEY_NAME_USER_AGENT = "user_agent";
  private static final String KEY_NAME_VERSION = "version";
  private static final String VERSION = "1.0";

  private static AppLinkResolver defaultResolver;

  /**
   * The result of calling {@link #navigate(android.content.Context)} on an
   * {@link bolts.AppLinkNavigation}.
   */
  public static enum NavigationResult {
    /**
     * Indicates that the navigation failed and no app was opened.
     */
    FAILED,
    /**
     * Indicates that the navigation succeeded by opening the URL in the browser.
     */
    WEB,
    /**
     * Indicates that the navigation succeeded by opening the URL in an app on the device.
     */
    APP,
  }

  private final AppLink appLink;
  private final Bundle extras;
  private final Bundle appLinkData;

  /**
   * Creates an AppLinkNavigation with the given link, extras, and App Link data.
   *
   * @param appLink     the AppLink being navigated to.
   * @param extras      the extras to include in the App Link navigation.
   * @param appLinkData additional App Link data for the navigation.
   */
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

  /**
   * @return the App Link to navigate to.
   */
  public AppLink getAppLink() {
    return appLink;
  }

  /**
   * Gets the al_applink_data for the AppLinkNavigation. This will generally contain data common
   * to navigation attempts such as back-links, user agents, and other information that may be used
   * in routing and handling an App Link request.
   *
   * @return the App Link data.
   */
  public Bundle getAppLinkData() {
    return appLinkData;
  }

  /**
   * The extras for the AppLinkNavigation. This will generally contain application-specific data
   * that should be passed along with the request, such as advertiser or affiliate IDs or other such
   * metadata relevant on this device.
   *
   * @return the extras for the AppLinkNavigation.
   */
  public Bundle getExtras() {
    return extras;
  }

  /**
   * Creates a bundle containing the final, constructed App Link data to be used in navigation.
   */
  private Bundle buildAppLinkDataForNavigation() {
    Bundle data = new Bundle();
    data.putAll(getAppLinkData());
    data.putString(AppLinks.KEY_NAME_TARGET, getAppLink().getSourceUrl().toString());
    data.putString(KEY_NAME_VERSION, VERSION);
    data.putString(KEY_NAME_USER_AGENT, "Bolts Android " + Bolts.VERSION);
    data.putBundle(AppLinks.KEY_NAME_EXTRAS, getExtras());
    return data;
  }

  /**
   * Gets a JSONObject-compatible value for the given object.
   */
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

  /**
   * Gets a JSONObject equivalent to the input bundle for use when falling back to a web navigation.
   */
  private JSONObject getJSONForBundle(Bundle bundle) throws JSONException {
    JSONObject root = new JSONObject();
    for (String key : bundle.keySet()) {
      root.put(key, getJSONValue(bundle.get(key)));
    }
    return root;
  }

  /**
   * Performs the navigation.
   *
   * @param context the Context from which the navigation should be performed.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public NavigationResult navigate(Context context) {
    PackageManager pm = context.getPackageManager();

    Bundle finalAppLinkData = buildAppLinkDataForNavigation();

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
      targetIntent.putExtra(AppLinks.KEY_NAME_APPLINK_DATA, finalAppLinkData);

      ResolveInfo resolved = pm.resolveActivity(targetIntent, PackageManager.MATCH_DEFAULT_ONLY);
      if (resolved != null) {
        eligibleTargetIntent = targetIntent;
        break;
      }
    }

    if (eligibleTargetIntent != null) {
      context.startActivity(eligibleTargetIntent);
      return NavigationResult.APP;
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
              .appendQueryParameter(AppLinks.KEY_NAME_APPLINK_DATA, appLinkDataJson.toString())
              .build();
      Intent launchBrowserIntent = new Intent(Intent.ACTION_VIEW, webUrl);
      context.startActivity(launchBrowserIntent);
      return NavigationResult.WEB;
    }

    return NavigationResult.FAILED;
  }

  /**
   * Sets the default resolver to be used for App Link resolution. Setting this to null will cause
   * the {@link #navigateInBackground(android.content.Context, android.net.Uri)} methods to use the
   * basic, built-in resolver provided by Bolts.
   *
   * @param resolver the resolver to use by default.
   */
  public static void setDefaultResolver(AppLinkResolver resolver) {
    defaultResolver = resolver;
  }

  /**
   * Gets the default resolver to be used for App Link resolution. If the developer has not set a
   * default resolver, this will return {@code null}, but the basic, built-in resolver provided by
   * Bolts will be used.
   *
   * @return the default resolver, or {@code null} if none is set.
   */
  public static AppLinkResolver getDefaultResolver() {
    return defaultResolver;
  }

  private static AppLinkResolver getResolver(Context context) {
    if (getDefaultResolver() != null) {
      return getDefaultResolver();
    }
    return new WebViewAppLinkResolver(context);
  }

  /**
   * Navigates to an {@link bolts.AppLink}.
   *
   * @param context the Context from which the navigation should be performed.
   * @param appLink the AppLink being navigated to.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static NavigationResult navigate(Context context, AppLink appLink) {
    return new AppLinkNavigation(appLink, null, null).navigate(context);
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the App Link resolution
   * strategy specified.
   *
   * @param context     the Context from which the navigation should be performed.
   * @param destination the destination URL for the App Link.
   * @param resolver    the resolver to use for fetching App Link metadata.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(final Context context,
                                                            Uri destination,
                                                            AppLinkResolver resolver) {
    return resolver.getAppLinkFromUrlInBackground(destination)
            .onSuccess(new Continuation<AppLink, NavigationResult>() {
              @Override
              public NavigationResult then(Task<AppLink> task) throws Exception {
                return navigate(context, task.getResult());
              }
            }, Task.UI_THREAD_EXECUTOR);
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the App Link resolution
   * strategy specified.
   *
   * @param context     the Context from which the navigation should be performed.
   * @param destination the destination URL for the App Link.
   * @param resolver    the resolver to use for fetching App Link metadata.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(Context context,
                                                            URL destination,
                                                            AppLinkResolver resolver) {
    return navigateInBackground(context, Uri.parse(destination.toString()), resolver);
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the App Link resolution
   * strategy specified.
   *
   * @param context        the Context from which the navigation should be performed.
   * @param destinationUrl the destination URL for the App Link.
   * @param resolver       the resolver to use for fetching App Link metadata.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(Context context,
                                                            String destinationUrl,
                                                            AppLinkResolver resolver) {
    return navigateInBackground(context, Uri.parse(destinationUrl), resolver);
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the default
   * App Link resolution strategy.
   *
   * @param context     the Context from which the navigation should be performed.
   * @param destination the destination URL for the App Link.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(Context context,
                                                            Uri destination) {
    return navigateInBackground(context,
            destination,
            getResolver(context));
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the default
   * App Link resolution strategy.
   *
   * @param context     the Context from which the navigation should be performed.
   * @param destination the destination URL for the App Link.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(Context context,
                                                            URL destination) {
    return navigateInBackground(context,
            destination,
            getResolver(context));
  }

  /**
   * Navigates to an {@link bolts.AppLink} for the given destination using the default
   * App Link resolution strategy.
   *
   * @param context        the Context from which the navigation should be performed.
   * @param destinationUrl the destination URL for the App Link.
   * @return the {@link bolts.AppLinkNavigation.NavigationResult} performed by navigating.
   */
  public static Task<NavigationResult> navigateInBackground(Context context,
                                                            String destinationUrl) {
    return navigateInBackground(context,
            destinationUrl,
            getResolver(context));
  }
}
