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
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class WebViewAppLinkResolver implements AppLinkResolver {
  private final Context context;

  public WebViewAppLinkResolver(Context context) {
    this.context = context;
  }

  private static final String TAG_EXTRACTION_JAVASCRIPT = "javascript:" +
          "boltsWebViewAppLinkResolverResult.setValue((function() {" +
          "  var metaTags = document.getElementsByTagName('meta');" +
          "  var results = [];" +
          "  for (var i = 0; i < metaTags.length; i++) {" +
          "    var property = metaTags[i].getAttribute('property');" +
          "    if (property && property.substring(0, 'al:'.length) === 'al:') {" +
          "      var tag = { \"property\": metaTags[i].getAttribute('property') };" +
          "      if (metaTags[i].hasAttribute('content')) {" +
          "        tag['content'] = metaTags[i].getAttribute('content');" +
          "      }" +
          "      results.push(tag);" +
          "    }" +
          "  }" +
          "  return JSON.stringify(results);" +
          "})())";
  private static final String PREFER_HEADER = "Prefer-Html-Meta-Tags";
  private static final String META_TAG_PREFIX = "al";
  private static final String DICTIONARY_VALUE_KEY = "value";
  private static final String APP_NAME_KEY = "app_name";
  private static final String CLASS_KEY = "class";
  private static final String PACKAGE_KEY = "package";
  private static final String URL_KEY = "url";

  @Override
  public Task<AppLink> getAppLinkFromUrlInBackground(final Uri url) {
    final Capture<String> content = new Capture<String>();
    final Capture<String> contentType = new Capture<String>();
    return Task.callInBackground(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        // Fetch the content at the given URL.
        URLConnection connection = new URL(url.toString()).openConnection();
        if (connection instanceof HttpURLConnection) {
          ((HttpURLConnection) connection).setInstanceFollowRedirects(true);
        }
        connection.setRequestProperty(PREFER_HEADER, META_TAG_PREFIX);
        connection.connect();

        content.set(readFromConnection(connection));
        contentType.set(connection.getContentType());
        return null;
      }
    }).onSuccessTask(new Continuation<Void, Task<JSONArray>>() {
      @Override
      public Task<JSONArray> then(Task<Void> task) throws Exception {
        // Load the content in a WebView and use JavaScript to extract the meta tags.
        final Task<JSONArray>.TaskCompletionSource tcs = Task.create();
        final WebView webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setNetworkAvailable(false);
        webView.setWebViewClient(new WebViewClient() {
          private boolean loaded = false;

          private void runJavaScript(WebView view) {
            if (!loaded) {
              // After the first resource has been loaded (which will be the pre-populated data)
              // run the JavaScript meta tag extraction script
              loaded = true;
              view.loadUrl(TAG_EXTRACTION_JAVASCRIPT);
            }
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            runJavaScript(view);
          }

          @Override
          public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            runJavaScript(view);
          }
        });
        // Inject an object that will receive the JSON for the extracted JavaScript tags
        webView.addJavascriptInterface(new Object() {
          @JavascriptInterface
          public void setValue(String value) {
            Log.d("bolts.Test", "Got value: " + value);
            try {
              tcs.trySetResult(new JSONArray(value));
            } catch (JSONException e) {
              tcs.trySetError(e);
            }
          }
        }, "boltsWebViewAppLinkResolverResult");
        String inferredContentType = null;
        if (contentType.get() != null) {
          inferredContentType = contentType.get().split(";")[0];
        }
        webView.loadDataWithBaseURL(url.toString(),
                content.get(),
                inferredContentType,
                null,
                null);
        return tcs.getTask();
      }
    }, Task.UI_THREAD_EXECUTOR).onSuccess(new Continuation<JSONArray, AppLink>() {
      @Override
      public AppLink then(Task<JSONArray> task) throws Exception {
        Map<String, Object> alData = parseALData(task.getResult());
        AppLink appLink = makeAppLinkFromALData(alData, url);
        return appLink;
      }
    });
  }

  /**
   * Builds up a data structure filled with the app link data from the meta tags on a page.
   * The structure of this object is a dictionary where each key holds an array of app link
   * data dictionaries.  Values are stored in a key called "_value".
   */
  private static Map<String, Object> parseALData(JSONArray dataArray) throws JSONException {
    HashMap<String, Object> al = new HashMap<String, Object>();
    for (int i = 0; i < dataArray.length(); i++) {
      JSONObject tag = dataArray.getJSONObject(i);
      String name = tag.getString("property");
      String[] nameComponents = name.split(":");
      if (!nameComponents[0].equals(META_TAG_PREFIX)) {
        continue;
      }
      Map<String, Object> root = al;
      for (int j = 1; j < nameComponents.length; j++) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) root.get(nameComponents[j]);
        if (children == null) {
          children = new ArrayList<Map<String, Object>>();
          root.put(nameComponents[j], children);
        }
        Map<String, Object> child = children.size() > 0 ? children.get(children.size() - 1) : null;
        if (child == null || j == nameComponents.length - 1) {
          child = new HashMap<String, Object>();
          children.add(child);
        }
        root = child;
      }
      if (tag.has("content")) {
        if (tag.isNull("content")) {
          root.put(DICTIONARY_VALUE_KEY, null);
        } else {
          root.put(DICTIONARY_VALUE_KEY, tag.getString("content"));
        }
      }
    }
    return al;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getALList(Map<String, Object> map, String key) {
    List<Map<String, Object>> result = (List<Map<String, Object>>) map.get(key);
    if (result == null) {
      return Collections.emptyList();
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static AppLink makeAppLinkFromALData(Map<String, Object> appLinkDict, Uri destination) {
    List<AppLink.Target> targets = new ArrayList<AppLink.Target>();
    List<Map<String, Object>> platformMapList = (List<Map<String, Object>>) appLinkDict.get("android");
    if (platformMapList == null) {
      platformMapList = Collections.emptyList();
    }
    for (Map<String, Object> platformMap : platformMapList) {
      // The schema requires a single url/package/app name/class, but we could find multiple
      // of them. We'll make a best effort to interpret this data.
      List<Map<String, Object>> urls = getALList(platformMap, URL_KEY);
      List<Map<String, Object>> packages = getALList(platformMap, PACKAGE_KEY);
      List<Map<String, Object>> classes = getALList(platformMap, CLASS_KEY);
      List<Map<String, Object>> appNames = getALList(platformMap, APP_NAME_KEY);

      int maxCount = Math.max(urls.size(), Math.max(packages.size(), Math.max(classes.size(), appNames.size())));
      for (int i = 0; i < maxCount; i++) {
        String urlString = (String) (urls.size() > i ? urls.get(i).get(DICTIONARY_VALUE_KEY) : null);
        Uri url = tryCreateUri(urlString);
        String packageName = (String) (packages.size() > i ? packages.get(i).get(DICTIONARY_VALUE_KEY) : null);
        String className = (String) (classes.size() > i ? classes.get(i).get(DICTIONARY_VALUE_KEY) : null);
        String appName = (String) (appNames.size() > i ? appNames.get(i).get(DICTIONARY_VALUE_KEY) : null);
        AppLink.Target target = new AppLink.Target(packageName, className, url, appName);
        targets.add(target);
      }
    }

    Uri webUrl = destination;
    List<Map<String, Object>> webMapList = (List<Map<String, Object>>) appLinkDict.get("web");
    if (webMapList != null && webMapList.size() > 0) {
      Map<String, Object> webMap = webMapList.get(0);
      List<Map<String, Object>> urls = (List<Map<String, Object>>) webMap.get("url");
      if (urls != null && urls.size() > 0) {
        String webUrlString = (String) urls.get(0).get(DICTIONARY_VALUE_KEY);
        if (Arrays.asList("none", "").contains(webUrlString)) {
          webUrl = null;
        } else {
          webUrl = tryCreateUri(webUrlString);
        }
      }
    }
    return new AppLink(destination, targets, webUrl);
  }

  private static Uri tryCreateUri(String urlString) {
    if (urlString == null) {
      return null;
    }
    return Uri.parse(urlString);
  }

  private static String readFromConnection(URLConnection connection) throws IOException {
    InputStream stream = connection.getInputStream();
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read = 0;
      while ((read = stream.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      String charset = connection.getContentEncoding();
      if (charset == null) {
        String mimeType = connection.getContentType();
        String[] parts = mimeType.split(";");
        for (String part : parts) {
          part = part.trim();
          if (part.startsWith("charset=")) {
            charset = part.substring("charset=".length());
            break;
          }
        }
        if (charset == null) {
          charset = "UTF-8";
        }
      }
      return new String(output.toByteArray(), charset);
    } finally {
      stream.close();
    }
  }
}
