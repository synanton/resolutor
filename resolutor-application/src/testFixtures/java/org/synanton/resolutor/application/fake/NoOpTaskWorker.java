package org.synanton.resolutor.application.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.task.Task;

/**
 * Records every task passed to {@link #execute(Task)} and returns {@link Result#completed()} by
 * default. Behaviour per-task can be overridden via {@link #failNext(int)}.
 */
public final class NoOpTaskWorker implements TaskWorker {

  private final List<Task> executed = new CopyOnWriteArrayList<>();
  private int failuresRemaining;

  @Override
  public Result execute(Task task) {
    executed.add(task);
    if (failuresRemaining > 0) {
      failuresRemaining--;
      return Result.failed("test-forced failure");
    }
    return Result.completed();
  }

  public List<Task> executed() {
    return new ArrayList<>(executed);
  }

  public void failNext(int n) {
    this.failuresRemaining = n;
  }

  public void clear() {
    executed.clear();
    failuresRemaining = 0;
  }
}
