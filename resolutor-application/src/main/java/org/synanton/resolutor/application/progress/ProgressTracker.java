package org.synanton.resolutor.application.progress;

import java.util.Objects;
import java.util.Optional;
import org.synanton.resolutor.application.port.in.ProgressDelta;
import org.synanton.resolutor.application.port.in.ProgressPort;
import org.synanton.resolutor.application.port.in.TaskStatusView;
import org.synanton.resolutor.application.port.in.TaskStatusView.ProgressView;
import org.synanton.resolutor.application.port.out.ProgressRepositoryPort;
import org.synanton.resolutor.application.port.out.ProgressSnapshot;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Implements {@link ProgressPort}: applies deltas to persisted snapshots and queries task status.
 */
public final class ProgressTracker implements ProgressPort {

  private final ProgressRepositoryPort progressRepo;
  private final TaskRepositoryPort taskRepo;

  public ProgressTracker(ProgressRepositoryPort progressRepo, TaskRepositoryPort taskRepo) {
    this.progressRepo = Objects.requireNonNull(progressRepo, "progressRepo");
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
  }

  /** Add {@code delta} to the stored snapshot, creating an initial row when none exists. */
  @Override
  public void updateProgress(TaskId id, ProgressDelta delta) {
    ProgressSnapshot current =
        progressRepo.findByTaskId(id).orElseGet(() -> ProgressSnapshot.initial(id));
    ProgressSnapshot updated =
        new ProgressSnapshot(
            id,
            current.totalCount() + delta.totalDelta(),
            current.successCount() + delta.successDelta(),
            current.failedCount() + delta.failedDelta(),
            current.version() + 1);
    progressRepo.save(updated);
  }

  /** Mark {@code id} COMPLETED unless it is already complete or missing. */
  @Override
  public void forceComplete(TaskId id) {
    Optional<Task> maybeTask = taskRepo.findById(id);
    if (maybeTask.isEmpty()) {
      return;
    }
    Task task = maybeTask.get();
    if (task.state() == TaskState.COMPLETED) {
      return;
    }
    taskRepo.save(task.withState(TaskState.COMPLETED));
  }

  /** Load the task and its progress snapshot. Throws if the task id is unknown. */
  @Override
  public TaskStatusView status(TaskId id) {
    Task task =
        taskRepo
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    Optional<ProgressSnapshot> snapshot = progressRepo.findByTaskId(id);
    ProgressView progressView =
        snapshot
            .filter(s -> s.totalCount() > 0)
            .map(s -> new ProgressView(s.totalCount(), s.successCount(), s.failedCount()))
            .orElse(null);
    return new TaskStatusView(id, task.state(), progressView);
  }
}
