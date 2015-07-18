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
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * A reference implementation for an App Link resolver that uses a hidden
 * {@link android.webkit.WebView} to parse the HTML containing App Link metadata.
 */
public class WebViewAppLinkResolver implements AppLinkResolver {
  private final Context context;

  /**
   * Creates a WebViewAppLinkResolver.
   *
   * @param context the context in which to create the hidden {@link android.webkit.WebView}.
   */
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

  @Override
  public Task<AppLink> getAppLinkFromUrlInBackground(final Uri url) {
    //final Capture<String> content = new Capture<String>();
    //final Capture<String> contentType = new Capture<String>();
    return ResolverUtils.fetchUrl(url)
        .onSuccessTask(new Continuation<ResolverUtils.StringEntity, Task<JSONArray>>() {
      @Override
      public Task<JSONArray> then(Task<ResolverUtils.StringEntity> task) throws Exception {
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
            try {
              tcs.trySetResult(new JSONArray(value));
            } catch (JSONException e) {
              tcs.trySetError(e);
            }
          }
        }, "boltsWebViewAppLinkResolverResult");

        String inferredContentType = null;
        String contentType = task.getResult().getContentType();
        if (contentType != null) {
          inferredContentType = contentType.split(";")[0];
        }
        String content = task.getResult().getContent();
        webView.loadDataWithBaseURL(url.toString(),
            content,
            inferredContentType,
            null,
            null);
        return tcs.getTask();
      }
    }, Task.UI_THREAD_EXECUTOR).onSuccess(new Continuation<JSONArray, AppLink>() {
      @Override
      public AppLink then(Task<JSONArray> task) throws Exception {
        return ResolverUtils.parseAppLinkFromAlData(task.getResult(), url);
      }
    });
  }
}
