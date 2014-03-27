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

import java.util.Collections;
import java.util.List;

public class AppLink {
  public static class Target {
    private final Uri url;
    private final String packageName;
    private final String className;
    private final String appName;

    public Target(String packageName, String className, Uri url, String appName) {
      if (packageName == null) {
        throw new IllegalArgumentException("Android AppLinkTargets must have a packageName");
      }
      this.packageName = packageName;
      this.className = className;
      this.url = url;
      this.appName = appName;
    }

    public Uri getUrl() {
      return url;
    }

    public String getAppName() {
      return appName;
    }

    public String getClassName() {
      return className;
    }

    public String getPackageName() {
      return packageName;
    }
  }


  private Uri sourceUrl;
  private List<Target> targets;
  private Uri webUrl;

  public AppLink(Uri sourceUrl, List<Target> targets, Uri webUrl) {
    this.sourceUrl = sourceUrl;
    if (targets == null) {
      targets = Collections.emptyList();
    }
    this.targets = targets;
    this.webUrl = webUrl;
  }

  public Uri getSourceUrl() {
    return sourceUrl;
  }

  public List<Target> getTargets() {
    return targets;
  }

  public Uri getWebUrl() {
    return webUrl;
  }
}
