package org.synanton.resolutor.application.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Implements {@link TaskIngestionPort}: persists a new task already in {@code PENDING} so the
 * planner can admit it on the next tick (design §23 {@code RECEIVED → PENDING} is completed in this
 * write).
 */
public final class TaskIngestionService implements TaskIngestionPort {

  private final TaskRepositoryPort taskRepo;
  private final MetricsPort metrics;
  private final Clock clock;

  public TaskIngestionService(TaskRepositoryPort taskRepo, MetricsPort metrics, Clock clock) {
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public TaskId ingest(NewTaskCommand cmd) {
    TaskId id = TaskId.generate();
    Task task =
        new Task(
            id,
            cmd.topResource(),
            Set.of(),
            cmd.searchDsl(),
            cmd.payload(),
            null,
            TaskState.PENDING,
            Instant.now(clock),
            cmd.timeoutAt(),
            0L);
    taskRepo.save(task);
    metrics.incrementTasksIngested(1);
    return id;
  }
}
