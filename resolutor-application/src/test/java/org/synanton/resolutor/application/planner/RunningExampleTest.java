package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.policy.FifoPolicy;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Golden test for the running example from docs/design.md §10.
 *
 * <pre>
 * Task A: Delete Project 7     → {Project 7, Talk 41, Tag 8}
 * Task B: Update Conference 42 → {Project 7}            ← Room 3 removed vs. original text;
 *                                                          design §10 lists "Room 3: B─E" as an
 *                                                          edge but then concludes two components,
 *                                                          which is only consistent if B does not
 *                                                          touch Room 3.
 * Task C: Archive Talk 41      → {Talk 41, Assistant 2}
 * Task D: Remove Project 7     → {Project 7}
 * Task E: Book Room 3          → {Room 3, Project 9}
 * </pre>
 *
 * <p>Expected conflict edges: A─B, A─D, B─D (via Project 7) and A─C (via Talk 41). Expected
 * connected components: {@code {A,B,C,D}} and {@code {E}}.
 */
class RunningExampleTest {

  private static final Instant BASE = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void planHasTwoComponentsWithLargestFour() {
    var tasks = buildTasks();
    var graph = ConflictGraphBuilder.build(tasks);
    var components = ConnectedComponents.of(graph);
    Map<TaskId, Task> index =
        tasks.stream().collect(Collectors.toMap(Task::id, Function.identity()));
    ExecutionPlan plan =
        ExecutionPlanFactory.build(
            components,
            index,
            graph,
            FifoPolicy.INSTANCE,
            Duration.ofMillis(5),
            "1.0.0-test",
            coloursOf(graph, components));

    assertThat(plan.metrics().connectedComponents())
        .as("must have exactly 2 connected components")
        .isEqualTo(2);
    assertThat(plan.metrics().largestComponent())
        .as("largest component must contain 4 tasks (A,B,C,D)")
        .isEqualTo(4);
    assertThat(plan.metrics().totalTasks()).isEqualTo(5);
    assertThat(plan.groups()).hasSize(2);
    int maxWave =
        plan.groups().stream()
            .flatMap(g -> g.waves().stream())
            .mapToInt(w -> w.taskIds().size())
            .max()
            .orElse(0);
    assertThat(maxWave)
        .as("C can share a colour with B or D; clique ABD still forces χ=3")
        .isEqualTo(2);
    assertThat(plan.metrics().chromaticNumber()).isEqualTo(3);
  }

  @Test
  void taskEIsInItsOwnGroup() {
    var tasks = buildTasks();
    var taskE =
        tasks.stream()
            .filter(t -> t.topResource().resourceClass().equals("room"))
            .findFirst()
            .orElseThrow();

    var graph = ConflictGraphBuilder.build(tasks);
    var components = ConnectedComponents.of(graph);
    Map<TaskId, Task> index =
        tasks.stream().collect(Collectors.toMap(Task::id, Function.identity()));
    ExecutionPlan plan =
        ExecutionPlanFactory.build(
            components,
            index,
            graph,
            FifoPolicy.INSTANCE,
            Duration.ofMillis(5),
            "1.0.0-test",
            coloursOf(graph, components));

    var groupForE =
        plan.groups().stream()
            .filter(g -> g.orderedTasks().contains(taskE.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Task E not found in any group"));

    assertThat(groupForE.size())
        .as("Task E is independent and must be in a group by itself")
        .isEqualTo(1);
  }

  @Test
  void abcdGroupContainsAllFourTasks() {
    var tasks = buildTasks();
    var taskE =
        tasks.stream()
            .filter(t -> t.topResource().resourceClass().equals("room"))
            .findFirst()
            .orElseThrow();

    var graph = ConflictGraphBuilder.build(tasks);
    var components = ConnectedComponents.of(graph);
    Map<TaskId, Task> index =
        tasks.stream().collect(Collectors.toMap(Task::id, Function.identity()));
    ExecutionPlan plan =
        ExecutionPlanFactory.build(
            components,
            index,
            graph,
            FifoPolicy.INSTANCE,
            Duration.ofMillis(5),
            "1.0.0-test",
            coloursOf(graph, components));

    var largeGroup =
        plan.groups().stream()
            .filter(g -> !g.orderedTasks().contains(taskE.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Large group not found"));

    assertThat(largeGroup.size()).isEqualTo(4);
  }

  @Test
  void conflictEdgesCountMatchesDesign() {
    // Design §10: edges via Project7 (A─B, A─D, B─D) + Talk41 (A─C) = 4 edges.
    var tasks = buildTasks();
    var graph = ConflictGraphBuilder.build(tasks);

    assertThat(graph.edges())
        .as("conflict edges: A─B, A─D, B─D (Project 7) and A─C (Talk 41)")
        .hasSize(4);
  }

  @Test
  void planMetricsParallelismAndSerializationAreConsistent() {
    var tasks = buildTasks();
    var graph = ConflictGraphBuilder.build(tasks);
    var components = ConnectedComponents.of(graph);
    Map<TaskId, Task> index =
        tasks.stream().collect(Collectors.toMap(Task::id, Function.identity()));
    ExecutionPlan plan =
        ExecutionPlanFactory.build(
            components,
            index,
            graph,
            FifoPolicy.INSTANCE,
            Duration.ofMillis(5),
            "1.0.0-test",
            coloursOf(graph, components));

    // parallelismFactor = components / max(1, largestComponent) = 2 / 4 = 0.5
    assertThat(plan.metrics().parallelismFactor()).isEqualTo(0.5);
    // serializationRatio = largestComponent / totalTasks = 4 / 5 = 0.8
    assertThat(plan.metrics().serializationRatio()).isEqualTo(0.8);
  }

  @Test
  void planIsReproducible() {
    var tasks = buildTasks();
    Map<TaskId, Task> index =
        tasks.stream().collect(Collectors.toMap(Task::id, Function.identity()));

    ExecutionPlan first = compile(tasks, index);
    ExecutionPlan second = compile(tasks, index);

    // Same logical structure (groups sizes, metrics) even if generatedAt differs.
    assertThat(first.metrics()).isEqualTo(second.metrics());
    assertThat(first.groups().stream().map(g -> g.orderedTasks()).toList())
        .isEqualTo(second.groups().stream().map(g -> g.orderedTasks()).toList());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static List<Task> buildTasks() {
    var project7 = Resource.of("project", "7");
    var talk41 = Resource.of("talk", "41");
    var tag8 = Resource.of("tag", "8");
    var assistant2 = Resource.of("assistant", "2");
    var room3 = Resource.of("room", "3");
    var project9 = Resource.of("project", "9");

    Task a = task(Resource.of("project", "7"), Set.of(project7, talk41, tag8), BASE);
    Task b = task(Resource.of("conference", "42"), Set.of(project7), BASE.plusSeconds(1));
    Task c = task(Resource.of("talk", "41"), Set.of(talk41, assistant2), BASE.plusSeconds(2));
    Task d = task(Resource.of("project", "7-remove"), Set.of(project7), BASE.plusSeconds(3));
    Task e = task(Resource.of("room", "3"), Set.of(room3, project9), BASE.plusSeconds(4));

    return List.of(a, b, c, d, e);
  }

  private static Task task(Resource top, Set<Resource> resources, Instant createdAt) {
    return new Task(
        TaskId.generate(), top, resources, "{}", "{}", null, TaskState.PENDING, createdAt, null, 0);
  }

  private static ExecutionPlan compile(List<Task> tasks, Map<TaskId, Task> index) {
    var graph = ConflictGraphBuilder.build(tasks);
    var components = ConnectedComponents.of(graph);
    return ExecutionPlanFactory.build(
        components,
        index,
        graph,
        FifoPolicy.INSTANCE,
        Duration.ofMillis(1),
        "1.0.0-test",
        coloursOf(graph, components));
  }

  private static Map<TaskId, Integer> coloursOf(ConflictGraph graph, List<Set<TaskId>> components) {
    Map<TaskId, Integer> colours = new HashMap<>();
    for (Set<TaskId> component : components) {
      colours.putAll(GraphColoringPass.colourComponent(graph, component));
    }
    return colours;
  }
}
