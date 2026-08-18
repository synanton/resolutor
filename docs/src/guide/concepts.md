# Core concepts

## Task

Unit of work. Identity is `TaskId` (UUID). Lifecycle: `PENDING` → `STARTED` → `COMPLETED` | `FAILED` | `PAUSED` | `TIMEOUT`.

| Field | Role |
| --- | --- |
| `topResource` | Primary `{class, id}` used for ingest and backpressure class |
| `searchDsl` | Opaque JSON; forwarded to the resource graph |
| `payload` | Opaque JSON; forwarded to the worker |
| `cursor` | Opaque resume token; Resolutor never parses it |
| `timeoutAt` | Optional deadline before dispatch |

Field shapes: [Main classes](classes.md).

## Resource

Opaque pair `{resourceClass, resourceId}`. Equality is structural. Typical classes in speech analysis: `organization`, `space`, `connection`, `project`, `talk`, `tag`, `tag-set`.

## Conflict graph

Undirected graph: vertex = task, edge = shared resource. Built by inverting resources → tasks and adding a clique (capped by `max-bucket-size`) per resource bucket.

## Execution plan

Immutable IR: `PlanId`, groups, waves, metrics. Groups run concurrently. Inside a group, **waves** run in colour order; tasks in one wave may run together.

`orderedTasks` is the flattened wave order (debug / Kafka fallback). Missing `waves` on the wire means fully serial.

## Colouring (v3)

Welsh–Powell greedy colouring per component. Same colour ⇒ independent set. Adjacent tasks never share a colour. Disable with `resolutor.planner.colouring=false`.

## Cost and locality (v4)

- **Cost:** longest-first colouring when duration estimates exist; `estimatedDurationMillis` is the plan critical path (max over groups of sum of per-wave max estimates).
- **Locality:** sort within a wave by resource class/id.
- **Backpressure reorder:** drop only over-limit classes instead of the whole component.

Estimates: `task-durations` map, then observed dispatch mean per class, then `default-task-duration` (100 ms).

## Backpressure

Per **resource class** (not id): in-flight AtomicLong + hourly ring buffer. `admit` is check-only; `onEmitted` / `onCompleted` update counts at dispatch.

## Leadership

Only one planner tick compiles and dispatches at a time (ShedLock JDBC). Replicas still serve HTTP.
