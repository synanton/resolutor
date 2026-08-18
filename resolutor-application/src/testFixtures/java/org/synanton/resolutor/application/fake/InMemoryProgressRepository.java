package org.synanton.resolutor.application.fake;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.ProgressSnapshot;
import org.synanton.resolutor.domain.task.TaskId;

/** Thread-safe in-memory {@link ProgressRepositoryPort} for unit tests. */
public class InMemoryProgressRepository implements ProgressRepositoryPort {

  private final Map<TaskId, ProgressSnapshot> store = new ConcurrentHashMap<>();

  @Override
  public Optional<ProgressSnapshot> findByTaskId(TaskId id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public void save(ProgressSnapshot snapshot) {
    store.put(snapshot.taskId(), snapshot);
  }

  @Override
  public Map<String, Long> inflightCountsByResourceClass() {
    return Map.of();
  }

  public void clear() {
    store.clear();
  }
}
