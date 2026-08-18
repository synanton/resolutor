package org.synanton.resolutor.domain.plan;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Immutable, replayable schedule produced by the planning pipeline.
 *
 * <p>This is the stable contract between the planner and the dispatcher - analogous to LLVM IR in a
 * compiler. It is platform-agnostic, optimisable, and reproducible. See docs/design.md §9.
 *
 * <p>{@code groups} run concurrently. Within each group, {@code waves} run in order; tasks in the
 * same wave may run in parallel.
 */
public record ExecutionPlan(
    PlanId id,
    Instant generatedAt,
    String plannerVersion,
    Duration planningDuration,
    String orderPolicy,
    List<SequentialGroup> groups,
    PlanMetrics metrics,
    Map<TaskId, String> taskToComponent) {

  public ExecutionPlan {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(generatedAt, "generatedAt");
    Objects.requireNonNull(plannerVersion, "plannerVersion");
    Objects.requireNonNull(planningDuration, "planningDuration");
    Objects.requireNonNull(orderPolicy, "orderPolicy");
    groups = List.copyOf(groups);
    Objects.requireNonNull(metrics, "metrics");
    taskToComponent = Map.copyOf(taskToComponent);
  }

  public int totalGroups() {
    return groups.size();
  }
}
