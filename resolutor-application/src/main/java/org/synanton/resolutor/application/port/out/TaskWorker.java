package org.synanton.resolutor.application.port.out;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;

/**
 * Outbound port: performs the domain action for one task invocation. The dispatcher owns state
 * transitions; implementations must not persist {@code TaskState} themselves.
 */
public interface TaskWorker {

  /** Execute the business action for {@code task}. */
  Result execute(Task task);

  /** Terminal or resumable outcome of a single worker invocation. */
  enum Outcome {
    COMPLETED,
    PAUSED,
    FAILED
  }

  /**
   * Result of a worker invocation.
   *
   * @param outcome whether the task completed, paused with a cursor, or failed
   * @param cursor resumable cursor when {@code outcome} is {@link Outcome#PAUSED}, otherwise null
   * @param failureReason human-readable reason when {@code outcome} is {@link Outcome#FAILED}
   */
  record Result(Outcome outcome, @Nullable Cursor cursor, @Nullable String failureReason) {

    public Result {
      Objects.requireNonNull(outcome, "outcome");
    }

    /** Mark the task completed. */
    public static Result completed() {
      return new Result(Outcome.COMPLETED, null, null);
    }

    /** Pause the task at {@code cursor} so it can be resumed on a later planning tick. */
    public static Result paused(Cursor cursor) {
      return new Result(Outcome.PAUSED, Objects.requireNonNull(cursor, "cursor"), null);
    }

    /** Fail the task with a human-readable {@code reason}. */
    public static Result failed(String reason) {
      return new Result(Outcome.FAILED, null, Objects.requireNonNull(reason, "reason"));
    }
  }
}
