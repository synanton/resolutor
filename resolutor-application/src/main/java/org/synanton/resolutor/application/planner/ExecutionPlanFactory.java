package org.synanton.resolutor.application.planner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import org.jspecify.annotations.Nullable;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.plan.PlanId;
import org.synanton.resolutor.domain.plan.PlanMetrics;
import org.synanton.resolutor.domain.plan.SequentialGroup;
import org.synanton.resolutor.domain.policy.OrderingPolicy;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Assembles an {@link ExecutionPlan} from pipeline outputs: components, optional colours, ordering
 * policy, and metrics.
 */
final class ExecutionPlanFactory {

  private ExecutionPlanFactory() {}

  static ExecutionPlan build(
      List<Set<TaskId>> components,
      Map<TaskId, Task> taskIndex,
      ConflictGraph graph,
      OrderingPolicy orderingPolicy,
      Duration planningDuration,
      String plannerVersion) {
    return build(
        components,
        taskIndex,
        graph,
        orderingPolicy,
        planningDuration,
        plannerVersion,
        Map.of(),
        Map.of(),
        false,
        t -> 0.0);
  }

  static ExecutionPlan build(
      List<Set<TaskId>> components,
      Map<TaskId, Task> taskIndex,
      ConflictGraph graph,
      OrderingPolicy orderingPolicy,
      Duration planningDuration,
      String plannerVersion,
      Map<TaskId, Integer> colours) {
    return build(
        components,
        taskIndex,
        graph,
        orderingPolicy,
        planningDuration,
        plannerVersion,
        colours,
        Map.of(),
        false,
        t -> 0.0);
  }

  static ExecutionPlan build(
      List<Set<TaskId>> components,
      Map<TaskId, Task> taskIndex,
      ConflictGraph graph,
      OrderingPolicy orderingPolicy,
      Duration planningDuration,
      String plannerVersion,
      Map<TaskId, Integer> colours,
      Map<TaskId, Long> durationMillis,
      boolean localityEnabled,
      @Nullable ToDoubleFunction<Task> pressure) {

    Instant generatedAt = Instant.now();
    List<SequentialGroup> groups = new ArrayList<>(components.size());
    Map<TaskId, String> taskToComponent = new HashMap<>(taskIndex.size() * 2);
    Map<TaskId, Integer> colourIndex = colours == null ? Map.of() : colours;
    Map<TaskId, Long> estimates = durationMillis == null ? Map.of() : durationMillis;

    int maxWaveSize = 0;
    int waveCount = 0;
    int chromaticNumber = 0;
    long estimatedDurationMillis = 0L;

    for (int i = 0; i < components.size(); i++) {
      Set<TaskId> component = components.get(i);
      String componentId = "component-" + i;

      List<Task> tasksInComponent =
          component.stream().map(taskIndex::get).filter(Objects::nonNull).toList();

      List<ColourWave> waves =
          wavesFor(tasksInComponent, colourIndex, orderingPolicy, localityEnabled, pressure);
      List<TaskId> ordered = flatten(waves);
      groups.add(new SequentialGroup(componentId, ordered, waves));
      component.forEach(tid -> taskToComponent.put(tid, componentId));

      waveCount += waves.size();
      long groupCriticalPath = 0L;
      for (ColourWave wave : waves) {
        maxWaveSize = Math.max(maxWaveSize, wave.taskIds().size());
        chromaticNumber = Math.max(chromaticNumber, wave.colour() + 1);
        long waveMax = 0L;
        for (TaskId id : wave.taskIds()) {
          waveMax = Math.max(waveMax, estimates.getOrDefault(id, 0L));
        }
        groupCriticalPath += waveMax;
      }
      estimatedDurationMillis = Math.max(estimatedDurationMillis, groupCriticalPath);
    }

    int totalTasks = taskIndex.size();
    int largestComponent = components.stream().mapToInt(Set::size).max().orElse(0);
    double parallelismFactor =
        components.isEmpty() ? 0.0 : (double) components.size() / Math.max(1, largestComponent);
    double serializationRatio = totalTasks == 0 ? 0.0 : (double) largestComponent / totalTasks;
    double intra =
        largestComponent == 0 ? 0.0 : (double) maxWaveSize / Math.max(1, largestComponent);

    PlanMetrics metrics =
        new PlanMetrics(
            totalTasks,
            components.size(),
            largestComponent,
            parallelismFactor,
            serializationRatio,
            graph.edges().size(),
            chromaticNumber,
            waveCount,
            intra,
            estimatedDurationMillis);

    return new ExecutionPlan(
        PlanId.generate(),
        generatedAt,
        plannerVersion,
        planningDuration,
        orderingPolicy.name(),
        groups,
        metrics,
        taskToComponent);
  }

  private static List<ColourWave> wavesFor(
      List<Task> tasksInComponent,
      Map<TaskId, Integer> colours,
      OrderingPolicy orderingPolicy,
      boolean localityEnabled,
      @Nullable ToDoubleFunction<Task> pressure) {
    boolean coloured = tasksInComponent.stream().anyMatch(t -> colours.containsKey(t.id()));
    if (!coloured) {
      return ColourWave.serial(
          orderTasks(tasksInComponent, orderingPolicy, localityEnabled, pressure));
    }
    Map<Integer, List<Task>> byColour = new TreeMap<>();
    for (Task task : tasksInComponent) {
      int colour = colours.getOrDefault(task.id(), 0);
      byColour.computeIfAbsent(colour, c -> new ArrayList<>()).add(task);
    }
    List<ColourWave> waves = new ArrayList<>(byColour.size());
    for (Map.Entry<Integer, List<Task>> entry : byColour.entrySet()) {
      List<TaskId> ordered =
          orderTasks(entry.getValue(), orderingPolicy, localityEnabled, pressure);
      waves.add(new ColourWave(entry.getKey(), ordered));
    }
    return List.copyOf(waves);
  }

  private static List<TaskId> orderTasks(
      List<Task> tasks,
      OrderingPolicy orderingPolicy,
      boolean localityEnabled,
      @Nullable ToDoubleFunction<Task> pressure) {
    List<TaskId> base = orderingPolicy.order(tasks);
    if (!localityEnabled && pressure == null) {
      return base;
    }
    Map<TaskId, Integer> rank = new HashMap<>();
    for (int i = 0; i < base.size(); i++) {
      rank.put(base.get(i), i);
    }
    ToDoubleFunction<Task> p = pressure == null ? t -> 0.0 : pressure;
    Comparator<Task> cmp =
        Comparator.comparing((Task t) -> localityEnabled ? t.topResource().resourceClass() : "")
            .thenComparing(t -> localityEnabled ? t.topResource().resourceId() : "")
            .thenComparingDouble(p)
            .thenComparingInt(t -> rank.getOrDefault(t.id(), Integer.MAX_VALUE));
    return tasks.stream().sorted(cmp).map(Task::id).toList();
  }

  private static List<TaskId> flatten(List<ColourWave> waves) {
    List<TaskId> ordered = new ArrayList<>();
    for (ColourWave wave : waves) {
      ordered.addAll(wave.taskIds());
    }
    return List.copyOf(ordered);
  }
}
