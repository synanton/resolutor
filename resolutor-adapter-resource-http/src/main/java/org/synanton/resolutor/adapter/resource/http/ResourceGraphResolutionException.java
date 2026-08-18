package org.synanton.resolutor.adapter.resource.http;

import org.synanton.resolutor.domain.task.TaskId;

/**
 * Thrown when the remote resource-graph service cannot produce a complete footprint. The planner
 * skips the task for the current tick rather than planning on a truncated resource set.
 */
public final class ResourceGraphResolutionException extends RuntimeException {

  /**
   * @param taskId task whose footprint could not be resolved
   * @param cause upstream or circuit-breaker failure
   */
  public ResourceGraphResolutionException(TaskId taskId, Throwable cause) {
    super("Resource resolution failed for task " + taskId, cause);
  }
}
