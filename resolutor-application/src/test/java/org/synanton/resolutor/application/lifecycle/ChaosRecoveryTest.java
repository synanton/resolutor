package org.synanton.resolutor.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.dispatch.DispatchConfig;
import org.synanton.resolutor.application.dispatch.InProcessDispatcher;
import org.synanton.resolutor.application.fake.AlwaysLeaderPort;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.InMemoryPlanPublisher;
import org.synanton.resolutor.application.fake.InMemoryProgressRepository;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.fake.NoOpTaskWorker;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Chaos-style recovery: a task left STARTED (planner killed mid-dispatch) is recovered and
 * completed exactly once on the next leadership cycle.
 */
class ChaosRecoveryTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);

  private InMemoryTaskRepository taskRepo;
  private NoOpTaskWorker worker;
  private InProcessDispatcher dispatcher;
  private LeadershipManager manager;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    worker = new NoOpTaskWorker();
    BackpressureManager backpressure = new BackpressureManager(BackpressureConfig.disabled());
    dispatcher =
        new InProcessDispatcher(
            taskRepo, worker, backpressure, new NoOpMetricsPort(), DispatchConfig.defaults());
    ExecutionPlanner planner =
        new ExecutionPlanner(
            new FixedResourceGraphPort(),
            backpressure,
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v-test"));
    manager =
        new LeadershipManager(
            new AlwaysLeaderPort(),
            backpressure,
            new InMemoryProgressRepository(),
            taskRepo,
            planner,
            new InMemoryPlanPublisher(),
            PlannerConfig.defaults("v-test"),
            new NoOpMetricsPort(),
            dispatcher,
            new TaskTimeoutReaper(taskRepo, backpressure),
            CLOCK);
  }

  @AfterEach
  void tearDown() {
    dispatcher.close();
  }

  @Test
  void orphanedStartedTaskCompletesOnceAfterNextCycle() {
    Task crashed =
        new Task(
            TaskId.generate(),
            Resource.of("project", "7"),
            Set.of(),
            "{}",
            "{}",
            null,
            TaskState.STARTED,
            Instant.parse("2026-08-14T00:00:00Z"),
            null,
            0L);
    taskRepo.save(crashed);

    manager.runPlanningCycle(Duration.ofSeconds(30));
    assertThat(taskRepo.findById(crashed.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);

    manager.runPlanningCycle(Duration.ofSeconds(30));
    assertThat(taskRepo.findById(crashed.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.COMPLETED);
    assertThat(taskRepo.countByState().get(TaskState.COMPLETED)).isEqualTo(1L);
    assertThat(worker.executed()).hasSize(1);
  }
}
