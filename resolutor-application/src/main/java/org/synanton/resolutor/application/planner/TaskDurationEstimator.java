package org.synanton.resolutor.application.planner;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.synanton.resolutor.domain.task.Task;

/**
 * Per-task duration estimates: configured per resource class, else exponential histogram mean from
 * observed dispatch, else {@code defaultDuration}.
 */
public final class TaskDurationEstimator {

  private final Duration defaultDuration;
  private final Map<String, Duration> perClass;
  private final ConcurrentHashMap<String, LongAdder> sumMillis = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

  public TaskDurationEstimator(Duration defaultDuration, Map<String, Duration> perClass) {
    this.defaultDuration = Objects.requireNonNull(defaultDuration, "defaultDuration");
    this.perClass = Map.copyOf(Objects.requireNonNull(perClass, "perClass"));
  }

  public static TaskDurationEstimator of(PlannerConfig config) {
    return new TaskDurationEstimator(config.defaultTaskDuration(), config.taskDurations());
  }

  /** Estimate wall time for {@code task} (never negative). */
  public Duration estimate(Task task) {
    Objects.requireNonNull(task, "task");
    String cls = task.topResource().resourceClass();
    Duration configured = perClass.get(cls);
    if (configured != null) {
      return configured;
    }
    LongAdder count = counts.get(cls);
    LongAdder sum = sumMillis.get(cls);
    if (count != null && sum != null && count.sum() > 0) {
      return Duration.ofMillis(Math.max(0L, sum.sum() / count.sum()));
    }
    return defaultDuration;
  }

  /** Record an observed dispatch duration for the resource class. */
  public void record(String resourceClass, Duration elapsed) {
    Objects.requireNonNull(resourceClass, "resourceClass");
    Objects.requireNonNull(elapsed, "elapsed");
    if (elapsed.isNegative()) {
      return;
    }
    sumMillis.computeIfAbsent(resourceClass, k -> new LongAdder()).add(elapsed.toMillis());
    counts.computeIfAbsent(resourceClass, k -> new LongAdder()).increment();
  }
}
