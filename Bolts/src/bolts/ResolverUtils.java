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

public class ResolverUtils {

  private static final String PREFER_HEADER = "Prefer-Html-Meta-Tags";
  private static final String META_TAG_PREFIX = "al";

  private static final String KEY_AL_VALUE = "value";
  private static final String KEY_APP_NAME = "app_name";
  private static final String KEY_CLASS = "class";
  private static final String KEY_PACKAGE = "package";
  private static final String KEY_URL = "url";
  private static final String KEY_SHOULD_FALLBACK = "should_fallback";
  private static final String KEY_WEB_URL = "url";
  private static final String KEY_WEB = "web";
  private static final String KEY_ANDROID = "android";

  private ResolverUtils() {
  }

  /**
   * Fetch the content from the specified URL.
   *
   * @param url the URL to fetch the content from.
   * @return the page content as a {@code StringEntity}.
   */
  public static Task<StringEntity> fetchUrl(final Uri url) {
    return Task.callInBackground(new Callable<StringEntity>() {
      @Override
      public StringEntity call() throws Exception {
        URL currentURL = new URL(url.toString());
        URLConnection connection = null;

        while (currentURL != null) {
          // Fetch the content at the given URL.
          connection = currentURL.openConnection();
          if (connection instanceof HttpURLConnection) {
            // Unfortunately, this doesn't actually follow redirects if they go from http->https,
            // so we have to do that manually.
            ((HttpURLConnection) connection).setInstanceFollowRedirects(true);
          }
          connection.setRequestProperty(PREFER_HEADER, META_TAG_PREFIX);
          connection.connect();

          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            if (httpConnection.getResponseCode() >= 300 && httpConnection.getResponseCode() < 400) {
              currentURL = new URL(httpConnection.getHeaderField("Location"));
              httpConnection.disconnect();
            } else {
              currentURL = null;
            }
          } else {
            currentURL = null;
          }
        }

        try {
          String content = readFromConnection(connection);
          String contentType = connection.getContentType();
          return new StringEntity(content, contentType);
        } finally {
          if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).disconnect();
          }
        }
      }
    });
  }

  /**
   * Parse an AppLink from the specified al data specified in a JSON array.
   *
   * @param dataArray the al meta tag data.
   * @param url the URL of the AppLink.
   * @return the AppLink.
   * @throws JSONException if there is an error parsing the JSON array.
   */
  public static AppLink parseAppLinkFromAlData(JSONArray dataArray, Uri url) throws JSONException {
    Map<String, Object> alData = parseAlData(dataArray);
    return makeAppLinkFromAlData(alData, url);
  }

  /**
   * Builds up a data structure filled with the app link data from the meta tags on a page.
   * The structure of this object is a dictionary where each key holds an array of app link
   * data dictionaries.  Values are stored in a key called "_value".
   */
  private static Map<String, Object> parseAlData(JSONArray dataArray) throws JSONException {
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
        List<Map<String, Object>> children =
            (List<Map<String, Object>>) root.get(nameComponents[j]);
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
          root.put(KEY_AL_VALUE, null);
        } else {
          root.put(KEY_AL_VALUE, tag.getString("content"));
        }
      }
    }
    return al;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getAlList(Map<String, Object> map, String key) {
    List<Map<String, Object>> result = (List<Map<String, Object>>) map.get(key);
    if (result == null) {
      return Collections.emptyList();
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static AppLink makeAppLinkFromAlData(Map<String, Object> appLinkDict, Uri destination) {
    List<AppLink.Target> targets = new ArrayList<AppLink.Target>();
    List<Map<String, Object>> platformMapList =
        (List<Map<String, Object>>) appLinkDict.get(KEY_ANDROID);
    if (platformMapList == null) {
      platformMapList = Collections.emptyList();
    }
    for (Map<String, Object> platformMap : platformMapList) {
      // The schema requires a single url/package/app name/class, but we could find multiple
      // of them. We'll make a best effort to interpret this data.
      List<Map<String, Object>> urls = getAlList(platformMap, KEY_URL);
      List<Map<String, Object>> packages = getAlList(platformMap, KEY_PACKAGE);
      List<Map<String, Object>> classes = getAlList(platformMap, KEY_CLASS);
      List<Map<String, Object>> appNames = getAlList(platformMap, KEY_APP_NAME);

      int maxCount = Math.max(urls.size(),
          Math.max(packages.size(), Math.max(classes.size(), appNames.size())));
      for (int i = 0; i < maxCount; i++) {
        String urlString = (String) (urls.size() > i ?
            urls.get(i).get(KEY_AL_VALUE) : null);
        Uri url = tryCreateUrl(urlString);
        String packageName = (String) (packages.size() > i ?
            packages.get(i).get(KEY_AL_VALUE) : null);
        String className = (String) (classes.size() > i ?
            classes.get(i).get(KEY_AL_VALUE) : null);
        String appName = (String) (appNames.size() > i ?
            appNames.get(i).get(KEY_AL_VALUE) : null);
        AppLink.Target target = new AppLink.Target(packageName, className, url, appName);
        targets.add(target);
      }
    }

    Uri webUrl = destination;
    List<Map<String, Object>> webMapList = (List<Map<String, Object>>) appLinkDict.get(KEY_WEB);
    if (webMapList != null && webMapList.size() > 0) {
      Map<String, Object> webMap = webMapList.get(0);
      List<Map<String, Object>> urls = (List<Map<String, Object>>) webMap.get(KEY_WEB_URL);
      List<Map<String, Object>> shouldFallbacks =
          (List<Map<String, Object>>) webMap.get(KEY_SHOULD_FALLBACK);
      if (shouldFallbacks != null && shouldFallbacks.size() > 0) {
        String shouldFallbackString = (String) shouldFallbacks.get(0).get(KEY_AL_VALUE);
        if (Arrays.asList("no", "false", "0").contains(shouldFallbackString.toLowerCase())) {
          webUrl = null;
        }
      }
      if (webUrl != null && urls != null && urls.size() > 0) {
        String webUrlString = (String) urls.get(0).get(KEY_AL_VALUE);
        webUrl = tryCreateUrl(webUrlString);
      }
    }
    return new AppLink(destination, targets, webUrl);
  }

  private static Uri tryCreateUrl(String urlString) {
    if (urlString == null) {
      return null;
    }
    return Uri.parse(urlString);
  }

  private static String readFromConnection(URLConnection connection) throws IOException {
    InputStream stream;
    if (connection instanceof HttpURLConnection) {
      HttpURLConnection httpConnection = (HttpURLConnection) connection;
      try {
        stream = connection.getInputStream();
      } catch (Exception e) {
        stream = httpConnection.getErrorStream();
      }
    } else {
      stream = connection.getInputStream();
    }
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
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

  /**
   * Represents an HTML entity (content) as a string.
   */
  public static class StringEntity {

    private String content;
    private String contentType;

    /**
     * Create a new {@code StringEntity}.
     * @param content the content, as a UTF-8 string.
     * @param contentType the content type.
     */
    public StringEntity(String content, String contentType) {
      this.content = content;
      this.contentType = contentType;
    }

    /**
     * @return the content as a UTF-8 string.
     */
    public String getContent() {
      return content;
    }

    /**
     * @return the content type of the content.
     */
    public String getContentType() {
      return contentType;
    }
  }

}
