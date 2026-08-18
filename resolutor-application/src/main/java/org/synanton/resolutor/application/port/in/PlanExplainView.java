package org.synanton.resolutor.application.port.in;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Diagnostics derived from a stored {@link ExecutionPlan} (design.md §9). Density and degree use
 * {@code conflictsDetected} as edge count and {@code totalTasks} as vertex count.
 */
public record PlanExplainView(
    PlanId id,
    PlanMetrics metrics,
    double conflictDensity,
    double averageDegree,
    Map<TaskId, String> taskToComponent) {

  public PlanExplainView {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(metrics, "metrics");
    taskToComponent = Map.copyOf(taskToComponent);
  }

  /** Build explain metrics from an already-compiled plan. */
  public static PlanExplainView from(ExecutionPlan plan) {
    Objects.requireNonNull(plan, "plan");
    int vertices = plan.metrics().totalTasks();
    int edges = plan.metrics().conflictsDetected();
    double density = vertices < 2 ? 0.0 : (2.0 * edges) / ((double) vertices * (vertices - 1));
    double averageDegree = vertices == 0 ? 0.0 : (2.0 * edges) / vertices;
    return new PlanExplainView(
        plan.id(), plan.metrics(), density, averageDegree, plan.taskToComponent());
  }

  public UUID idValue() {
    return id.value();
  }
}
