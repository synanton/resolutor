package org.synanton.resolutor.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class TaskTimeoutReaperTest {

  private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

  private InMemoryTaskRepository taskRepo;
  private BackpressureManager backpressure;
  private TaskTimeoutReaper reaper;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    backpressure =
        new BackpressureManager(new BackpressureConfig(true, 1L, 500_000L, java.util.Map.of()));
    reaper = new TaskTimeoutReaper(taskRepo, backpressure);
  }

  @Test
  void marksExpiredPendingTaskTimeout() {
    Task expired = task(TaskState.PENDING, NOW.minusSeconds(1), null);
    taskRepo.save(expired);

    TaskTimeoutReaper.Result result = reaper.run(NOW);

    assertThat(result.timedOut()).isEqualTo(1);
    assertThat(taskRepo.findById(expired.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.TIMEOUT);
  }

  @Test
  void leavesUnexpiredPendingTaskUnchanged() {
    Task live = task(TaskState.PENDING, NOW.plusSeconds(60), null);
    taskRepo.save(live);

    TaskTimeoutReaper.Result result = reaper.run(NOW);

    assertThat(result.timedOut()).isZero();
    assertThat(taskRepo.findById(live.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.PENDING);
  }

  @Test
  void recoversOrphanedStartedWithoutCursorToPending() {
    Task orphan = task(TaskState.STARTED, null, null);
    taskRepo.save(orphan);
    backpressure.onEmitted("project");
    assertThat(backpressure.admit("project")).isFalse();

    TaskTimeoutReaper.Result result = reaper.run(NOW);

    assertThat(result.recovered()).isEqualTo(1);
    assertThat(taskRepo.findById(orphan.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.PENDING);
    assertThat(backpressure.admit("project")).isTrue();
  }

  @Test
  void recoversOrphanedStartedWithCursorToPaused() {
    Task orphan = task(TaskState.STARTED, null, new Cursor("page:2"));
    taskRepo.save(orphan);

    reaper.run(NOW);

    assertThat(taskRepo.findById(orphan.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.PAUSED);
  }

  @Test
  void timedOutStartedReleasesInflightSlot() {
    Task expired = task(TaskState.STARTED, NOW.minusSeconds(1), null);
    taskRepo.save(expired);
    backpressure.onEmitted("project");
    assertThat(backpressure.admit("project")).isFalse();

    reaper.run(NOW);

    assertThat(taskRepo.findById(expired.id()))
        .get()
        .extracting(Task::state)
        .isEqualTo(TaskState.TIMEOUT);
    assertThat(backpressure.admit("project")).isTrue();
  }

  private static Task task(TaskState state, Instant timeoutAt, Cursor cursor) {
    return new Task(
        TaskId.generate(),
        Resource.of("project", "1"),
        Set.of(),
        "{}",
        "{}",
        cursor,
        state,
        NOW.minusSeconds(120),
        timeoutAt,
        0L);
  }
}
