package org.synanton.resolutor.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.AlwaysLeaderPort;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.InMemoryPlanPublisher;
import org.synanton.resolutor.application.fake.InMemoryProgressRepository;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.application.fake.NoOpDispatcherPort;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.LeadershipPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class LeadershipManagerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);

  private InMemoryTaskRepository taskRepo;
  private InMemoryProgressRepository progressRepo;
  private InMemoryPlanPublisher planPublisher;
  private FixedResourceGraphPort resourceGraph;
  private BackpressureManager backpressure;
  private RecordingDispatcher dispatcher;
  private LeadershipManager manager;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    progressRepo = new InMemoryProgressRepository();
    planPublisher = new InMemoryPlanPublisher();
    resourceGraph = new FixedResourceGraphPort();
    backpressure = new BackpressureManager(BackpressureConfig.disabled());
    dispatcher = new RecordingDispatcher();
    manager = manager(new AlwaysLeaderPort(), dispatcher);
  }

  @Test
  void planningCyclePublishesPlan() {
    taskRepo.save(pending());

    Optional<ExecutionPlan> result = manager.runPlanningCycle(Duration.ofSeconds(30));

    assertThat(result).isPresent();
    assertThat(planPublisher.allPublished()).hasSize(1);
  }

  @Test
  void planningCycleDispatchesBeforeReturning() {
    taskRepo.save(pending());

    Optional<ExecutionPlan> result = manager.runPlanningCycle(Duration.ofSeconds(30));

    assertThat(result).isPresent();
    assertThat(dispatcher.plans).hasSize(1);
    assertThat(dispatcher.plans.getFirst().groups()).isNotEmpty();
  }

  @Test
  void planningCycleWithEmptyRepoPublishesEmptyPlanAndDoesNotDispatch() {
    Optional<ExecutionPlan> result = manager.runPlanningCycle(Duration.ofSeconds(30));

    assertThat(result).isPresent();
    assertThat(result.get().groups()).isEmpty();
    assertThat(dispatcher.plans).isEmpty();
  }

  @Test
  void whenLeadershipNotAcquiredNoPlanIsPublished() {
    LeadershipPort neverLeader =
        new LeadershipPort() {
          @Override
          public <T> Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work) {
            return Optional.empty();
          }
        };
    LeadershipManager mgr = manager(neverLeader, new NoOpDispatcherPort());

    taskRepo.save(pending());
    Optional<ExecutionPlan> result = mgr.runPlanningCycle(Duration.ofSeconds(30));

    assertThat(result).isEmpty();
    assertThat(planPublisher.allPublished()).isEmpty();
  }

  @Test
  void backpressureIsReconstructedOnlyOnce() {
    AtomicBoolean reconstructed = new AtomicBoolean(false);
    InMemoryProgressRepository spy =
        new InMemoryProgressRepository() {
          @Override
          public java.util.Map<String, Long> inflightCountsByResourceClass() {
            reconstructed.set(true);
            return super.inflightCountsByResourceClass();
          }
        };
    progressRepo = spy;
    LeadershipManager mgr = manager(new AlwaysLeaderPort(), new NoOpDispatcherPort());

    mgr.runPlanningCycle(Duration.ofSeconds(30));
    assertThat(reconstructed.get()).isTrue();

    reconstructed.set(false);
    mgr.runPlanningCycle(Duration.ofSeconds(30));
    assertThat(reconstructed.get()).isFalse();
  }

  @Test
  void recoversOrphanedStartedTasksThenPlansThem() {
    Task orphan = task(TaskState.STARTED, null);
    taskRepo.save(orphan);

    Optional<ExecutionPlan> result = manager.runPlanningCycle(Duration.ofSeconds(30));

    assertThat(taskRepo.findById(orphan.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.PENDING);
    assertThat(result).isPresent();
    assertThat(result.get().metrics().totalTasks()).isEqualTo(1);
  }

  private LeadershipManager manager(LeadershipPort leadership, DispatcherPort dispatcherPort) {
    ExecutionPlanner planner =
        new ExecutionPlanner(
            resourceGraph, backpressure, new NoOpMetricsPort(), PlannerConfig.defaults("v-test"));
    TaskTimeoutReaper reaper = new TaskTimeoutReaper(taskRepo, backpressure);
    return new LeadershipManager(
        leadership,
        backpressure,
        progressRepo,
        taskRepo,
        planner,
        planPublisher,
        PlannerConfig.defaults("v-test"),
        new NoOpMetricsPort(),
        dispatcherPort,
        reaper,
        CLOCK);
  }

  private static Task pending() {
    return task(TaskState.PENDING, null);
  }

  private static Task task(TaskState state, Instant timeoutAt) {
    return new Task(
        TaskId.generate(),
        Resource.of("project", "1"),
        Set.of(),
        "{}",
        "{}",
        null,
        state,
        Instant.parse("2026-08-14T00:00:00Z"),
        timeoutAt,
        0L);
  }

  private static final class RecordingDispatcher implements DispatcherPort {
    private final List<ExecutionPlan> plans = new ArrayList<>();

    @Override
    public List<GroupResult> runPlan(ExecutionPlan plan) {
      plans.add(plan);
      return List.of();
    }

    @Override
    public GroupResult dispatch(SequentialGroup group) {
      return new GroupResult(group.componentId(), List.of());
    }
  }
}
