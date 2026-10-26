package org.synanton.resolutor.application.port.out;

import java.time.Instant;
import java.util.Optional;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;

/** Outbound port: stores execution-plan history and exposes the latest published plan. */
public interface PlanPublisherPort {

  /** Publish {@code plan} as the newest plan in history. */
  void publish(ExecutionPlan plan);

  /** Return the most recently published plan, if any. */
  Optional<ExecutionPlan> latestPlan();

  /** Return the plan with {@code id}, if it exists in history. */
  Optional<ExecutionPlan> findById(PlanId id);

  /** Return the newest plan generated at or before {@code at}, if any. */
  Optional<ExecutionPlan> planAtOrBefore(Instant at);
}
