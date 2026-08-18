package org.synanton.resolutor.application.planner;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.graph.ConflictGraph.Edge;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;

/**
 * Builds a {@link ConflictGraph} from a batch of tasks whose resources have already been resolved.
 *
 * <p>Algorithm (docs/design.md §12):
 *
 * <ol>
 *   <li>Invert resource → tasks: for each task, add it to every resource's bucket.
 *   <li>For each bucket, emit an edge for every pair (kᵢ choose 2).
 * </ol>
 *
 * <p>Complexity: {@code O(T·R + Σ kᵢ²)} where {@code kᵢ} is the bucket size. In practice bounded
 * fan-in means {@code O(T·R)}.
 */
final class ConflictGraphBuilder {

  private static final Logger LOG = System.getLogger(ConflictGraphBuilder.class.getName());

  private ConflictGraphBuilder() {}

  static ConflictGraph build(List<Task> tasks) {
    return build(tasks, Integer.MAX_VALUE);
  }

  static ConflictGraph build(List<Task> tasks, int maxBucketSize) {
    if (tasks.isEmpty()) {
      return ConflictGraph.empty();
    }

    Set<TaskId> vertices = new LinkedHashSet<>(tasks.size());
    Map<Resource, List<TaskId>> buckets = new HashMap<>();

    for (Task task : tasks) {
      vertices.add(task.id());
      for (Resource resource : task.resolvedResources()) {
        buckets.computeIfAbsent(resource, k -> new ArrayList<>()).add(task.id());
      }
    }

    Set<Edge> edges = new HashSet<>();
    for (Map.Entry<Resource, List<TaskId>> entry : buckets.entrySet()) {
      List<TaskId> bucket = entry.getValue();
      if (bucket.size() > maxBucketSize) {
        LOG.log(
            Level.WARNING,
            "Resource {0} has bucket size {1} exceeding limit {2}; "
                + "this may indicate a bad ResourceGraphPort implementation.",
            entry.getKey(),
            bucket.size(),
            maxBucketSize);
      }
      for (int i = 0; i < bucket.size(); i++) {
        for (int j = i + 1; j < bucket.size(); j++) {
          edges.add(new Edge(bucket.get(i), bucket.get(j)));
        }
      }
    }

    return new ConflictGraph(vertices, edges);
  }
}
