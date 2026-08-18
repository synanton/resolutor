package org.synanton.resolutor.application.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.synanton.resolutor.application.planner.pass.ExecutionPlanPass;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.graph.ConflictGraph.Edge;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Welsh–Powell greedy colouring of each connected component. Adjacent tasks never share a colour.
 *
 * <p>Default vertex order: descending degree, then {@code TaskId} string. When duration estimates
 * are present (v4 cost model), longest tasks are coloured first (then degree, then id).
 */
public final class GraphColoringPass implements ExecutionPlanPass {

  public static final GraphColoringPass INSTANCE = new GraphColoringPass();

  @Override
  public String name() {
    return "graph-colouring";
  }

  @Override
  public PassState apply(PassState state) {
    Map<TaskId, Integer> colours = new HashMap<>(state.colours());
    for (Set<TaskId> component : state.components()) {
      colours.putAll(colourComponent(state.graph(), component, state.durationMillis()));
    }
    return new PassState(state.graph(), state.components(), colours, state.durationMillis());
  }

  static Map<TaskId, Integer> colourComponent(ConflictGraph graph, Set<TaskId> component) {
    return colourComponent(graph, component, Map.of());
  }

  static Map<TaskId, Integer> colourComponent(
      ConflictGraph graph, Set<TaskId> component, Map<TaskId, Long> durationMillis) {
    Map<TaskId, Set<TaskId>> adj = adjacency(graph, component);
    Map<TaskId, Long> durations = durationMillis == null ? Map.of() : durationMillis;
    List<TaskId> order = new ArrayList<>(component);
    order.sort(
        Comparator.<TaskId>comparingLong(v -> -durations.getOrDefault(v, 0L))
            .thenComparingInt(v -> -neighbours(adj, v).size())
            .thenComparing(v -> v.value().toString()));

    Map<TaskId, Integer> colours = new HashMap<>();
    for (TaskId vertex : order) {
      Set<Integer> forbidden = new HashSet<>();
      for (TaskId neighbour : neighbours(adj, vertex)) {
        Integer c = colours.get(neighbour);
        if (c != null) {
          forbidden.add(c);
        }
      }
      int colour = 0;
      while (forbidden.contains(colour)) {
        colour++;
      }
      colours.put(vertex, colour);
    }
    return Map.copyOf(colours);
  }

  private static Map<TaskId, Set<TaskId>> adjacency(ConflictGraph graph, Set<TaskId> component) {
    Map<TaskId, Set<TaskId>> adj = new HashMap<>();
    for (TaskId v : component) {
      adj.put(v, new HashSet<>());
    }
    for (Edge edge : graph.edges()) {
      if (component.contains(edge.first()) && component.contains(edge.second())) {
        neighbours(adj, edge.first()).add(edge.second());
        neighbours(adj, edge.second()).add(edge.first());
      }
    }
    return adj;
  }

  private static Set<TaskId> neighbours(Map<TaskId, Set<TaskId>> adj, TaskId vertex) {
    Set<TaskId> neighbours = adj.get(vertex);
    if (neighbours == null) {
      throw new IllegalStateException("vertex missing from adjacency: " + vertex);
    }
    return neighbours;
  }
}
