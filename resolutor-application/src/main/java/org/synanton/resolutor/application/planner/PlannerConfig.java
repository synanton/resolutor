package org.synanton.resolutor.application.planner;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.synanton.resolutor.domain.policy.FifoPolicy;
import org.synanton.resolutor.domain.policy.OrderingPolicy;

/** Immutable configuration snapshot for {@link ExecutionPlanner}. */
public record PlannerConfig(
    String plannerVersion,
    int batchSize,
    OrderingPolicy orderingPolicy,
    int maxBucketSize,
    boolean colouringEnabled,
    boolean localityEnabled,
    boolean costEnabled,
    boolean backpressureReorderEnabled,
    Duration defaultTaskDuration,
    Map<String, Duration> taskDurations) {

  public PlannerConfig {
    Objects.requireNonNull(plannerVersion, "plannerVersion");
    Objects.requireNonNull(orderingPolicy, "orderingPolicy");
    Objects.requireNonNull(defaultTaskDuration, "defaultTaskDuration");
    Objects.requireNonNull(taskDurations, "taskDurations");
    if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
    if (maxBucketSize < 1) throw new IllegalArgumentException("maxBucketSize must be >= 1");
    if (defaultTaskDuration.isNegative()) {
      throw new IllegalArgumentException("defaultTaskDuration must be >= 0");
    }
    taskDurations = Map.copyOf(taskDurations);
  }

  /** FIFO, colouring + v4 passes on, default task duration 100 ms. */
  public static PlannerConfig defaults(String plannerVersion) {
    return new PlannerConfig(
        plannerVersion,
        100,
        FifoPolicy.INSTANCE,
        10_000,
        true,
        true,
        true,
        true,
        Duration.ofMillis(100),
        Map.of());
  }

  /** Colouring and v4 reordering off (v1 serial groups). */
  public static PlannerConfig serial(String plannerVersion) {
    return new PlannerConfig(
        plannerVersion,
        100,
        FifoPolicy.INSTANCE,
        10_000,
        false,
        false,
        false,
        false,
        Duration.ofMillis(100),
        Map.of());
  }
}
