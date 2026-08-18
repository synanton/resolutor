package org.synanton.resolutor.application.planner;

import org.synanton.resolutor.application.planner.pass.ExecutionPlanPass;
import org.synanton.resolutor.domain.graph.ConflictGraph;

/** v1/v2 default pass: partition the conflict graph with union-find. */
public final class ConnectedComponentsPass implements ExecutionPlanPass {

  public static final ConnectedComponentsPass INSTANCE = new ConnectedComponentsPass();

  @Override
  public String name() {
    return "connected-components";
  }

  @Override
  public PassState apply(PassState state) {
    ConflictGraph graph = state.graph();
    return new PassState(
        graph, ConnectedComponents.of(graph), state.colours(), state.durationMillis());
  }
}
