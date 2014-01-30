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

/**
 * Provides a class that can be used for capturing variables in an anonymous class implementation.
 * 
 * @param <T>
 */
public class Capture<T> {
  private T value;

  public Capture() {
  }

  public Capture(T value) {
    this.value = value;
  }

  public T get() {
    return value;
  }

  public void set(T value) {
    this.value = value;
  }
}
