package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.graph.ConflictGraph.Edge;
import org.synanton.resolutor.domain.task.TaskId;

class GraphColoringPassTest {

  private static final TaskId T1 =
      TaskId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final TaskId T2 =
      TaskId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final TaskId T3 =
      TaskId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  @Test
  void pathOfThreeGivesTwoColoursAndIndependentEndsShare() {
    ConflictGraph graph =
        new ConflictGraph(Set.of(T1, T2, T3), Set.of(new Edge(T1, T2), new Edge(T2, T3)));

    Map<TaskId, Integer> colours = GraphColoringPass.colourComponent(graph, graph.vertices());

    assertThat(colours.get(T1)).isNotEqualTo(colours.get(T2));
    assertThat(colours.get(T3)).isNotEqualTo(colours.get(T2));
    assertThat(colours.get(T1)).isEqualTo(colours.get(T3));
    assertThat(colours.values().stream().mapToInt(Integer::intValue).max().orElseThrow() + 1)
        .isEqualTo(2);
  }

  @Test
  void triangleUsesThreeDistinctColours() {
    ConflictGraph graph =
        new ConflictGraph(
            Set.of(T1, T2, T3), Set.of(new Edge(T1, T2), new Edge(T2, T3), new Edge(T3, T1)));

    Map<TaskId, Integer> colours = GraphColoringPass.colourComponent(graph, graph.vertices());

    assertThat(Set.copyOf(colours.values())).hasSize(3);
  }

  @Test
  void isolatedVertexIsColourZero() {
    ConflictGraph graph = new ConflictGraph(Set.of(T1), Set.of());

    Map<TaskId, Integer> colours = GraphColoringPass.colourComponent(graph, graph.vertices());

    assertThat(colours).containsEntry(T1, 0);
  }

  @Test
  void longestTaskIsColouredFirstOnAClique() {
    ConflictGraph graph =
        new ConflictGraph(
            Set.of(T1, T2, T3), Set.of(new Edge(T1, T2), new Edge(T2, T3), new Edge(T3, T1)));

    Map<TaskId, Integer> colours =
        GraphColoringPass.colourComponent(
            graph, graph.vertices(), Map.of(T3, 9_000L, T1, 1L, T2, 1L));

    assertThat(colours.get(T3)).isZero();
    assertThat(Set.copyOf(colours.values())).hasSize(3);
  }
}
