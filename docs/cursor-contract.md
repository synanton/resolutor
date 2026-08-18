# Cursor contract (workers)

Resolutor does not interpret business payloads. A **worker** (`TaskWorker`) performs the domain action for one `Task` invocation. The dispatcher owns state transitions.

## Idempotency

Replaying the same task with the same `cursor` must produce the same observable side effects (at-least-once delivery). If the worker already finished the page described by the cursor, it should return `COMPLETED` or the next `PAUSED` cursor without duplicating writes.

## Outcomes

| Result | Dispatcher state | Meaning |
| --- | --- | --- |
| `COMPLETED` | `COMPLETED` | No more work. Cursor may be cleared. |
| `PAUSED(cursor)` | `PAUSED` | Opaque cursor stored; planner will re-admit the task. |
| `FAILED(reason)` | `FAILED` | Terminal unless an operator retries via a new ingest. |

Workers must not persist Resolutor `TaskState`. They only return a `Result`.

## Cursor

- Opaque string. Resolutor never parses it.
- Empty/absent cursor on a recovered `STARTED` task is treated as restart from the beginning (`PENDING`).
- Present cursor after a crash becomes `PAUSED` so the next plan resumes.

## Timeouts

The dispatcher may mark `TIMEOUT` if `timeoutAt` elapsed before `execute` or if the worker overruns `resolutor.dispatch.task-timeout`. Workers should check interruption and return `PAUSED` with the last safe cursor when possible.

## Kafka dispatch

When `resolutor.dispatch.mode=kafka`, groups are published to `resolutor.kafka.groups-topic` (key = component id). Consumers must execute tasks **in group order**. Independent groups may run concurrently. The same idempotency rules apply.
