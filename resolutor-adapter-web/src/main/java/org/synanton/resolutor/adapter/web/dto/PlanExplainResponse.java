package org.synanton.resolutor.adapter.web.dto;

import java.util.Map;
import java.util.UUID;
import org.synanton.resolutor.application.port.in.PlanExplainView;
import org.synanton.resolutor.domain.plan.PlanMetrics;

/** Response for {@code GET /api/v1/plans/{id}/explain}. */
public record PlanExplainResponse(
    UUID id,
    PlanMetricsBody metrics,
    double conflictDensity,
    double averageDegree,
    Map<UUID, String> taskToComponent) {

  public record PlanMetricsBody(
      int totalTasks,
      int connectedComponents,
      int largestComponent,
      double parallelismFactor,
      double serializationRatio,
      int conflictsDetected,
      int chromaticNumber,
      int waveCount,
      double intraComponentParallelism,
      long estimatedDurationMillis) {}

  public static PlanExplainResponse from(PlanExplainView view) {
    PlanMetrics m = view.metrics();
    Map<UUID, String> mapping = new java.util.HashMap<>();
    view.taskToComponent().forEach((k, v) -> mapping.put(k.value(), v));
    return new PlanExplainResponse(
        view.idValue(),
        new PlanMetricsBody(
            m.totalTasks(),
            m.connectedComponents(),
            m.largestComponent(),
            m.parallelismFactor(),
            m.serializationRatio(),
            m.conflictsDetected(),
            m.chromaticNumber(),
            m.waveCount(),
            m.intraComponentParallelism(),
            m.estimatedDurationMillis()),
        view.conflictDensity(),
        view.averageDegree(),
        Map.copyOf(mapping));
  }
}
