package org.synanton.resolutor.application.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Decomposes a {@link ConflictGraph} into its connected components using iterative union-find with
 * path compression and union by rank.
 *
 * <p>Output is deterministic: vertices are sorted lexicographically by {@code TaskId.value()}
 * before processing, and components are returned in order of their lexicographically-smallest
 * member. Complexity: {@code O((V + E) · α(V))} ≈ {@code O(V + E)}.
 */
public final class ConnectedComponents {

  private ConnectedComponents() {}

  static List<Set<TaskId>> of(ConflictGraph graph) {
    if (graph.vertices().isEmpty()) {
      return List.of();
    }

    List<TaskId> sorted =
        graph.vertices().stream().sorted(Comparator.comparing(t -> t.value().toString())).toList();

    UnionFind uf = new UnionFind(sorted);

    for (ConflictGraph.Edge edge : graph.edges()) {
      uf.union(edge.first(), edge.second());
    }

    // Collect into components keyed by root, preserving insertion order for determinism.
    Map<TaskId, Set<TaskId>> byRoot = new LinkedHashMap<>();
    for (TaskId v : sorted) {
      TaskId root = uf.find(v);
      byRoot.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(v);
    }

    return new ArrayList<>(byRoot.values());
  }

  private static final class UnionFind {

    private final Map<TaskId, TaskId> parent;
    private final Map<TaskId, Integer> rank;

    UnionFind(List<TaskId> vertices) {
      parent = new HashMap<>(vertices.size() * 2);
      rank = new HashMap<>(vertices.size() * 2);
      for (TaskId v : vertices) {
        parent.put(v, v);
        rank.put(v, 0);
      }
    }

    /** Iterative find with full path compression. */
    TaskId find(TaskId x) {
      // Walk to root.
      TaskId root = x;
      while (true) {
        TaskId p = parent.get(root);
        if (p == null || p.equals(root)) break;
        root = p;
      }
      // Compress path.
      TaskId current = x;
      while (!current.equals(root)) {
        TaskId next = parent.get(current);
        parent.put(current, root);
        if (next == null) break;
        current = next;
      }
      return root;
    }

    void union(TaskId x, TaskId y) {
      TaskId rootX = find(x);
      TaskId rootY = find(y);
      if (rootX.equals(rootY)) return;

      int rankX = rank.getOrDefault(rootX, 0);
      int rankY = rank.getOrDefault(rootY, 0);
      if (rankX < rankY) {
        parent.put(rootX, rootY);
      } else if (rankX > rankY) {
        parent.put(rootY, rootX);
      } else {
        parent.put(rootY, rootX);
        rank.put(rootX, rankX + 1);
      }
    }
  }
}
