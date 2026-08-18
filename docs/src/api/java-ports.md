# Java ports

Inbound ports are what controllers call. Outbound ports are what adapters implement. All live under `org.synanton.resolutor.application.port`.

## Inbound

### `TaskIngestionPort`

```java
TaskId ingest(NewTaskCommand cmd);
```

`NewTaskCommand(Resource topResource, String searchDsl, String payload, @Nullable Instant timeoutAt)`.

Implementation: `TaskIngestionService` - inserts `PENDING`, version `0`, increments ingest metric.

### `ProgressPort`

```java
void updateProgress(TaskId id, ProgressDelta delta);
void forceComplete(TaskId id);
TaskStatusView status(TaskId id); // throws IllegalArgumentException if missing
```

`ProgressDelta(long successDelta, long failedDelta, long totalDelta)` with helpers `success(long)` / `failed(long, long)`.

### `PlanQueryPort`

```java
Optional<ExecutionPlan> latestPlan();
Optional<ExecutionPlan> planById(PlanId id);
Optional<ExecutionPlan> planFor(Instant at);
ExecutionPlan simulate(List<NewTaskCommand> commands);
```

`simulate` builds transient `Task` rows in memory (new ids, `PENDING`) and calls `ExecutionPlanner.compile`. Nothing is saved.

`PlanExplainView.from(plan)` derives `conflictDensity` and `averageDegree` for `GET .../explain`.

## Outbound

### `ResourceGraphPort`

```java
Set<Resource> resolve(Task task);
```

### `TaskWorker`

```java
Result execute(Task task);

record Result(Outcome outcome, @Nullable Cursor cursor, @Nullable String failureReason) {
  static Result completed();
  static Result paused(Cursor cursor);
  static Result failed(String reason);
}
enum Outcome { COMPLETED, PAUSED, FAILED }
```

### `DispatcherPort`

```java
List<GroupResult> runPlan(ExecutionPlan plan);
GroupResult dispatch(SequentialGroup group);

record GroupResult(String componentId, List<TaskResult> taskResults) {}
record TaskResult(TaskId taskId, boolean success, @Nullable String failureReason) {
  static TaskResult ok(TaskId id);
  static TaskResult failed(TaskId id, String reason);
}
```

Javadoc still says “tasks inside a group run sequentially”; **v3** `InProcessDispatcher` runs **waves** in parallel. Kafka `dispatch` publishes one message per group and does not wait for workers.

### `TaskRepositoryPort`

`loadBatchForPlanning(int limit)` - `PENDING`/`PAUSED`, `createdAt` then id.  
`save(Task)` - optimistic concurrency on `version`.  
`findById`, `loadExpired`, `loadByStates`, `countByState`.

### `PlanPublisherPort`

Publish and read `ExecutionPlan` history (`latestPlan`, `findById`, `planAtOrBefore`).

### `LeadershipPort`

`Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work)`.

### `MetricsPort`

`incrementTasksIngested`, `recordPlanBuilt`, `recordBackpressureDenied`, `recordGroupDispatched`, `recordResourceGraphCall`, defaults for `recordTaskDuration`, `recordPlanExecuted`, `observe`.
