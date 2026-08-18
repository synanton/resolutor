package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.synanton.resolutor.application.port.out.ProgressSnapshot;
import org.synanton.resolutor.domain.task.TaskId;

/** Bidirectional mapping between {@link ProgressSnapshot} and {@link TaskProgressEntity}. */
public final class TaskProgressEntityMapper {

  public void merge(TaskProgressEntity entity, ProgressSnapshot snapshot) {
    entity.setTaskId(snapshot.taskId().value());
    entity.setTotalCount(snapshot.totalCount());
    entity.setSuccessCount(snapshot.successCount());
    entity.setFailedCount(snapshot.failedCount());
    entity.setUpdatedAt(Instant.now());
    if (entity.getVersion() == 0L && snapshot.version() > 0L) {
      entity.setVersion(snapshot.version());
    }
  }

  public ProgressSnapshot toDomain(TaskProgressEntity entity) {
    UUID id = Objects.requireNonNull(entity.getTaskId(), "entity.taskId");
    return new ProgressSnapshot(
        TaskId.of(id),
        entity.getTotalCount(),
        entity.getSuccessCount(),
        entity.getFailedCount(),
        entity.getVersion());
  }
}
