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

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A simple implementation for an App Link resolver that parses the HTML containing App Link metadata
 * using {@link android.text.Html#fromHtml}.
 */
public class HtmlAppLinkResolver implements AppLinkResolver {
  /**
   * Creates an {@code HtmlAppLinkResolver}.
   */
  public HtmlAppLinkResolver() {
  }

  private static final String META_TAG = "meta";
  private static final String META_TAG_PREFIX = "al:";
  private static final String META_ATTR_PROPERTY = "property";
  private static final String META_ATTR_CONTENT = "content";

  @Override
  public Task<AppLink> getAppLinkFromUrlInBackground(final Uri url) {
    return ResolverUtils.fetchUrl(url)
        .onSuccess(new Continuation<ResolverUtils.StringEntity, JSONArray>() {
          @Override
          public JSONArray then(Task<ResolverUtils.StringEntity> task) throws Exception {
            return parseMetaTags(task.getResult().getContent());
          }
        }).onSuccess(new Continuation<JSONArray, AppLink>() {
      @Override
      public AppLink then(Task<JSONArray> task) throws Exception {
        return ResolverUtils.parseAppLinkFromAlData(task.getResult(), url);
      }
    });
  }

  private static JSONArray parseMetaTags(String html) throws Exception {
    final MetaTagHandler metaTagHandler = new MetaTagHandler();
    Html.fromHtml(html, metaTagHandler, metaTagHandler);

    return metaTagHandler.getJsonArray();
  }

  private static class MetaTagHandler extends DefaultHandler implements Html.TagHandler, Html.ImageGetter {

    private JSONArray jsonArray = new JSONArray();

    @Override
    public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
      xmlReader.setContentHandler(this);
    }

    @Override
    public Drawable getDrawable(String source) {
      return null;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (META_TAG.equalsIgnoreCase(localName)) {
        addMetaElement(attributes);
      }
    }

    public JSONArray getJsonArray() {
      return jsonArray;
    }

    private void addMetaElement(Attributes attributes) throws SAXException {
      String property = attributes.getValue(META_ATTR_PROPERTY);

      if (property != null && property.startsWith(META_TAG_PREFIX)) {
        String content = attributes.getValue(META_ATTR_CONTENT);

        JSONObject metaObject = new JSONObject();
        try {
          metaObject.put(META_ATTR_PROPERTY, property);
          if (content != null) {
            metaObject.put(META_ATTR_CONTENT, content);
          }
        } catch (JSONException e) {
          throw new SAXException(e);
        }

        jsonArray.put(metaObject);
      }
    }

  }
}
