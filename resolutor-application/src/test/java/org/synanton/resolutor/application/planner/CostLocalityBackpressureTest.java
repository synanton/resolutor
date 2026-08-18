package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.policy.FifoPolicy;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class CostLocalityBackpressureTest {

  @Test
  void criticalPathIsMaxGroupSumOfWaveMaxima() {
    FixedResourceGraphPort graph = new FixedResourceGraphPort();
    Task a = task("alpha", "1");
    Task b = task("beta", "2");
    ExecutionPlanner planner =
        new ExecutionPlanner(
            graph,
            new BackpressureManager(BackpressureConfig.disabled()),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v4"));

    ExecutionPlan plan = planner.compile(List.of(a, b));

    assertThat(plan.groups()).hasSize(2);
    assertThat(plan.metrics().estimatedDurationMillis()).isEqualTo(100L);
  }

  @Test
  void localityOrdersAWaveByResourceClass() {
    FixedResourceGraphPort graph = new FixedResourceGraphPort();
    Resource sharedA = Resource.of("edge", "A");
    Resource sharedB = Resource.of("edge", "B");
    Task t1 = task("zoo", "1");
    Task t2 = task("mid", "2");
    Task t3 = task("alpha", "3");
    graph.register(t1, Set.of(sharedA, t1.topResource()));
    graph.register(t2, Set.of(sharedA, sharedB, t2.topResource()));
    graph.register(t3, Set.of(sharedB, t3.topResource()));

    ExecutionPlanner planner =
        new ExecutionPlanner(
            graph,
            new BackpressureManager(BackpressureConfig.disabled()),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v4"));

    ExecutionPlan plan = planner.compile(List.of(t1, t2, t3));
    ColourWave wide =
        plan.groups().getFirst().waves().stream()
            .filter(w -> w.taskIds().size() == 2)
            .findFirst()
            .orElseThrow();
    assertThat(wide.taskIds()).containsExactly(t3.id(), t1.id());
  }

  @Test
  void backpressureReorderKeepsAdmittedTasksInAPartialComponent() {
    FixedResourceGraphPort graph = new FixedResourceGraphPort();
    Resource tag = Resource.of("tag", "8");
    Task project = task("project", "7");
    Task talk = task("talk", "41");
    graph.register(project, Set.of(project.topResource(), tag));
    graph.register(talk, Set.of(talk.topResource(), tag));

    BackpressureConfig tightProject =
        new BackpressureConfig(
            true,
            1_000L,
            Long.MAX_VALUE,
            Map.of("project", new BackpressureConfig.ClassConfig(0L, Long.MAX_VALUE)));
    ExecutionPlanner planner =
        new ExecutionPlanner(
            graph,
            new BackpressureManager(tightProject),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v4"));

    ExecutionPlan plan = planner.compile(List.of(project, talk));

    assertThat(plan.groups()).hasSize(1);
    assertThat(plan.groups().getFirst().orderedTasks()).containsExactly(talk.id());
  }

  @Test
  void backpressureWithoutReorderDropsTheWholeComponent() {
    FixedResourceGraphPort graph = new FixedResourceGraphPort();
    Resource tag = Resource.of("tag", "8");
    Task project = task("project", "7");
    Task talk = task("talk", "41");
    graph.register(project, Set.of(project.topResource(), tag));
    graph.register(talk, Set.of(talk.topResource(), tag));

    BackpressureConfig tightProject =
        new BackpressureConfig(
            true,
            1_000L,
            Long.MAX_VALUE,
            Map.of("project", new BackpressureConfig.ClassConfig(0L, Long.MAX_VALUE)));
    PlannerConfig noReorder =
        new PlannerConfig(
            "v4",
            100,
            FifoPolicy.INSTANCE,
            10_000,
            true,
            true,
            true,
            false,
            Duration.ofMillis(100),
            Map.of());
    ExecutionPlanner planner =
        new ExecutionPlanner(
            graph, new BackpressureManager(tightProject), new NoOpMetricsPort(), noReorder);

    ExecutionPlan plan = planner.compile(List.of(project, talk));

    assertThat(plan.groups()).isEmpty();
  }

  private static Task task(String resourceClass, String resourceId) {
    return new Task(
        TaskId.generate(),
        Resource.of(resourceClass, resourceId),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.parse("2026-01-01T00:00:00Z"),
        null,
        0L);
  }
}
