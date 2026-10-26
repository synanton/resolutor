package org.synanton.resolutor.application.port.out;

import java.time.Duration;
import java.util.function.Supplier;
import org.synanton.resolutor.domain.plan.PlanMetrics;

/** Outbound port: counters, gauges and timers for observability adapters. */
public interface MetricsPort {

  /** Record that {@code count} tasks were ingested. */
  void incrementTasksIngested(int count);

  /** Record a completed planning cycle and its duration. */
  void recordPlanBuilt(PlanMetrics metrics, Duration planningDuration);

  /** Record a backpressure denial for {@code resourceClass}. */
  void recordBackpressureDenied(String resourceClass);

  /** Record the duration and outcome of dispatching one component group. */
  void recordGroupDispatched(String componentId, Duration duration, boolean succeeded);

  /** Record a resource-graph call with the given {@code outcome} (e.g. success/failure/timeout). */
  void recordResourceGraphCall(String outcome);

  /** Record how long a single task took to execute. */
  default void recordTaskDuration(String resourceClass, Duration duration) {}

  /** Record the actual duration of a plan dispatch against its estimate. */
  default void recordPlanExecuted(long estimatedDurationMillis, Duration actual) {}

  /** Observe {@code work} under the named meter/span. */
  default <T> T observe(String name, Supplier<T> work) {
    return work.get();
  }
}
