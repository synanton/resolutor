package org.synanton.resolutor.application.lifecycle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.LeadershipPort;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.PlanPublisherPort;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.task.Task;

/**
 * Orchestrates one planning+dispatch cycle under distributed leadership.
 *
 * <p>On the first successful leadership acquisition, seeds {@link BackpressureManager} from the
 * database so that in-flight counts survive a leader failover (see docs/design.md Appendix A).
 * Dispatch runs before the leadership lock is released (design §14).
 */
public final class LeadershipManager {

  private static final System.Logger LOG = System.getLogger(LeadershipManager.class.getName());

  private final LeadershipPort leadershipPort;
  private final BackpressureManager backpressure;
  private final ProgressRepositoryPort progressRepo;
  private final TaskRepositoryPort taskRepo;
  private final ExecutionPlanner planner;
  private final PlanPublisherPort planPublisher;
  private final PlannerConfig plannerConfig;
  private final MetricsPort metrics;
  private final DispatcherPort dispatcher;
  private final TaskTimeoutReaper timeoutReaper;
  private final Clock clock;

  private final AtomicBoolean reconstructed = new AtomicBoolean(false);

  public LeadershipManager(
      LeadershipPort leadershipPort,
      BackpressureManager backpressure,
      ProgressRepositoryPort progressRepo,
      TaskRepositoryPort taskRepo,
      ExecutionPlanner planner,
      PlanPublisherPort planPublisher,
      PlannerConfig plannerConfig,
      MetricsPort metrics,
      DispatcherPort dispatcher,
      TaskTimeoutReaper timeoutReaper,
      Clock clock) {
    this.leadershipPort = Objects.requireNonNull(leadershipPort, "leadershipPort");
    this.backpressure = Objects.requireNonNull(backpressure, "backpressure");
    this.progressRepo = Objects.requireNonNull(progressRepo, "progressRepo");
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
    this.planner = Objects.requireNonNull(planner, "planner");
    this.planPublisher = Objects.requireNonNull(planPublisher, "planPublisher");
    this.plannerConfig = Objects.requireNonNull(plannerConfig, "plannerConfig");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.timeoutReaper = Objects.requireNonNull(timeoutReaper, "timeoutReaper");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Acquire leadership and, if successful, reap timeouts, compile a plan, and dispatch it before
   * releasing the lock. Returns the produced plan, or empty if leadership was not acquired.
   */
  public Optional<ExecutionPlan> runPlanningCycle(Duration lockAtMost) {
    return leadershipPort.runIfLeader(
        lockAtMost,
        () -> {
          reconstructIfNeeded();
          timeoutReaper.run(Instant.now(clock));
          List<Task> batch = taskRepo.loadBatchForPlanning(plannerConfig.batchSize());
          ExecutionPlan plan = metrics.observe("plan.compile", () -> planner.compile(batch));
          planPublisher.publish(plan);
          metrics.recordPlanBuilt(plan.metrics(), plan.planningDuration());
          LOG.log(
              System.Logger.Level.INFO,
              "Planning cycle complete: {0} groups, {1} tasks, id={2}",
              plan.groups().size(),
              plan.metrics().totalTasks(),
              plan.id());
          dispatchIfNeeded(plan);
          return plan;
        });
  }

  private void dispatchIfNeeded(ExecutionPlan plan) {
    if (plan.groups().isEmpty()) {
      return;
    }
    try {
      Instant dispatchStart = Instant.now();
      metrics.observe(
          "plan.dispatch",
          () -> {
            dispatcher.runPlan(plan);
            return Boolean.TRUE;
          });
      metrics.recordPlanExecuted(
          plan.metrics().estimatedDurationMillis(), Duration.between(dispatchStart, Instant.now()));
    } catch (RuntimeException ex) {
      LOG.log(System.Logger.Level.ERROR, "Dispatch cycle raised", ex);
    }
  }

  private void reconstructIfNeeded() {
    if (reconstructed.compareAndSet(false, true)) {
      backpressure.reconstructFromDb(progressRepo.inflightCountsByResourceClass());
      LOG.log(System.Logger.Level.INFO, "Backpressure state reconstructed from database");
    }
  }
}
