package org.synanton.resolutor.application.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.domain.graph.ConflictGraph;
import org.synanton.resolutor.domain.graph.ConflictGraph.Edge;
import org.synanton.resolutor.domain.task.TaskId;

class ConnectedComponentsTest {

  @Test
  void emptyGraphYieldsNoComponents() {
    assertThat(ConnectedComponents.of(ConflictGraph.empty())).isEmpty();
  }

  @Test
  void singleIsolatedVertex() {
    var a = id();
    var g = new ConflictGraph(Set.of(a), Set.of());

    var components = ConnectedComponents.of(g);

    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactly(a);
  }

  @Test
  void twoDisconnectedVertices() {
    var a = id();
    var b = id();
    var g = new ConflictGraph(Set.of(a, b), Set.of());

    var components = ConnectedComponents.of(g);

    assertThat(components).hasSize(2);
    assertThat(components).anyMatch(c -> c.contains(a));
    assertThat(components).anyMatch(c -> c.contains(b));
  }

  @Test
  void connectedPair() {
    var a = id();
    var b = id();
    var g = new ConflictGraph(Set.of(a, b), Set.of(new Edge(a, b)));

    var components = ConnectedComponents.of(g);

    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void triangleIsOneComponent() {
    var a = id();
    var b = id();
    var c = id();
    var g =
        new ConflictGraph(Set.of(a, b, c), Set.of(new Edge(a, b), new Edge(b, c), new Edge(a, c)));

    var components = ConnectedComponents.of(g);

    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactlyInAnyOrder(a, b, c);
  }

  @Test
  void chainConnectsAll() {
    var a = id();
    var b = id();
    var c = id();
    var d = id();
    // a─b─c─d  (chain)
    var g =
        new ConflictGraph(
            Set.of(a, b, c, d), Set.of(new Edge(a, b), new Edge(b, c), new Edge(c, d)));

    var components = ConnectedComponents.of(g);

    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactlyInAnyOrder(a, b, c, d);
  }

  @Test
  void pathCompressionIsIdempotent() {
    // Invoke find() multiple times to exercise path-compression branch.
    var a = id();
    var b = id();
    var c = id();
    var g = new ConflictGraph(Set.of(a, b, c), Set.of(new Edge(a, b), new Edge(b, c)));

    var first = ConnectedComponents.of(g);
    var second = ConnectedComponents.of(g);

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(1);
    assertThat(first.get(0)).containsExactlyInAnyOrderElementsOf(second.get(0));
  }

  private static TaskId id() {
    return TaskId.generate();
  }
}
