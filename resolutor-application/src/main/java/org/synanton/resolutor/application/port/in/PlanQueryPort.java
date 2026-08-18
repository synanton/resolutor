package org.synanton.resolutor.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;

/** Inbound port: query published plans and compile a dry-run plan. */
public interface PlanQueryPort {

  /** Return the most recently published plan, if any. */
  Optional<ExecutionPlan> latestPlan();

  /** Return the plan with {@code id}, if it was published. */
  Optional<ExecutionPlan> planById(PlanId id);

  /** Return the latest plan generated at or before {@code at}. */
  Optional<ExecutionPlan> planFor(Instant at);

  /**
   * Compile an {@link ExecutionPlan} for {@code commands} without persisting tasks or publishing.
   */
  ExecutionPlan simulate(List<NewTaskCommand> commands);
}
