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

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

public class CaptureTest extends InstrumentationTestCase {
  public void testStringRepresentation() {
    Capture<Integer> capture = new Capture<Integer>(42);
    assertEquals("42", capture.toString());
  }
}
