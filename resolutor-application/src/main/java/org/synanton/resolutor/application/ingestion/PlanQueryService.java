package org.synanton.resolutor.application.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.PlanQueryPort;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Implements {@link PlanQueryPort}: history reads via {@link PlanPublisherPort}, dry-run compile
 * via {@link ExecutionPlanner} (no persist, no publish).
 */
public final class PlanQueryService implements PlanQueryPort {

  private final PlanPublisherPort planPublisher;
  private final ExecutionPlanner planner;
  private final Clock clock;

  public PlanQueryService(PlanPublisherPort planPublisher, ExecutionPlanner planner, Clock clock) {
    this.planPublisher = Objects.requireNonNull(planPublisher, "planPublisher");
    this.planner = Objects.requireNonNull(planner, "planner");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public Optional<ExecutionPlan> latestPlan() {
    return planPublisher.latestPlan();
  }

  @Override
  public Optional<ExecutionPlan> planById(PlanId id) {
    Objects.requireNonNull(id, "id");
    return planPublisher.findById(id);
  }

  @Override
  public Optional<ExecutionPlan> planFor(Instant at) {
    Objects.requireNonNull(at, "at");
    return planPublisher.planAtOrBefore(at);
  }

  @Override
  public ExecutionPlan simulate(List<NewTaskCommand> commands) {
    Objects.requireNonNull(commands, "commands");
    Instant now = Instant.now(clock);
    List<Task> tasks = new ArrayList<>(commands.size());
    for (NewTaskCommand cmd : commands) {
      tasks.add(
          new Task(
              TaskId.generate(),
              cmd.topResource(),
              Set.of(),
              cmd.searchDsl(),
              cmd.payload(),
              null,
              TaskState.PENDING,
              now,
              cmd.timeoutAt(),
              0L));
    }
    return planner.compile(tasks);
  }
}
