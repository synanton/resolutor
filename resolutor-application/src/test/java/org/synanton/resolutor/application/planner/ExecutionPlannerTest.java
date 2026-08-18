package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class ExecutionPlannerTest {

  private FixedResourceGraphPort resourceGraph;
  private BackpressureManager backpressure;
  private ExecutionPlanner planner;

  @BeforeEach
  void setUp() {
    resourceGraph = new FixedResourceGraphPort();
    backpressure = new BackpressureManager(BackpressureConfig.disabled());
    planner =
        new ExecutionPlanner(
            resourceGraph, backpressure, new NoOpMetricsPort(), PlannerConfig.defaults("test-1"));
  }

  @Test
  void emptyBatchProducesEmptyPlan() {
    ExecutionPlan plan = planner.compile(List.of());

    assertThat(plan.groups()).isEmpty();
    assertThat(plan.metrics().totalTasks()).isZero();
  }

  @Test
  void singleTaskProducesOneGroup() {
    Task t = task("project", "1");
    ExecutionPlan plan = planner.compile(List.of(t));

    assertThat(plan.groups()).hasSize(1);
    assertThat(plan.groups().get(0).orderedTasks()).containsExactly(t.id());
  }

  @Test
  void independentTasksRunInParallel() {
    Task a = task("project", "1");
    Task b = task("project", "2");
    ExecutionPlan plan = planner.compile(List.of(a, b));

    // Different resources → no conflict → two separate groups.
    assertThat(plan.groups()).hasSize(2);
    assertThat(plan.metrics().connectedComponents()).isEqualTo(2);
  }

  @Test
  void conflictingTasksAreGroupedTogether() {
    Resource shared = Resource.of("project", "99");
    Task a = task("project", "1");
    Task b = task("project", "2");
    resourceGraph.register(a, Set.of(shared));
    resourceGraph.register(b, Set.of(shared));

    ExecutionPlan plan = planner.compile(List.of(a, b));

    assertThat(plan.groups()).hasSize(1);
    assertThat(plan.groups().get(0).orderedTasks()).containsExactlyInAnyOrder(a.id(), b.id());
  }

  @Test
  void backpressureDeniesEntireComponent() {
    BackpressureConfig tight = new BackpressureConfig(true, 0L, Long.MAX_VALUE, Map.of());
    planner =
        new ExecutionPlanner(
            resourceGraph,
            new BackpressureManager(tight),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("test-1"));

    Task t = task("project", "1");
    ExecutionPlan plan = planner.compile(List.of(t));

    assertThat(plan.groups()).isEmpty();
  }

  @Test
  void resolutionFailureSkipsTask() {
    ResourceGraphPort failing =
        task -> {
          throw new IllegalStateException("resource graph unavailable");
        };
    planner =
        new ExecutionPlanner(
            failing, backpressure, new NoOpMetricsPort(), PlannerConfig.defaults("test-1"));

    ExecutionPlan plan = planner.compile(List.of(task("project", "1")));

    assertThat(plan.groups()).isEmpty();
    assertThat(plan.metrics().totalTasks()).isZero();
  }

  @Test
  void plannerVersionCarriedThrough() {
    ExecutionPlan plan = planner.compile(List.of());
    assertThat(plan.plannerVersion()).isEqualTo("test-1");
  }

  @Test
  void colouringDisabledUsesSingletonWaves() {
    planner =
        new ExecutionPlanner(
            resourceGraph, backpressure, new NoOpMetricsPort(), PlannerConfig.serial("test-1"));
    Resource a = Resource.of("res", "A");
    Resource b = Resource.of("res", "B");
    Task t1 = task("project", "1");
    Task t2 = task("project", "2");
    Task t3 = task("project", "3");
    resourceGraph.register(t1, Set.of(a));
    resourceGraph.register(t2, Set.of(a, b));
    resourceGraph.register(t3, Set.of(b));

    ExecutionPlan plan = planner.compile(List.of(t1, t2, t3));

    assertThat(plan.groups()).hasSize(1);
    assertThat(plan.groups().getFirst().waves()).allMatch(w -> w.taskIds().size() == 1);
  }

  @Test
  void taskToComponentMappingIsPopulated() {
    Task a = task("room", "1");
    Task b = task("room", "2");
    Resource shared = Resource.of("floor", "G");
    resourceGraph.register(a, Set.of(shared));
    resourceGraph.register(b, Set.of(shared));

    ExecutionPlan plan = planner.compile(List.of(a, b));

    assertThat(plan.taskToComponent()).containsKey(a.id());
    assertThat(plan.taskToComponent()).containsKey(b.id());
    assertThat(plan.taskToComponent().get(a.id())).isEqualTo(plan.taskToComponent().get(b.id()));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Task task(String resourceClass, String resourceId) {
    return new Task(
        TaskId.generate(),
        Resource.of(resourceClass, resourceId),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.now(),
        null,
        0L);
  }
}
