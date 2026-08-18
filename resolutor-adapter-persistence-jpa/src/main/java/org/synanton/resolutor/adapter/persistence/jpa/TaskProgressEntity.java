package org.synanton.resolutor.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Row-shaped image of a per-task progress snapshot. */
@Entity
@Table(name = "task_progress")
public class TaskProgressEntity {

  @Id
  @Column(name = "task_id", nullable = false, updatable = false)
  private @Nullable UUID taskId;

  @Column(name = "total_count", nullable = false)
  private long totalCount;

  @Column(name = "success_count", nullable = false)
  private long successCount;

  @Column(name = "failed_count", nullable = false)
  private long failedCount;

  @Column(name = "updated_at", nullable = false)
  private @Nullable Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public TaskProgressEntity() {}

  public @Nullable UUID getTaskId() {
    return taskId;
  }

  public void setTaskId(UUID taskId) {
    this.taskId = taskId;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public long getSuccessCount() {
    return successCount;
  }

  public void setSuccessCount(long successCount) {
    this.successCount = successCount;
  }

  public long getFailedCount() {
    return failedCount;
  }

  public void setFailedCount(long failedCount) {
    this.failedCount = failedCount;
  }

  public @Nullable Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
