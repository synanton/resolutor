package org.synanton.resolutor.adapter.persistence.jpa;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.ProgressSnapshot;
import org.synanton.resolutor.domain.task.TaskId;

/** JPA-backed {@link ProgressRepositoryPort}. */
public final class ProgressRepositoryJpaAdapter implements ProgressRepositoryPort {

  private final TaskProgressJpaRepository progressJpa;
  private final TaskJpaRepository taskJpa;
  private final TaskProgressEntityMapper mapper;

  public ProgressRepositoryJpaAdapter(
      TaskProgressJpaRepository progressJpa,
      TaskJpaRepository taskJpa,
      TaskProgressEntityMapper mapper) {
    this.progressJpa = Objects.requireNonNull(progressJpa, "progressJpa");
    this.taskJpa = Objects.requireNonNull(taskJpa, "taskJpa");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public Optional<ProgressSnapshot> findByTaskId(TaskId id) {
    return progressJpa.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public void save(ProgressSnapshot snapshot) {
    TaskProgressEntity entity =
        progressJpa.findById(snapshot.taskId().value()).orElseGet(TaskProgressEntity::new);
    mapper.merge(entity, snapshot);
    progressJpa.save(entity);
  }

  @Override
  public Map<String, Long> inflightCountsByResourceClass() {
    Map<String, Long> counts = new HashMap<>();
    for (Object[] row : taskJpa.aggregateInflightByResourceClass()) {
      String cls = (String) row[0];
      Long count = ((Number) row[1]).longValue();
      counts.put(cls, count);
    }
    return counts;
  }
}
