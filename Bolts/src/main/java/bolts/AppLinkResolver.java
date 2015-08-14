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

/**
 * Implement this interface to provide an alternate strategy for resolving App Links that may
 * include pre-fetching, caching, or querying for App Link data from an index provided by a
 * service provider.
 */
public interface AppLinkResolver {
  /**
   * Asynchronously resolves App Link data for a given URL.
   *
   * @param url the URL to resolve into an App Link.
   * @return the {@link bolts.AppLink} for the given URL.
   */
  public Task<AppLink> getAppLinkFromUrlInBackground(Uri url);
}
