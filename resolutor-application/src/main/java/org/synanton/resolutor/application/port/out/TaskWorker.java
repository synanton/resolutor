package org.synanton.resolutor.application.port.out;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;

/** Executes one task using an external runtime or an in-process adapter. */
@FunctionalInterface
public interface TaskWorker {

  Result execute(Task task);

  enum Outcome {
    COMPLETED,
    PAUSED,
    FAILED
  }

  record Result(Outcome outcome, @Nullable Cursor cursor, @Nullable String failureReason) {
    public Result {
      Objects.requireNonNull(outcome, "outcome");
    }

    public static Result completed() {
      return new Result(Outcome.COMPLETED, null, null);
    }

    public static Result paused(Cursor cursor) {
      return new Result(Outcome.PAUSED, Objects.requireNonNull(cursor, "cursor"), null);
    }

    public static Result failed(String reason) {
      return new Result(Outcome.FAILED, null, Objects.requireNonNull(reason, "reason"));
    }
  }
}
