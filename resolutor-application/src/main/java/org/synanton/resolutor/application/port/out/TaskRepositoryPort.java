package org.synanton.resolutor.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Persistence port for task lifecycle and planning queries. */
public interface TaskRepositoryPort {
  List<Task> loadBatchForPlanning(int limit);

  List<Task> loadExpired(Instant now, Set<TaskState> states);

  List<Task> loadByStates(Set<TaskState> states);

  void save(Task task);

  Optional<Task> findById(TaskId id);

  Map<TaskState, Long> countByState();
}
