package org.synanton.resolutor.adapter.web.dto;

import java.util.Map;
import java.util.stream.Collectors;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.TaskId;

/** Maps a domain {@link ExecutionPlan} to the wire-format {@link ExecutionPlanResponse}. */
public final class ExecutionPlanDtoMapper {

  private ExecutionPlanDtoMapper() {}

  public static ExecutionPlanResponse toResponse(ExecutionPlan plan) {
    return new ExecutionPlanResponse(
        plan.id().value(),
        plan.generatedAt(),
        plan.plannerVersion(),
        plan.planningDuration(),
        plan.orderPolicy(),
        plan.groups().stream().map(ExecutionPlanDtoMapper::toGroup).toList(),
        toMetrics(plan.metrics()),
        toTaskToComponent(plan.taskToComponent()));
  }

  private static ExecutionPlanResponse.SequentialGroupBody toGroup(SequentialGroup g) {
    return new ExecutionPlanResponse.SequentialGroupBody(
        g.componentId(),
        g.orderedTasks().stream().map(TaskId::value).toList(),
        g.waves().stream().map(ExecutionPlanDtoMapper::toWave).toList());
  }

  private static ExecutionPlanResponse.ColourWaveBody toWave(ColourWave wave) {
    return new ExecutionPlanResponse.ColourWaveBody(
        wave.colour(), wave.taskIds().stream().map(TaskId::value).toList());
  }

  private static ExecutionPlanResponse.PlanMetricsBody toMetrics(PlanMetrics m) {
    return new ExecutionPlanResponse.PlanMetricsBody(
        m.totalTasks(),
        m.connectedComponents(),
        m.largestComponent(),
        m.parallelismFactor(),
        m.serializationRatio(),
        m.conflictsDetected(),
        m.chromaticNumber(),
        m.waveCount(),
        m.intraComponentParallelism(),
        m.estimatedDurationMillis());
  }

  private static Map<java.util.UUID, String> toTaskToComponent(Map<TaskId, String> src) {
    return src.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(e -> e.getKey().value(), Map.Entry::getValue));
  }
}
