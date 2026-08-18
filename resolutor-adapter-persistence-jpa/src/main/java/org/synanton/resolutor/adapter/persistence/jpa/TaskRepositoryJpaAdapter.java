package org.synanton.resolutor.adapter.persistence.jpa;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** JPA-backed {@link TaskRepositoryPort}. */
public final class TaskRepositoryJpaAdapter implements TaskRepositoryPort {

  private final TaskJpaRepository jpa;
  private final TaskEntityMapper mapper;

  public TaskRepositoryJpaAdapter(TaskJpaRepository jpa, TaskEntityMapper mapper) {
    this.jpa = Objects.requireNonNull(jpa, "jpa");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  /**
   * Load up to {@code limit} PENDING/PAUSED tasks ordered by createdAt then id. Returns an empty
   * list when {@code limit} is not positive.
   */
  @Override
  public List<Task> loadBatchForPlanning(int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return jpa.findEligibleForPlanning(PageRequest.of(0, limit)).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<Task> loadExpired(Instant now, Set<TaskState> states) {
    if (states.isEmpty()) {
      return List.of();
    }
    List<String> names = states.stream().map(Enum::name).toList();
    return jpa.findExpired(names, now).stream().map(mapper::toDomain).toList();
  }

  @Override
  public List<Task> loadByStates(Set<TaskState> states) {
    if (states.isEmpty()) {
      return List.of();
    }
    List<String> names = states.stream().map(Enum::name).toList();
    return jpa.findByStateIn(names).stream().map(mapper::toDomain).toList();
  }

  /** Insert or merge {@code task} onto the matching row (or a new entity). */
  @Override
  public void save(Task task) {
    TaskEntity entity = jpa.findById(task.id().value()).orElseGet(TaskEntity::new);
    mapper.merge(entity, task);
    jpa.save(entity);
  }

  /** Load the domain task for {@code id}, or empty if no row exists. */
  @Override
  public Optional<Task> findById(TaskId id) {
    return jpa.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public Map<TaskState, Long> countByState() {
    Map<TaskState, Long> counts = new EnumMap<>(TaskState.class);
    for (Object[] row : jpa.countByState()) {
      TaskState state = TaskState.valueOf((String) row[0]);
      counts.put(state, ((Number) row[1]).longValue());
    }
    return counts;
  }
}
