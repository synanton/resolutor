package org.synanton.resolutor.application.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Thread-safe in-memory {@link TaskRepositoryPort} for unit tests. */
public final class InMemoryTaskRepository implements TaskRepositoryPort {

  private final Map<TaskId, Task> store = new ConcurrentHashMap<>();

  @Override
  public List<Task> loadBatchForPlanning(int limit) {
    return store.values().stream()
        .filter(t -> t.state() == TaskState.PENDING || t.state() == TaskState.PAUSED)
        .sorted(
            (a, b) -> {
              int cmp = a.createdAt().compareTo(b.createdAt());
              return cmp != 0
                  ? cmp
                  : a.id().value().toString().compareTo(b.id().value().toString());
            })
        .limit(limit)
        .toList();
  }

  @Override
  public List<Task> loadExpired(Instant now, Set<TaskState> states) {
    return store.values().stream()
        .filter(t -> states.contains(t.state()))
        .filter(t -> t.timeoutAt() != null && !t.timeoutAt().isAfter(now))
        .toList();
  }

  @Override
  public List<Task> loadByStates(Set<TaskState> states) {
    return store.values().stream().filter(t -> states.contains(t.state())).toList();
  }

  @Override
  public void save(Task task) {
    store.put(task.id(), task);
  }

  @Override
  public Optional<Task> findById(TaskId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public Map<TaskState, Long> countByState() {
    Map<TaskState, Long> counts = new EnumMap<>(TaskState.class);
    for (Task task : store.values()) {
      counts.merge(task.state(), 1L, Long::sum);
    }
    return counts;
  }

  public List<Task> all() {
    return new ArrayList<>(store.values());
  }

  public void clear() {
    store.clear();
  }
}
