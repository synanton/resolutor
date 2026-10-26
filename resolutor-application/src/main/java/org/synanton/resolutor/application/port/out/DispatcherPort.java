package org.synanton.resolutor.application.port.out;

import java.util.List;
import java.util.Objects;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

/** Output port for executing a planned set of task groups. */
public interface DispatcherPort {

  List<GroupResult> runPlan(ExecutionPlan plan);

  GroupResult dispatch(SequentialGroup group);

  /** Aggregate result for one dependency-connected group. */
  record GroupResult(String componentId, List<TaskResult> taskResults) {
    public GroupResult {
      Objects.requireNonNull(componentId, "componentId");
      taskResults = List.copyOf(taskResults);
    }
  }

  /** Result for an individual dispatched task. */
  record TaskResult(TaskId taskId, boolean success, String failureReason) {
    public static TaskResult ok(TaskId taskId) {
      return new TaskResult(taskId, true, null);
    }

    public static TaskResult failed(TaskId taskId, String reason) {
      return new TaskResult(taskId, false, Objects.requireNonNull(reason, "reason"));
    }
  }
}
