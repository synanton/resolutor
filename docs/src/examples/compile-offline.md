# Compile a batch in a test

You can compile speech-analysis recals **without** Spring, HTTP, or PostgreSQL. Use `ExecutionPlanner` plus `FixedResourceGraphPort`.

This mirrors production isolation: two projects sharing a talk form one component; a disjoint talk is a second group.

```java
package com.example.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.synanton.resolutor.application.backpressure.BackpressureConfig;
import org.synanton.resolutor.application.backpressure.BackpressureManager;
import org.synanton.resolutor.application.fake.FixedResourceGraphPort;
import org.synanton.resolutor.application.fake.NoOpMetricsPort;
import org.synanton.resolutor.application.planner.ExecutionPlanner;
import org.synanton.resolutor.application.planner.PlannerConfig;
import org.synanton.resolutor.domain.plan.ColourWave;
import org.synanton.resolutor.domain.plan.ExecutionPlan;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;
import org.synanton.resolutor.domain.task.TaskId;
import org.synanton.resolutor.domain.task.TaskState;

class SharedTalkConflictTest {

  @Test
  void overlappingProjectsSerialiseOnTheSharedTalk() {
    Resource talk = Resource.of("talk", "1001");
    Resource other = Resource.of("talk", "1002");
    Resource conn = Resource.of("connection", "conn-9");
    Resource sales = Resource.of("project", "42");
    Resource qa = Resource.of("project", "99");

    Task salesDict = pending("tag-dict", "sales-v3");
    Task qaLlm = pending("tag-llm", "qa-v1");
    Task otherDict = pending("tag-dict", "other");

    FixedResourceGraphPort graph = new FixedResourceGraphPort();
    graph.register(salesDict, Set.of(sales, conn, talk, Resource.of("tag-set", "sales-v3")));
    graph.register(qaLlm, Set.of(qa, conn, talk, Resource.of("tag-set", "qa-v1")));
    graph.register(otherDict, Set.of(Resource.of("project", "7"), other));

    ExecutionPlanner planner =
        new ExecutionPlanner(
            graph,
            new BackpressureManager(BackpressureConfig.disabled()),
            new NoOpMetricsPort(),
            PlannerConfig.defaults("v4"));

    ExecutionPlan plan = planner.compile(List.of(salesDict, qaLlm, otherDict));

    assertThat(plan.metrics().connectedComponents()).isEqualTo(2);
    assertThat(plan.metrics().conflictsDetected()).isGreaterThanOrEqualTo(1);

    var shared =
        plan.groups().stream()
            .filter(g -> g.orderedTasks().contains(salesDict.id()))
            .findFirst()
            .orElseThrow();
    assertThat(shared.orderedTasks()).containsExactlyInAnyOrder(salesDict.id(), qaLlm.id());

    boolean sameWave =
        shared.waves().stream()
            .anyMatch(
                (ColourWave w) ->
                    w.taskIds().contains(salesDict.id()) && w.taskIds().contains(qaLlm.id()));
    assertThat(sameWave)
        .as("writers on the same talk must not share a colour")
        .isFalse();
  }

  private static Task pending(String klass, String id) {
    return new Task(
        TaskId.generate(),
        Resource.of(klass, id),
        Set.of(),
        "{}",
        "{}",
        null,
        TaskState.PENDING,
        Instant.parse("2026-03-15T00:00:00Z"),
        null,
        0L);
  }
}
```

Put this in a module that depends on `testFixtures(project(":resolutor-application"))`.

## Dispatch the plan in-memory

```java
InMemoryTaskRepository repo = new InMemoryTaskRepository();
repo.save(salesDict);
repo.save(qaLlm);

NoOpTaskWorker worker = new NoOpTaskWorker();
try (InProcessDispatcher dispatcher =
    new InProcessDispatcher(
        repo,
        worker,
        new BackpressureManager(BackpressureConfig.disabled()),
        new NoOpMetricsPort(),
        DispatchConfig.defaults())) {
  dispatcher.runPlan(plan);
}

assertThat(repo.findById(salesDict.id()).orElseThrow().state()).isEqualTo(TaskState.COMPLETED);
assertThat(worker.executed()).extracting(Task::id).contains(salesDict.id(), qaLlm.id());
```

`NoOpTaskWorker` records invocations and returns `COMPLETED`. Swap in your `RecalculateTagsWorker` against a fake `TagEngine` to assert upsert keys `(talk, tagSet, tag)`.

## Simulate API vs unit compile

| | `PlanQueryPort.simulate` | `ExecutionPlanner.compile` in a test |
| --- | --- | --- |
| Resource graph | Real Spring bean (HTTP or yours) | `FixedResourceGraphPort` |
| Persistence | None | None |
| Task ids | Fresh UUIDs | The `Task` instances you built |
| HTTP | `POST /api/v1/plans/simulate` | JUnit |

Use simulate when the **HTTP graph** is what you want to verify; use the unit compile when you want **deterministic footprints**.
