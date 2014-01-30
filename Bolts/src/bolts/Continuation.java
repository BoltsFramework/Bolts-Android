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
 * A function to be called after a task completes.
 * @see Task
 */
public interface Continuation<TTaskResult, TContinuationResult> {
  TContinuationResult then(Task<TTaskResult> task) throws Exception;
}
