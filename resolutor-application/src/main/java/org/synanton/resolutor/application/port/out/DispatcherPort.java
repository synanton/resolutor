package org.synanton.resolutor.application.port.out;

import java.util.List;
import java.util.Objects;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

/** Outbound port: executes planned sequential groups and reports per-task outcomes. */
public interface DispatcherPort {

  /** Execute every group in {@code plan} and return the aggregate results. */
  List<GroupResult> runPlan(ExecutionPlan plan);

  /** Execute a single group sequentially (in-process) or publish it (Kafka). */
  GroupResult dispatch(SequentialGroup group);

  /** Outcome of dispatching one sequential group. */
  record GroupResult(String componentId, List<TaskResult> taskResults) {

    public GroupResult {
      Objects.requireNonNull(componentId, "componentId");
      taskResults = List.copyOf(Objects.requireNonNull(taskResults, "taskResults"));
    }
  }

  /** Result for an individual dispatched task. */
  /** Outcome of dispatching one task. */
  record TaskResult(TaskId taskId, boolean success, @Nullable String failureReason) {
    public static TaskResult ok(TaskId taskId) {
      return new TaskResult(taskId, true, null);

    public TaskResult {
      Objects.requireNonNull(taskId, "taskId");
    }

    public static TaskResult failed(TaskId taskId, String reason) {
      return new TaskResult(taskId, false, Objects.requireNonNull(reason, "reason"));
    /** Mark {@code id} as dispatched successfully. */
    public static TaskResult ok(TaskId id) {
      return new TaskResult(id, true, null);
    }

    /** Mark {@code id} as failed with a human-readable {@code reason}. */
    public static TaskResult failed(TaskId id, String reason) {
      return new TaskResult(id, false, Objects.requireNonNull(reason, "reason"));
    }
  }
}
