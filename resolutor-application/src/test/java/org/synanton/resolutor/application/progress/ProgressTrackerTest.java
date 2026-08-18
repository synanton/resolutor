package org.synanton.resolutor.application.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.fake.InMemoryProgressRepository;
import org.synanton.resolutor.application.fake.InMemoryTaskRepository;
import org.synanton.resolutor.application.port.in.ProgressDelta;
import org.synanton.resolutor.application.port.in.TaskStatusView;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class ProgressTrackerTest {

  private InMemoryTaskRepository taskRepo;
  private InMemoryProgressRepository progressRepo;
  private ProgressTracker tracker;

  @BeforeEach
  void setUp() {
    taskRepo = new InMemoryTaskRepository();
    progressRepo = new InMemoryProgressRepository();
    tracker = new ProgressTracker(progressRepo, taskRepo);
  }

  @Test
  void updateProgressCreatesSnapshotForNewTask() {
    Task t = pending();
    taskRepo.save(t);

    tracker.updateProgress(t.id(), ProgressDelta.success(5));

    var snapshot = progressRepo.findByTaskId(t.id());
    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().successCount()).isEqualTo(5);
    assertThat(snapshot.get().totalCount()).isEqualTo(5);
  }

  @Test
  void updateProgressAccumulatesDeltas() {
    Task t = pending();
    taskRepo.save(t);

    tracker.updateProgress(t.id(), ProgressDelta.success(3));
    tracker.updateProgress(t.id(), ProgressDelta.success(4));

    var snapshot = progressRepo.findByTaskId(t.id());
    assertThat(snapshot.get().successCount()).isEqualTo(7);
    assertThat(snapshot.get().totalCount()).isEqualTo(7);
  }

  @Test
  void updateProgressTracksFailures() {
    Task t = pending();
    taskRepo.save(t);

    tracker.updateProgress(t.id(), ProgressDelta.failed(2, 10));

    var snapshot = progressRepo.findByTaskId(t.id());
    assertThat(snapshot.get().failedCount()).isEqualTo(2);
    assertThat(snapshot.get().totalCount()).isEqualTo(10);
    assertThat(snapshot.get().successCount()).isZero();
  }

  @Test
  void forceCompleteTransitionsTaskState() {
    Task t = pending();
    taskRepo.save(t);

    tracker.forceComplete(t.id());

    var updated = taskRepo.findById(t.id());
    assertThat(updated).isPresent();
    assertThat(updated.get().state()).isEqualTo(TaskState.COMPLETED);
  }

  @Test
  void forceCompleteIsIdempotentWhenAlreadyCompleted() {
    Task t = pending().withState(TaskState.COMPLETED);
    taskRepo.save(t);

    tracker.forceComplete(t.id());

    assertThat(taskRepo.findById(t.id()).get().state()).isEqualTo(TaskState.COMPLETED);
  }

  @Test
  void forceCompleteIsNoOpForUnknownTask() {
    tracker.forceComplete(TaskId.generate());
    // No exception - silent no-op.
  }

  @Test
  void statusReturnsTaskStateAndNoProgressWhenNoSnapshot() {
    Task t = pending();
    taskRepo.save(t);

    TaskStatusView view = tracker.status(t.id());

    assertThat(view.state()).isEqualTo(TaskState.PENDING);
    assertThat(view.progress()).isNull();
  }

  @Test
  void statusReturnsProgressViewWhenSnapshotExists() {
    Task t = pending();
    taskRepo.save(t);
    tracker.updateProgress(t.id(), ProgressDelta.success(10));

    TaskStatusView view = tracker.status(t.id());

    assertThat(view.progress()).isNotNull();
    assertThat(view.progress().totalCount()).isEqualTo(10);
    assertThat(view.progress().pendingCount()).isZero();
  }

  @Test
  void statusThrowsForUnknownTask() {
    assertThatThrownBy(() -> tracker.status(TaskId.generate()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static Task pending() {
    return new Task(
        TaskId.generate(),
        Resource.of("report", "r1"),
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
