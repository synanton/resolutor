package org.synanton.resolutor.application.planner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

/**
 * Graph-build and backpressure-admit throughput. Budgets live in {@code perf/README.md}.
 *
 * <p>Run: {@code ./gradlew :resolutor-application:jmh}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class PlannerJmhBenchmark {

  @Param({"100", "500"})
  public int taskCount;

  private List<Task> tasks = List.of();
  private BackpressureManager backpressure = new BackpressureManager(BackpressureConfig.defaults());

  @Setup
  public void setup() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    List<Task> built = new ArrayList<>(taskCount);
    for (int i = 0; i < taskCount; i++) {
      Resource top = Resource.of("project", Integer.toString(i % 50));
      built.add(
          new Task(
              TaskId.of(new UUID(0, i + 1L)),
              top,
              Set.of(top, Resource.of("talk", Integer.toString(i))),
              "{}",
              "{}",
              null,
              TaskState.PENDING,
              now.plusSeconds(i),
              null,
              0L));
    }
    tasks = List.copyOf(built);
    backpressure = new BackpressureManager(BackpressureConfig.defaults());
  }

  @Benchmark
  public int compileGraphAndComponents() {
    var graph = ConflictGraphBuilder.build(tasks, 10_000);
    return ConnectedComponents.of(graph).size();
  }

  /** Colouring does not shrink cliques; it only adds waves inside non-clique components. */
  @Benchmark
  public int colourComponents() {
    var graph = ConflictGraphBuilder.build(tasks, 10_000);
    var components = ConnectedComponents.of(graph);
    int colours = 0;
    for (var component : components) {
      colours += new HashSet<>(GraphColoringPass.colourComponent(graph, component).values()).size();
    }
    return colours;
  }

  @Benchmark
  public long admitThroughput() {
    long admitted = 0;
    for (Task task : tasks) {
      if (backpressure.admit(task.topResource().resourceClass())) {
        admitted++;
      }
    }
    return admitted;
  }
}
