/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
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
