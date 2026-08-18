package org.synanton.resolutor.domain.graph;

import java.util.Objects;
import java.util.Set;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Undirected conflict graph {@code G = (V, E)} where {@code V} = tasks and {@code (u,v) ∈ E} iff
 * tasks {@code u} and {@code v} share at least one resource.
 *
 * <p>This is the primary IR produced by {@code ConflictGraphBuilder} and consumed by optimisation
 * passes. See docs/design.md §8 for the formal definition.
 */
public record ConflictGraph(Set<TaskId> vertices, Set<Edge> edges) {

  public ConflictGraph {
    vertices = Set.copyOf(vertices);
    edges = Set.copyOf(edges);
  }

  public static ConflictGraph empty() {
    return new ConflictGraph(Set.of(), Set.of());
  }

  /**
   * Undirected edge between two tasks. Equality is symmetric: {@code Edge(a,b).equals(Edge(b,a))}.
   * The compact constructor canonicalises vertex order so the record's structural equality holds.
   */
  public record Edge(TaskId first, TaskId second) {

    public Edge {
      Objects.requireNonNull(first, "first");
      Objects.requireNonNull(second, "second");
      if (first.equals(second)) throw new IllegalArgumentException("self-loops are not allowed");
      // Canonicalise: smaller UUID string is always `first` so Edge(a,b).equals(Edge(b,a)).
      if (first.value().toString().compareTo(second.value().toString()) > 0) {
        TaskId tmp = first;
        first = second;
        second = tmp;
      }
    }
  }
}
