package org.synanton.resolutor.application.planner;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.planner.pass.ExecutionPlanPass;
import org.synanton.resolutor.application.planner.pass.ExecutionPlanPass.PassState;
import org.synanton.resolutor.application.port.out.MetricsPort;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Orchestrates the planning pipeline: resolve → graph → {@link ExecutionPlanPass}es → backpressure
 * → {@link ExecutionPlan}.
 *
 * <p>Resource resolution runs concurrently on virtual threads with a per-compile {@link TaskId}
 * cache. Extra passes plug in without changing {@code DispatcherPort}.
 */
public final class ExecutionPlanner {

  private static final Logger LOG = System.getLogger(ExecutionPlanner.class.getName());

  private final ResourceGraphPort resourceGraphPort;
  private final BackpressureManager backpressureManager;
  private final MetricsPort metrics;
  private final PlannerConfig config;
  private final List<ExecutionPlanPass> passes;
  private final TaskDurationEstimator estimator;

  public ExecutionPlanner(
      ResourceGraphPort resourceGraphPort,
      BackpressureManager backpressureManager,
      MetricsPort metrics,
      PlannerConfig config) {
    this(
        resourceGraphPort,
        backpressureManager,
        metrics,
        config,
        defaultPasses(config),
        TaskDurationEstimator.of(config));
  }

  public ExecutionPlanner(
      ResourceGraphPort resourceGraphPort,
      BackpressureManager backpressureManager,
      MetricsPort metrics,
      PlannerConfig config,
      TaskDurationEstimator estimator) {
    this(resourceGraphPort, backpressureManager, metrics, config, defaultPasses(config), estimator);
  }

  public ExecutionPlanner(
      ResourceGraphPort resourceGraphPort,
      BackpressureManager backpressureManager,
      MetricsPort metrics,
      PlannerConfig config,
      List<ExecutionPlanPass> passes) {
    this(
        resourceGraphPort,
        backpressureManager,
        metrics,
        config,
        passes,
        TaskDurationEstimator.of(config));
  }

  public ExecutionPlanner(
      ResourceGraphPort resourceGraphPort,
      BackpressureManager backpressureManager,
      MetricsPort metrics,
      PlannerConfig config,
      List<ExecutionPlanPass> passes,
      TaskDurationEstimator estimator) {
    this.resourceGraphPort = Objects.requireNonNull(resourceGraphPort, "resourceGraphPort");
    this.backpressureManager = Objects.requireNonNull(backpressureManager, "backpressureManager");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.config = Objects.requireNonNull(config, "config");
    this.passes = List.copyOf(Objects.requireNonNull(passes, "passes"));
    this.estimator = Objects.requireNonNull(estimator, "estimator");
    if (this.passes.isEmpty()) {
      throw new IllegalArgumentException("at least one ExecutionPlanPass is required");
    }
  }

  private static List<ExecutionPlanPass> defaultPasses(PlannerConfig config) {
    if (config.colouringEnabled()) {
      return List.of(ConnectedComponentsPass.INSTANCE, GraphColoringPass.INSTANCE);
    }
    return List.of(ConnectedComponentsPass.INSTANCE);
  }

  /**
   * Compile an {@link ExecutionPlan} for the given batch.
   *
   * <p>Tasks whose resource resolution fails are skipped (logged at WARN) and excluded from the
   * plan - they remain PENDING for the next tick. Backpressure may drop a whole component (v1) or,
   * when {@link PlannerConfig#backpressureReorderEnabled()}, drop only tasks whose class is over
   * limit.
   */
  public ExecutionPlan compile(List<Task> tasks) {
    if (tasks.isEmpty()) {
      return ExecutionPlanFactory.build(
          List.of(),
          Map.of(),
          ConflictGraph.empty(),
          config.orderingPolicy(),
          Duration.ZERO,
          config.plannerVersion());
    }

    Instant start = Instant.now();

    List<Task> resolved = resolveAll(tasks);
    Map<TaskId, Long> durations = new HashMap<>();
    if (config.costEnabled()) {
      for (Task task : resolved) {
        durations.put(task.id(), estimator.estimate(task).toMillis());
      }
    }
    ConflictGraph graph = ConflictGraphBuilder.build(resolved, config.maxBucketSize());
    PassState state = new PassState(graph, List.of(), Map.of(), durations);
    for (ExecutionPlanPass pass : passes) {
      state = pass.apply(state);
    }

    Map<TaskId, Task> taskIndex =
        resolved.stream().collect(Collectors.toMap(Task::id, Function.identity()));
    List<Set<TaskId>> admitted = filterByBackpressure(state.components(), taskIndex);

    Duration elapsed = Duration.between(start, Instant.now());
    @Nullable ToDoubleFunction<Task> pressure =
        config.backpressureReorderEnabled()
            ? t -> backpressureManager.pressure(t.topResource().resourceClass())
            : null;
    return ExecutionPlanFactory.build(
        admitted,
        taskIndex,
        graph,
        config.orderingPolicy(),
        elapsed,
        config.plannerVersion(),
        state.colours(),
        durations,
        config.localityEnabled(),
        pressure);
  }

  private List<Task> resolveAll(List<Task> tasks) {
    Map<TaskId, Set<Resource>> cache = new ConcurrentHashMap<>();
    List<Task> result = new ArrayList<>(tasks.size());
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<@Nullable Task>> futures = new ArrayList<>(tasks.size());
      for (Task task : tasks) {
        futures.add(pool.submit(() -> resolveOne(task, cache)));
      }
      for (Future<@Nullable Task> future : futures) {
        try {
          Task resolved = future.get();
          if (resolved != null) {
            result.add(resolved);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          LOG.log(Level.WARNING, "Resource resolution interrupted; returning partial batch");
          return result;
        } catch (ExecutionException e) {
          LOG.log(Level.WARNING, "Resource resolution worker failed", e.getCause());
        }
      }
    }
    return result;
  }

  private @Nullable Task resolveOne(Task task, Map<TaskId, Set<Resource>> cache) {
    try {
      Set<Resource> resources =
          cache.computeIfAbsent(task.id(), id -> resourceGraphPort.resolve(task));
      metrics.recordResourceGraphCall("success");
      return task.withResolvedResources(resources);
    } catch (Exception ex) {
      metrics.recordResourceGraphCall(isTimeout(ex) ? "timeout" : "failure");
      LOG.log(
          Level.WARNING,
          "Resource resolution failed for task {0}, skipping: {1}",
          task.id(),
          ex.getMessage());
      return null;
    }
  }

  private List<Set<TaskId>> filterByBackpressure(
      List<Set<TaskId>> components, Map<TaskId, Task> taskIndex) {
    if (!backpressureManager.isEnabled()) {
      return components;
    }
    List<Set<TaskId>> admitted = new ArrayList<>(components.size());
    for (Set<TaskId> component : components) {
      if (config.backpressureReorderEnabled()) {
        Set<TaskId> kept = new LinkedHashSet<>();
        for (TaskId tid : component) {
          Task t = taskIndex.get(tid);
          if (t == null) {
            continue;
          }
          String cls = t.topResource().resourceClass();
          if (backpressureManager.admit(cls)) {
            kept.add(tid);
          } else {
            backpressureManager.recordDenied(cls);
            metrics.recordBackpressureDenied(cls);
          }
        }
        if (!kept.isEmpty()) {
          admitted.add(Set.copyOf(kept));
        }
        continue;
      }
      boolean allAdmitted = true;
      Map<String, Long> classCounts = new HashMap<>();
      for (TaskId tid : component) {
        Task t = taskIndex.get(tid);
        if (t == null) {
          allAdmitted = false;
          break;
        }
        String cls = t.topResource().resourceClass();
        classCounts.merge(cls, 1L, Long::sum);
        if (!backpressureManager.admit(cls)) {
          allAdmitted = false;
          break;
        }
      }
      if (allAdmitted) {
        admitted.add(component);
      } else {
        classCounts
            .keySet()
            .forEach(
                cls -> {
                  backpressureManager.recordDenied(cls);
                  metrics.recordBackpressureDenied(cls);
                });
      }
    }
    return admitted;
  }

  private static boolean isTimeout(Throwable ex) {
    for (Throwable current = ex; current != null; current = current.getCause()) {
      if (current instanceof java.util.concurrent.TimeoutException) {
        return true;
      }
      String name = current.getClass().getSimpleName();
      if (name.contains("Timeout") || name.contains("TimedOut")) {
        return true;
      }
    }
    return false;
  }
}
