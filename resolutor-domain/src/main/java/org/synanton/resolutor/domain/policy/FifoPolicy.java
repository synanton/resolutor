package org.synanton.resolutor.domain.policy;

import java.util.Comparator;
import java.util.List;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/** Orders tasks by {@code createdAt}, oldest first. Ties broken by {@code TaskId} string order. */
public final class FifoPolicy implements OrderingPolicy {

  public static final FifoPolicy INSTANCE = new FifoPolicy();

  private FifoPolicy() {}

  @Override
  public List<TaskId> order(List<Task> tasks) {
    return tasks.stream()
        .sorted(Comparator.comparing(Task::createdAt).thenComparing(t -> t.id().value().toString()))
        .map(Task::id)
        .toList();
  }

  @Override
  public String name() {
    return "FIFO";
  }
}
