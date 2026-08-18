# Main classes

Canonical package: `org.synanton.resolutor`. Types below are the ones you read or implement when embedding Resolutor. Internal helpers (`ConflictGraphBuilder`, `ExecutionPlanFactory`) stay package-private.

## Domain (`resolutor-domain`)

Immutable records and enums. No Spring, no I/O.

```
org.synanton.resolutor.domain
├── task        Task, TaskId, TaskState, Cursor
├── resource    Resource
├── graph       ConflictGraph, ConflictGraph.Edge
├── plan        ExecutionPlan, PlanId, SequentialGroup, ColourWave, PlanMetrics
└── policy      OrderingPolicy (sealed) → FifoPolicy, PriorityPolicy, DeadlinePolicy
```

| Type | Role |
| --- | --- |
| [`Task`](#task) | Unit of work: top resource, JSON `searchDsl`/`payload`, cursor, state |
| `TaskId` | UUID wrapper; `generate()`, `of(UUID)`, `parse(String)` |
| `TaskState` | `RECEIVED`, `PENDING`, `STARTED`, `PROCESSING`, `PAUSED`, `COMPLETED`, `TIMEOUT`, `FAILED`. The planner mainly moves `PENDING`/`PAUSED` → `STARTED` → terminal |
| `Cursor` | Opaque resume string; `new Cursor(json)`, `Cursor.initial()` is `""` |
| `Resource` | `{resourceClass, resourceId}`; `Resource.of("talk", "1001")` |
| `ConflictGraph` | Undirected task graph; `Edge` canonicalises vertex order |
| [`ExecutionPlan`](#executionplan) | Immutable schedule: groups, waves, metrics |
| `SequentialGroup` | One connected component; `waves` then flattened `orderedTasks` |
| `ColourWave` | Independent set; `ColourWave.serial(ids)` = one task per wave |
| `PlanMetrics` | Parallelism, colouring, `estimatedDurationMillis` |
| `OrderingPolicy` | Order inside a wave/component; built-ins are FIFO, PRIORITY, DEADLINE |

### `Task`

```java
public record Task(
    TaskId id,
    Resource topResource,
    Set<Resource> resolvedResources, // empty until ResourceGraphPort
    String searchDsl,                 // JSON text
    String payload,                   // JSON text
    @Nullable Cursor cursor,
    TaskState state,
    Instant createdAt,
    @Nullable Instant timeoutAt,
    long version) {

  Task withResolvedResources(Set<Resource> resources);
  Task withState(TaskState newState);
  Task withCursor(Cursor newCursor);
}
```

Copies are cheap; the planner never mutates a stored instance in place.

### `ExecutionPlan`

```java
public record ExecutionPlan(
    PlanId id,
    Instant generatedAt,
    String plannerVersion,
    Duration planningDuration,
    String orderPolicy,
    List<SequentialGroup> groups,     // groups run concurrently
    PlanMetrics metrics,
    Map<TaskId, String> taskToComponent) {

  int totalGroups();
}
```

Walk a plan:

```java
for (SequentialGroup group : plan.groups()) {
  for (ColourWave wave : group.waves()) {
    // wave.taskIds() may run in parallel
  }
}
```

## Application services

| Type | Package | Role |
| --- | --- | --- |
| `ExecutionPlanner` | `application.planner` | resolve → graph → passes → backpressure → plan |
| `PlannerConfig` | same | Version, batch, colouring/locality/cost/reorder flags, durations |
| `TaskDurationEstimator` | same | Class override → histogram mean → default |
| `GraphColoringPass` | same | Welsh–Powell; `INSTANCE` |
| `ConnectedComponentsPass` | same | Union-find partition; `INSTANCE` |
| `ExecutionPlanPass` | `application.planner.pass` | SPI; `PassState(graph, components, colours, durationMillis)` |
| `InProcessDispatcher` | `application.dispatch` | Virtual threads; wave join |
| `DispatchConfig` | same | `maxConcurrentGroups`, `taskTimeout` |
| `BackpressureManager` | `application.backpressure` | `admit` / `onEmitted` / `onCompleted` / `pressure` |
| `LeadershipManager` | `application.lifecycle` | ShedLock tick: reap, compile, publish, dispatch |
| `TaskTimeoutReaper` | same | `TIMEOUT` + recover orphaned `STARTED` |
| `TaskIngestionService` | `application.ingestion` | Implements `TaskIngestionPort` |
| `PlanQueryService` | same | History + `simulate` |
| `ProgressTracker` | `application.progress` | Implements `ProgressPort` |

Construct a planner in tests (no Spring):

```java
var graph = new FixedResourceGraphPort();
var backpressure = new BackpressureManager(BackpressureConfig.disabled());
var planner =
    new ExecutionPlanner(
        graph, backpressure, new NoOpMetricsPort(), PlannerConfig.defaults("v4"));

ExecutionPlan plan = planner.compile(List.of(taskA, taskB));
```

`PlannerConfig.serial("v1")` turns colouring and v4 reordering off.

Custom pass:

```java
public interface ExecutionPlanPass {
  String name();
  PassState apply(PassState state);

  record PassState(
      ConflictGraph graph,
      List<Set<TaskId>> components,
      Map<TaskId, Integer> colours,
      Map<TaskId, Long> durationMillis) {}
}
```

`apply` must return a new `PassState`. Do not add graph edges.

## Inbound ports (your HTTP/app calls these)

| Port | Methods |
| --- | --- |
| `TaskIngestionPort` | `TaskId ingest(NewTaskCommand)` |
| `ProgressPort` | `updateProgress`, `forceComplete`, `status` |
| `PlanQueryPort` | `latestPlan`, `planById`, `planFor(Instant)`, `simulate` |

`NewTaskCommand(Resource topResource, String searchDsl, String payload, @Nullable Instant timeoutAt)`.

```java
TaskId id =
    ingestion.ingest(
        new NewTaskCommand(
            Resource.of("tag-dict", "sales-v3"),
            """{"projectId":"42","from":"2026-03-01T00:00:00Z","to":"2026-03-31T23:59:59Z"}""",
            """{"createdBy":"user:18","jobKind":"recalculate-tags"}""",
            null));
```

## Outbound ports (you implement two; the rest have adapters)

**You implement**

| Port | Method |
| --- | --- |
| `ResourceGraphPort` | `Set<Resource> resolve(Task task)` - throw to skip this tick |
| `TaskWorker` | `Result execute(Task task)` - `completed()` / `paused(Cursor)` / `failed(String)` |

**Shipped adapters**

| Port | Adapter |
| --- | --- |
| `TaskRepositoryPort` | JPA |
| `ProgressRepositoryPort` | JPA |
| `PlanPublisherPort` | JPA JSONB history |
| `LeadershipPort` | ShedLock |
| `MetricsPort` | Micrometer |
| `DispatcherPort` | `InProcessDispatcher` or Kafka publisher |
| `ResourceGraphPort` | HTTP (`ResourceGraphHttpAdapter`) or compose no-op |

Default worker in `ApplicationBeansConfig` (override with `@Primary` / `@ConditionalOnMissingBean`):

```java
@Bean
@ConditionalOnMissingBean
TaskWorker taskWorker() {
  return (Task task) -> TaskWorker.Result.completed();
}
```

## Adapters (Spring)

| Class | Module |
| --- | --- |
| `TaskController`, `PlanController` | `resolutor-adapter-web` |
| `GlobalExceptionHandler` | `problem+json` |
| `CorrelationIdFilter` | `X-Request-Id` |
| `ExecutionPlanJsonMapper` | plan JSONB |
| `ResourceGraphHttpAdapter` | graph HTTP + Resilience4j |
| `KafkaPlanDispatcher`, `KafkaGroupConsumer` | optional dispatch |
| `MicrometerMetricsPort` | Prometheus |

## Test fixtures (`resolutor-application` `testFixtures`)

`FixedResourceGraphPort`, `InMemoryTaskRepository`, `InMemoryPlanPublisher`, `NoOpTaskWorker`, `NoOpMetricsPort`, `AlwaysLeaderPort`.

See [Embedding](embedding.md) and [Compile a batch in a test](../examples/compile-offline.md).
