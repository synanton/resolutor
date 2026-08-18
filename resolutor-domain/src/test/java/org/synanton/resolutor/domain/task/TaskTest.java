package org.synanton.resolutor.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.resource.Resource;

class TaskTest {

  @Test
  void resolvedResourcesIsImmutableCopy() {
    var original = new java.util.HashSet<Resource>();
    original.add(Resource.of("project", "7"));
    var task = task(original);

    original.add(Resource.of("talk", "41"));

    assertThat(task.resolvedResources()).hasSize(1);
  }

  @Test
  void withResolvedResourcesCreatesNewInstance() {
    var task = task(Set.of());
    var newResources = Set.of(Resource.of("project", "7"));

    var updated = task.withResolvedResources(newResources);

    assertThat(updated.resolvedResources()).isEqualTo(newResources);
    assertThat(task.resolvedResources()).isEmpty();
  }

  @Test
  void withStateCreatesNewInstance() {
    var task = task(Set.of());

    var started = task.withState(TaskState.STARTED);

    assertThat(started.state()).isEqualTo(TaskState.STARTED);
    assertThat(task.state()).isEqualTo(TaskState.PENDING);
  }

  @Test
  void nullIdIsRejected() {
    assertThatThrownBy(
            () ->
                new Task(
                    null,
                    Resource.of("project", "7"),
                    Set.of(),
                    "{}",
                    "{}",
                    null,
                    TaskState.PENDING,
                    Instant.now(),
                    null,
                    0))
        .isInstanceOf(NullPointerException.class);
  }

  private static Task task(Set<Resource> resources) {
    return new Task(
        TaskId.generate(),
        Resource.of("project", "7"),
        resources,
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.now(),
        null,
        0);
  }
}
