package org.synanton.resolutor.domain.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.task.TaskId;

class ConflictGraphTest {

  @Test
  void edgeIsSymmetric() {
    var a = TaskId.generate();
    var b = TaskId.generate();

    var ab = new ConflictGraph.Edge(a, b);
    var ba = new ConflictGraph.Edge(b, a);

    assertThat(ab).isEqualTo(ba);
    assertThat(ab.hashCode()).isEqualTo(ba.hashCode());
  }

  @Test
  void edgeCanonicalFirstIsLexicographicallySmaller() {
    var a = TaskId.parse("00000000-0000-0000-0000-000000000001");
    var b = TaskId.parse("00000000-0000-0000-0000-000000000002");

    var edge = new ConflictGraph.Edge(b, a);

    assertThat(edge.first()).isEqualTo(a);
    assertThat(edge.second()).isEqualTo(b);
  }

  @Test
  void selfLoopIsRejected() {
    var a = TaskId.generate();
    assertThatThrownBy(() -> new ConflictGraph.Edge(a, a))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void emptyGraph() {
    var g = ConflictGraph.empty();
    assertThat(g.vertices()).isEmpty();
    assertThat(g.edges()).isEmpty();
  }

  @Test
  void verticesAndEdgesAreImmutable() {
    var a = TaskId.generate();
    var b = TaskId.generate();
    var g = new ConflictGraph(new java.util.HashSet<>(java.util.List.of(a, b)), java.util.Set.of());

    assertThatThrownBy(() -> g.vertices().add(TaskId.generate()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
