package org.synanton.resolutor.application.port.out;

import java.util.Objects;
import org.synanton.resolutor.domain.task.TaskId;

/** Persisted progress counters for a single task. */
public record ProgressSnapshot(
    TaskId taskId, long totalCount, long successCount, long failedCount, long version) {

  public ProgressSnapshot {
    Objects.requireNonNull(taskId, "taskId");
    if (totalCount < 0 || successCount < 0 || failedCount < 0 || version < 0) {
      throw new IllegalArgumentException("progress counters and version must be >= 0");
    }
  }

  /** An empty snapshot for a task that has not reported any progress yet. */
  public static ProgressSnapshot initial(TaskId taskId) {
    return new ProgressSnapshot(taskId, 0L, 0L, 0L, 0L);
  }
}
