package org.synanton.resolutor.application.port.in;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Read model for task status + optional progress counters. */
public record TaskStatusView(TaskId id, TaskState state, @Nullable ProgressView progress) {

  public TaskStatusView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(state, "state");
  }

  /** Counters reported by the worker. Absent until the task begins processing. */
  public record ProgressView(long totalCount, long successCount, long failedCount) {

    public long pendingCount() {
      return Math.max(0, totalCount - successCount - failedCount);
    }
  }
}
