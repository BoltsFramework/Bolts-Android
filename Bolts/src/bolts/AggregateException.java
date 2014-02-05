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

import java.util.List;

/**
 * Aggregates Exceptions that may be thrown in the process of a task's execution.
 */
public class AggregateException extends Exception {
  private static final long serialVersionUID = 1L;

  private List<Exception> errors;

  public AggregateException(List<Exception> errors) {
    super("There were multiple errors.");

    this.errors = errors;
  }

  /**
   * Returns the list of errors that this exception encapsulates.
   */
  public List<Exception> getErrors() {
    return errors;
  }
}
