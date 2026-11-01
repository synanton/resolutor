package org.synanton.resolutor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class ApplicationBeansConfigTest {

  @Test
  void fallbackWorkerFailsFastUnlessOptedIn() {
    assertThatThrownBy(() -> ApplicationBeansConfig.fallbackTaskWorker(false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noop-enabled");
  }

  @Test
  void fallbackWorkerCompletesTasksWhenOptedIn() {
    TaskWorker worker = ApplicationBeansConfig.fallbackTaskWorker(true);

    TaskWorker.Result result = worker.execute(pending());

    assertThat(result.outcome()).isEqualTo(TaskWorker.Outcome.COMPLETED);
  }

  private static Task pending() {
    return new Task(
        TaskId.generate(),
        Resource.of("project", "1"),
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
