package org.synanton.resolutor.domain.plan;

/**
 * Observable metrics attached to an {@link ExecutionPlan}. See docs/design.md §9.
 *
 * <p>{@code parallelismFactor = connectedComponents / max(1, largestComponent)}. Higher is better.
 * {@code serializationRatio = largestComponent / totalTasks}. Lower is better.
 *
 * <p>v3 colouring: {@code chromaticNumber} is the plan-wide max colour + 1; {@code waveCount} sums
 * waves across groups; {@code intraComponentParallelism = maxWaveSize / max(1, largestComponent)}.
 *
 * <p>v4 cost model: {@code estimatedDurationMillis} is the plan critical path (max over groups of
 * the sum of per-wave max task estimates). Actual dispatch duration is recorded separately on
 * {@code MetricsPort}.
 */
public record PlanMetrics(
    int totalTasks,
    int connectedComponents,
    int largestComponent,
    double parallelismFactor,
    double serializationRatio,
    int conflictsDetected,
    int chromaticNumber,
    int waveCount,
    double intraComponentParallelism,
    long estimatedDurationMillis) {

  /** v3 constructor: estimated duration left at zero. */
  public PlanMetrics(
      int totalTasks,
      int connectedComponents,
      int largestComponent,
      double parallelismFactor,
      double serializationRatio,
      int conflictsDetected,
      int chromaticNumber,
      int waveCount,
      double intraComponentParallelism) {
    this(
        totalTasks,
        connectedComponents,
        largestComponent,
        parallelismFactor,
        serializationRatio,
        conflictsDetected,
        chromaticNumber,
        waveCount,
        intraComponentParallelism,
        0L);
  }

  /** v1/v2 constructor: colouring metrics and estimated duration left at zero. */
  public PlanMetrics(
      int totalTasks,
      int connectedComponents,
      int largestComponent,
      double parallelismFactor,
      double serializationRatio,
      int conflictsDetected) {
    this(
        totalTasks,
        connectedComponents,
        largestComponent,
        parallelismFactor,
        serializationRatio,
        conflictsDetected,
        0,
        0,
        0.0,
        0L);
  }

  public PlanMetrics {
    if (estimatedDurationMillis < 0) {
      throw new IllegalArgumentException("estimatedDurationMillis must be >= 0");
    }
  }

  public static PlanMetrics zero() {
    return new PlanMetrics(0, 0, 0, 0.0, 0.0, 0, 0, 0, 0.0, 0L);
  }
}
