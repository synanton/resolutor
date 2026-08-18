package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class TaskDurationEstimatorTest {

  @Test
  void configuredClassOverridesDefault() {
    TaskDurationEstimator estimator =
        new TaskDurationEstimator(Duration.ofMillis(100), Map.of("project", Duration.ofSeconds(2)));
    assertThat(estimator.estimate(task("project")).toMillis()).isEqualTo(2000L);
    assertThat(estimator.estimate(task("talk")).toMillis()).isEqualTo(100L);
  }

  @Test
  void observedMeanBeatsDefault() {
    TaskDurationEstimator estimator = new TaskDurationEstimator(Duration.ofMillis(100), Map.of());
    estimator.record("talk", Duration.ofMillis(40));
    estimator.record("talk", Duration.ofMillis(60));
    assertThat(estimator.estimate(task("talk")).toMillis()).isEqualTo(50L);
  }

  private static Task task(String resourceClass) {
    return new Task(
        TaskId.generate(),
        Resource.of(resourceClass, "1"),
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
