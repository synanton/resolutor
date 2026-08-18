package org.synanton.resolutor.adapter.web.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Response body for {@code GET /api/v1/plans/latest}. */
public record ExecutionPlanResponse(
    UUID id,
    Instant generatedAt,
    String plannerVersion,
    Duration planningDuration,
    String orderPolicy,
    List<SequentialGroupBody> groups,
    PlanMetricsBody metrics,
    Map<UUID, String> taskToComponent) {

  public record SequentialGroupBody(
      String componentId, List<UUID> orderedTasks, List<ColourWaveBody> waves) {}

  public record ColourWaveBody(int colour, List<UUID> taskIds) {}

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
}
