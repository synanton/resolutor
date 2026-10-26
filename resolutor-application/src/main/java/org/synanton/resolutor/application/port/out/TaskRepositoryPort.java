package org.synanton.resolutor.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Outbound port: persistence for tasks and their lifecycle state. */
public interface TaskRepositoryPort {

  /** Load up to {@code limit} {@code PENDING}/{@code PAUSED} tasks ordered by createdAt then id. */
  List<Task> loadBatchForPlanning(int limit);

  /** Load tasks in {@code states} whose {@code timeoutAt} is at or before {@code now}. */
  List<Task> loadExpired(Instant now, Set<TaskState> states);

  /** Load every task currently in one of {@code states}. */
  List<Task> loadByStates(Set<TaskState> states);

  /** Insert or update {@code task}. */
  void save(Task task);

  /** Find the task with {@code id}, or empty when no row exists. */
  Optional<Task> findById(TaskId id);

  /** Count tasks grouped by their current state. */
  Map<TaskState, Long> countByState();
}
