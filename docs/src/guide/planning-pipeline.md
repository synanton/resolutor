# Planning pipeline

`ExecutionPlanner.compile(List<Task>)`:

1. **Resolve** every task on a virtual thread (`ResourceGraphPort`), skip failures (stay `PENDING`).
2. **ConflictGraphBuilder** with `maxBucketSize`.
3. **Passes** (default):
   - `ConnectedComponentsPass`
   - `GraphColoringPass` if `colouring` is on (optional longest-first using duration estimates)
4. **Backpressure filter** per component (all-or-nothing, or per-task if `backpressure-reorder`).
5. **Factory** builds `SequentialGroup`s, waves, locality order, critical-path estimate.

Custom passes: inject `List<ExecutionPlanPass>` into `ExecutionPlanner`. Passes must be deterministic and must not add edges.

```java
new ExecutionPlanner(
    resourceGraph,
    backpressure,
    metrics,
    config,
    List.of(
        ConnectedComponentsPass.INSTANCE,
        GraphColoringPass.INSTANCE,
        new MyPass()),
    TaskDurationEstimator.of(config));
```

The 4-argument constructor uses `defaultPasses(config)` (`ConnectedComponentsPass` plus `GraphColoringPass` when colouring is on) and `TaskDurationEstimator.of(config)`.

## Dispatch

`InProcessDispatcher`:

- One virtual thread per group.
- For each wave: run member tasks concurrently (virtual threads), then join, then the next wave.
- Failures do not skip later tasks in the same group.
- Kafka mode: one message per group (key = component id), payload includes `waves`.

## Tick

`PlannerTick` + `LeadershipManager.runPlanningCycle`:

1. Acquire lock (`lock-at-most` covers compile and in-process dispatch).
2. Reconstruct inflight counts from DB once.
3. Reap timeouts / orphaned `STARTED`.
4. Load `PENDING`/`PAUSED` batch (`batch-size`).
5. Compile, publish, record metrics, `runPlan`.

## Simulate

`POST /api/v1/plans/simulate` compiles without persist or dispatch. Use it to check colouring and conflicts for a speech-analysis batch before going live.
