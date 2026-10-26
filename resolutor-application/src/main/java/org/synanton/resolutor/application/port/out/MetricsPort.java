package org.synanton.resolutor.application.port.out;

import java.time.Duration;
import org.synanton.resolutor.domain.plan.PlanMetrics;

/** Output port for application metrics; adapters may export these to a monitoring backend. */
public interface MetricsPort {
  void incrementTasksIngested(int count);

  void recordPlanBuilt(PlanMetrics metrics, Duration planningDuration);

  void recordBackpressureDenied(String resourceClass);

  void recordGroupDispatched(String componentId, Duration duration, boolean succeeded);

  void recordResourceGraphCall(String outcome);

  default void recordTaskDuration(String resourceClass, Duration duration) {}
}
