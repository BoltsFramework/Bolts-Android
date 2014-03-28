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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public final class AppLinks {
  static final String APPLINK_DATA_KEY_NAME = "al_applink_data";
  static final String EXTRAS_KEY_NAME = "extras";
  static final String TARGET_KEY_NAME = "target_url";

  public static Bundle getAppLinkData(Intent intent) {
    return intent.getBundleExtra(APPLINK_DATA_KEY_NAME);
  }

  public static Bundle getAppLinkExtras(Intent intent) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData == null) {
      return null;
    }
    return appLinkData.getBundle(EXTRAS_KEY_NAME);
  }

  public static Uri getTargetUrl(Intent intent) {
    Bundle appLinkData = getAppLinkData(intent);
    if (appLinkData != null) {
      return Uri.parse(appLinkData.getString(TARGET_KEY_NAME));
    }
    return intent.getData();
  }
}
