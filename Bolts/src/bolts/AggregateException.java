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

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates multiple {@code Throwable}s that may be thrown in the process of a task's execution.
 *
 * @see Task#whenAll(java.util.Collection)
 */
public class AggregateException extends Exception {
  private static final long serialVersionUID = 1L;

  private Throwable[] causes;

  /**
   * Constructs a new {@code AggregateException} with the current stack trace, the
   * specified detail message and the specified causes.
   *
   * The stacktrace will show that this {@code AggregateException} will be caused by the first
   * exception and all of the causes can be accessed via {@link #getCauses()}.
   *
   * @param detailMessage
   *            the detail message for this exception.
   * @param causes
   *            the causes of this exception.
   */
  public AggregateException(String detailMessage, Throwable[] causes) {
    super(detailMessage, causes != null && causes.length > 0 ? causes[0] : null);

    this.causes = causes != null && causes.length > 0 ? causes : null;
  }

  /**
   * @deprecated Please use {@link #AggregateException(String, Throwable[])} instead.
   */
  @Deprecated
  public AggregateException(List<Exception> errors) {
    this("There were multiple errors.", errors.toArray(new Exception[errors.size()]));
  }

  /**
   * @deprecated Please use {@link #getCauses()} instead.
   */
  @Deprecated
  public List<Exception> getErrors() {
    List<Exception> errors = new ArrayList<Exception>();
    if (causes == null) {
      return errors;
    }

    for (Throwable cause : causes) {
      if (cause instanceof Exception) {
        errors.add((Exception) cause);
      } else {
        errors.add(new Exception(cause));
      }
    }
    return errors;
  }

  /**
   * Returns the causes of this {@code AggregateException}, or {@code null} if there are no causes.
   */
  public Throwable[] getCauses() {
    return causes;
  }
}
