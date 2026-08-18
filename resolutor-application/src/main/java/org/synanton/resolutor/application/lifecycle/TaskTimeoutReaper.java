package org.synanton.resolutor.application.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Marks expired tasks {@code TIMEOUT} and returns leftover {@code STARTED}/{@code PROCESSING} rows
 * to a plannable state after a crash (see docs/design.md §19 and implementation-plan §7).
 */
public final class TaskTimeoutReaper {

  private static final Set<TaskState> REAPABLE =
      Set.of(TaskState.PENDING, TaskState.PAUSED, TaskState.STARTED, TaskState.PROCESSING);
  private static final Set<TaskState> IN_FLIGHT = Set.of(TaskState.STARTED, TaskState.PROCESSING);

  private static final System.Logger LOG = System.getLogger(TaskTimeoutReaper.class.getName());

  private final TaskRepositoryPort taskRepo;
  private final BackpressureManager backpressure;

  public TaskTimeoutReaper(TaskRepositoryPort taskRepo, BackpressureManager backpressure) {
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
    this.backpressure = Objects.requireNonNull(backpressure, "backpressure");
  }

  /**
   * Apply timeout and crash-recovery transitions using {@code now} as the deadline cutoff.
   *
   * @return counts of tasks timed out and tasks returned to PENDING/PAUSED
   */
  public Result run(Instant now) {
    Objects.requireNonNull(now, "now");
    int timedOut = reapExpired(now);
    int recovered = recoverOrphans();
    return new Result(timedOut, recovered);
  }

  /** Number of tasks moved to TIMEOUT and number recovered to a plannable state. */
  public record Result(int timedOut, int recovered) {}

  private int reapExpired(Instant now) {
    List<Task> expired = taskRepo.loadExpired(now, REAPABLE);
    for (Task task : expired) {
      taskRepo.save(task.withState(TaskState.TIMEOUT));
      if (IN_FLIGHT.contains(task.state())) {
        backpressure.onCompleted(task.topResource().resourceClass());
      }
    }
    if (!expired.isEmpty()) {
      LOG.log(System.Logger.Level.INFO, "Reaped {0} timed-out task(s)", expired.size());
    }
    return expired.size();
  }

  private int recoverOrphans() {
    List<Task> orphans = taskRepo.loadByStates(IN_FLIGHT);
    for (Task task : orphans) {
      TaskState next = task.cursor() == null ? TaskState.PENDING : TaskState.PAUSED;
      taskRepo.save(task.withState(next));
      backpressure.onCompleted(task.topResource().resourceClass());
    }
    if (!orphans.isEmpty()) {
      LOG.log(
          System.Logger.Level.INFO,
          "Recovered {0} orphaned STARTED/PROCESSING task(s) for replanning",
          orphans.size());
    }
    return orphans.size();
  }
}
