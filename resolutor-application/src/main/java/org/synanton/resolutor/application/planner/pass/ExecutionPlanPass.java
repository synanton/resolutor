package org.synanton.resolutor.application.planner.pass;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * One optimisation pass over the conflict-graph IR. Passes must be deterministic and must not
 * introduce edges.
 */
public interface ExecutionPlanPass {

  /** Stable name for logs and plan diagnostics. */
  String name();

  /** Return a new {@link PassState}; input is treated as immutable. */
  PassState apply(PassState state);

  /**
   * Graph, connected-component partition, optional per-task colours, and optional duration
   * estimates in milliseconds (empty until the cost model fills them).
   */
  record PassState(
      ConflictGraph graph,
      List<Set<TaskId>> components,
      Map<TaskId, Integer> colours,
      Map<TaskId, Long> durationMillis) {

    public PassState {
      Objects.requireNonNull(graph, "graph");
      components = List.copyOf(components);
      colours = Map.copyOf(colours);
      durationMillis = Map.copyOf(durationMillis);
    }

    public PassState(
        ConflictGraph graph, List<Set<TaskId>> components, Map<TaskId, Integer> colours) {
      this(graph, components, colours, Map.of());
    }

    public static PassState of(ConflictGraph graph) {
      return new PassState(graph, List.of(), Map.of(), Map.of());
    }
  }
}
