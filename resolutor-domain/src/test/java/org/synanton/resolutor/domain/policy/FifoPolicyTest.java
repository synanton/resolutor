package org.synanton.resolutor.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class FifoPolicyTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void ordersOldestFirst() {
    var old = task("old", T0);
    var mid = task("mid", T0.plusSeconds(10));
    var newer = task("new", T0.plusSeconds(20));

    var ordered = FifoPolicy.INSTANCE.order(List.of(newer, old, mid));

    assertThat(ordered).containsExactly(old.id(), mid.id(), newer.id());
  }

  @Test
  void singleTask() {
    var t = task("only", T0);
    assertThat(FifoPolicy.INSTANCE.order(List.of(t))).containsExactly(t.id());
  }

  @Test
  void emptyListReturnsEmpty() {
    assertThat(FifoPolicy.INSTANCE.order(List.of())).isEmpty();
  }

  private static Task task(String resourceId, Instant createdAt) {
    return new Task(
        TaskId.generate(),
        Resource.of("project", resourceId),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        createdAt,
        null,
        0);
  }
}
