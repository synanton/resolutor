package org.synanton.resolutor.application.fake;

import java.time.Duration;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.domain.plan.PlanMetrics;

/** No-op {@link MetricsPort} for tests that do not assert on metrics. */
public final class NoOpMetricsPort implements MetricsPort {

  @Override
  public void incrementTasksIngested(int count) {}

  @Override
  public void recordPlanBuilt(PlanMetrics metrics, Duration planningDuration) {}

  @Override
  public void recordBackpressureDenied(String resourceClass) {}

  @Override
  public void recordGroupDispatched(String componentId, Duration duration, boolean succeeded) {}

  @Override
  public void recordResourceGraphCall(String outcome) {}
}
