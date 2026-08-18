package org.synanton.resolutor.application.port.in;

import org.synanton.resolutor.domain.task.TaskId;

/** Inbound port: report task progress and query task status. */
public interface ProgressPort {

  /** Apply a monotonic count delta to the progress snapshot for {@code id}. */
  void updateProgress(TaskId id, ProgressDelta delta);

  /** Force the task into {@code COMPLETED}, preserving any existing cursor. */
  void forceComplete(TaskId id);

  /**
   * Return the current state and progress view for {@code id}.
   *
   * @throws IllegalArgumentException if the task does not exist
   */
  TaskStatusView status(TaskId id);
}
