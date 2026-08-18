package org.synanton.resolutor.domain.policy;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/** Orders tasks by {@code timeoutAt}, earliest deadline first. Tasks with no deadline go last. */
public final class DeadlinePolicy implements OrderingPolicy {

  public static final DeadlinePolicy INSTANCE = new DeadlinePolicy();

  private DeadlinePolicy() {}

  @Override
  public List<TaskId> order(List<Task> tasks) {
    return tasks.stream()
        .sorted(
            (a, b) -> {
              @Nullable Instant ta = a.timeoutAt();
              @Nullable Instant tb = b.timeoutAt();
              if (ta == null && tb == null) return 0;
              if (ta == null) return 1;
              if (tb == null) return -1;
              return ta.compareTo(tb);
            })
        .map(Task::id)
        .toList();
  }

  @Override
  public String name() {
    return "DEADLINE";
  }
}
