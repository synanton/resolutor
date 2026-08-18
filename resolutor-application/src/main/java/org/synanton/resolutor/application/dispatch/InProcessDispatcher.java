package org.synanton.resolutor.application.dispatch;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.planner.TaskDurationEstimator;
import org.synanton.resolutor.application.port.out.DispatcherPort;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.TaskRepositoryPort;
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * In-process implementation of {@link DispatcherPort}. Executes each {@link SequentialGroup} on a
 * Java 21 virtual thread. Within a group, colour waves run in order; tasks in the same wave run
 * concurrently on virtual threads.
 *
 * <p>See {@code docs/implementation-plan.md} §7 and §18 (v3). Failures within a group do not cancel
 * siblings - we use plain futures (not {@code StructuredTaskScope}) to preserve fault isolation
 * between groups.
 */
public final class InProcessDispatcher implements DispatcherPort, AutoCloseable {

  private static final System.Logger LOG = System.getLogger(InProcessDispatcher.class.getName());

  private final TaskRepositoryPort taskRepo;
  private final TaskWorker worker;
  private final BackpressureManager backpressure;
  private final MetricsPort metrics;
  private final DispatchConfig config;
  private final @Nullable TaskDurationEstimator estimator;
  private final ExecutorService groupExecutor;
  private final @Nullable Semaphore concurrencyLimit;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public InProcessDispatcher(
      TaskRepositoryPort taskRepo,
      TaskWorker worker,
      BackpressureManager backpressure,
      MetricsPort metrics,
      DispatchConfig config) {
    this(taskRepo, worker, backpressure, metrics, config, null);
  }

  public InProcessDispatcher(
      TaskRepositoryPort taskRepo,
      TaskWorker worker,
      BackpressureManager backpressure,
      MetricsPort metrics,
      DispatchConfig config,
      @Nullable TaskDurationEstimator estimator) {
    this.taskRepo = Objects.requireNonNull(taskRepo, "taskRepo");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.backpressure = Objects.requireNonNull(backpressure, "backpressure");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.config = Objects.requireNonNull(config, "config");
    this.estimator = estimator;
    this.groupExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.concurrencyLimit =
        config.maxConcurrentGroups() == 0 ? null : new Semaphore(config.maxConcurrentGroups());
  }

  /**
   * Submit every group in {@code plan} to the virtual-thread executor and wait for completion.
   * Returns the aggregate per-group results. Failures in one group do not cancel siblings.
   */
  @Override
  public List<GroupResult> runPlan(ExecutionPlan plan) {
    Objects.requireNonNull(plan, "plan");
    if (closed.get() || plan.groups().isEmpty()) {
      return List.of();
    }

    List<Future<GroupResult>> futures = new ArrayList<>(plan.groups().size());
    for (SequentialGroup group : plan.groups()) {
      futures.add(groupExecutor.submit(() -> dispatch(group)));
    }

    List<GroupResult> results = new ArrayList<>(futures.size());
    for (Iterator<Future<GroupResult>> it = futures.iterator(); it.hasNext(); ) {
      Future<GroupResult> future = it.next();
      try {
        results.add(future.get());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.log(System.Logger.Level.WARNING, "Dispatch interrupted; cancelling remaining groups");
        future.cancel(true);
        while (it.hasNext()) {
          it.next().cancel(true);
        }
        return List.copyOf(results);
      } catch (ExecutionException e) {
        LOG.log(System.Logger.Level.ERROR, "Group execution raised", e.getCause());
      }
    }
    return List.copyOf(results);
  }

  /**
   * Run {@code group} on this thread (after acquiring the optional concurrency permit). Waves
   * execute in colour order; a failed task does not skip later tasks in the same group.
   */
  @Override
  public GroupResult dispatch(SequentialGroup group) {
    Objects.requireNonNull(group, "group");
    Instant start = Instant.now();
    @Nullable Semaphore permit = concurrencyLimit;
    boolean acquired = false;
    if (permit != null) {
      try {
        permit.acquire();
        acquired = true;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new GroupResult(group.componentId(), List.of());
      }
    }
    boolean succeeded = false;
    try {
      List<TaskResult> results = new ArrayList<>(group.orderedTasks().size());
      for (ColourWave wave : group.waves()) {
        results.addAll(runWave(wave.taskIds()));
      }
      succeeded = results.stream().allMatch(TaskResult::success);
      return new GroupResult(group.componentId(), results);
    } finally {
      if (acquired && permit != null) {
        permit.release();
      }
      Duration elapsed = Duration.between(start, Instant.now());
      metrics.recordGroupDispatched(group.componentId(), elapsed, succeeded);
    }
  }

  /** Shut down the executor and wait up to 30s for in-flight groups to drain. */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    groupExecutor.shutdown();
    try {
      if (!groupExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        groupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      groupExecutor.shutdownNow();
    }
  }

  private List<TaskResult> runWave(List<TaskId> taskIds) {
    if (taskIds.size() <= 1) {
      List<TaskResult> serial = new ArrayList<>(taskIds.size());
      for (TaskId taskId : taskIds) {
        serial.add(runOne(taskId));
      }
      return serial;
    }
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<TaskResult>> futures = new ArrayList<>(taskIds.size());
      for (TaskId taskId : taskIds) {
        futures.add(pool.submit(() -> runOne(taskId)));
      }
      List<TaskResult> results = new ArrayList<>(taskIds.size());
      for (int i = 0; i < futures.size(); i++) {
        Future<TaskResult> future = futures.get(i);
        try {
          results.add(future.get());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          for (int j = i; j < futures.size(); j++) {
            futures.get(j).cancel(true);
          }
          results.add(TaskResult.failed(taskIds.get(i), "Dispatch interrupted"));
          for (int j = i + 1; j < taskIds.size(); j++) {
            results.add(TaskResult.failed(taskIds.get(j), "Dispatch interrupted"));
          }
          return results;
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          String reason =
              cause == null || cause.getMessage() == null ? "wave failed" : cause.getMessage();
          results.add(TaskResult.failed(taskIds.get(i), reason));
        }
      }
      return results;
    }
  }

  private TaskResult runOne(TaskId taskId) {
    Optional<Task> maybe = taskRepo.findById(taskId);
    if (maybe.isEmpty()) {
      return TaskResult.failed(taskId, "Task not found");
    }
    Task task = maybe.get();
    if (task.state() == TaskState.COMPLETED) {
      return TaskResult.ok(taskId);
    }
    @Nullable Instant taskDeadline = task.timeoutAt();
    if (taskDeadline != null && !Instant.now().isBefore(taskDeadline)) {
      taskRepo.save(task.withState(TaskState.TIMEOUT));
      backpressure.onCompleted(task.topResource().resourceClass());
      return TaskResult.failed(taskId, "Task timed out before dispatch");
    }

    Task started = task.withState(TaskState.STARTED);
    taskRepo.save(started);
    backpressure.onEmitted(task.topResource().resourceClass());

    Instant deadline = Instant.now().plus(config.taskTimeout());
    Instant execStart = Instant.now();
    TaskWorker.Result result;
    try {
      result = worker.execute(started);
    } catch (RuntimeException ex) {
      recordDuration(task, execStart);
      LOG.log(System.Logger.Level.WARNING, "Worker threw for task " + taskId, ex);
      taskRepo.save(started.withState(TaskState.FAILED));
      backpressure.onCompleted(task.topResource().resourceClass());
      return TaskResult.failed(taskId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
    recordDuration(task, execStart);

    boolean deadlineExceeded = Instant.now().isAfter(deadline);
    Task finalTask;
    TaskResult wire;
    switch (result.outcome()) {
      case COMPLETED -> {
        finalTask = started.withState(TaskState.COMPLETED);
        wire = TaskResult.ok(taskId);
      }
      case PAUSED -> {
        @Nullable Cursor cursor = result.cursor();
        Task withCursor = cursor == null ? started : started.withCursor(cursor);
        finalTask = withCursor.withState(TaskState.PAUSED);
        wire = TaskResult.ok(taskId);
      }
      case FAILED -> {
        finalTask = started.withState(TaskState.FAILED);
        @Nullable String reason = result.failureReason();
        wire = TaskResult.failed(taskId, reason == null ? "failed" : reason);
      }
      default -> throw new IllegalStateException("Unknown outcome: " + result.outcome());
    }
    if (deadlineExceeded && result.outcome() != TaskWorker.Outcome.PAUSED) {
      finalTask = finalTask.withState(TaskState.TIMEOUT);
      wire = TaskResult.failed(taskId, "Task exceeded deadline (" + config.taskTimeout() + ")");
      LOG.log(System.Logger.Level.WARNING, "Task {0} exceeded deadline", taskId);
      throwIfInterrupted();
    }
    taskRepo.save(finalTask);
    backpressure.onCompleted(task.topResource().resourceClass());
    return wire;
  }

  private void recordDuration(Task task, Instant execStart) {
    Duration elapsed = Duration.between(execStart, Instant.now());
    metrics.recordTaskDuration(task.topResource().resourceClass(), elapsed);
    if (estimator != null) {
      estimator.record(task.topResource().resourceClass(), elapsed);
    }
  }

  private static void throwIfInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new IllegalStateException("Dispatch interrupted", new InterruptedException());
    }
  }
}
