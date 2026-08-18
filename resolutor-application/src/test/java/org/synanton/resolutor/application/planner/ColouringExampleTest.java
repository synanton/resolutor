package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/** Path of three: T1–{A}, T2–{A,B}, T3–{B}. Ends may share a colour; the middle task cannot. */
class ColouringExampleTest {

  @Test
  void pathOfThreeProducesAWaveOfTwo() {
    FixedResourceGraphPort resourceGraph = new FixedResourceGraphPort();
    Resource a = Resource.of("res", "A");
    Resource b = Resource.of("res", "B");
    Task t1 = task("t1");
    Task t2 = task("t2");
    Task t3 = task("t3");
    resourceGraph.register(t1, Set.of(a));
    resourceGraph.register(t2, Set.of(a, b));
    resourceGraph.register(t3, Set.of(b));

    ExecutionPlanner planner =
        new ExecutionPlanner(
            resourceGraph,
            new BackpressureManager(BackpressureConfig.disabled()),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v3"));

    ExecutionPlan plan = planner.compile(List.of(t1, t2, t3));

    assertThat(plan.groups()).hasSize(1);
    List<ColourWave> waves = plan.groups().getFirst().waves();
    int maxWave = waves.stream().mapToInt(w -> w.taskIds().size()).max().orElse(0);
    assertThat(maxWave).isEqualTo(2);
    assertThat(plan.metrics().chromaticNumber()).isEqualTo(2);
    assertThat(sameWave(waves, t1.id(), t2.id())).isFalse();
    assertThat(sameWave(waves, t2.id(), t3.id())).isFalse();
    assertThat(sameWave(waves, t1.id(), t3.id())).isTrue();
  }

  private static boolean sameWave(List<ColourWave> waves, TaskId x, TaskId y) {
    return waves.stream().anyMatch(w -> w.taskIds().contains(x) && w.taskIds().contains(y));
  }

  private static Task task(String marker) {
    return new Task(
        TaskId.generate(),
        Resource.of("top", marker),
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
