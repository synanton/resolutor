# Architecture

Hexagonal layout: **domain ← application ← adapters**. Spring Boot lives in `resolutor-app` (composition root) and the adapter modules.

```
                    ┌────────────── adapters ──────────────┐
 REST / Kafka  →    │  web · kafka · jpa · http · metrics  │
                    └──────────────────┬───────────────────┘
                                       │ ports
                    ┌──────────────────▼───────────────────┐
                    │           application                 │
                    │  ingest · planner · dispatch ·        │
                    │  backpressure · leadership            │
                    └──────────────────┬───────────────────┘
                                       │
                    ┌──────────────────▼───────────────────┐
                    │               domain                  │
                    │  Task · Resource · ConflictGraph ·    │
                    │  ExecutionPlan · ColourWave           │
                    └──────────────────────────────────────┘
```

## Runtime path

```
ingest → PENDING
   → ShedLock tick
      → resolve (ResourceGraphPort)
      → ConflictGraph → components → colour waves
      → backpressure filter
      → persist ExecutionPlan
      → dispatch groups (waves join in order)
         → TaskWorker.execute
```

1. **Ingest** stores a `PENDING` task (top resource + opaque JSON `searchDsl` / `payload`).
2. **Leadership** (ShedLock) runs one compile+dispatch cycle per tick.
3. **Resolve** calls `ResourceGraphPort` concurrently (virtual threads, per-tick cache).
4. **Compile** builds the conflict graph, connected components, optional colouring, locality / cost / backpressure filters.
5. **Publish** persists the `ExecutionPlan` (history + latest).
6. **Dispatch** runs each component group on a virtual thread; colour waves inside a group may run in parallel.

## Isolation rule

Two tasks **conflict** if their resolved resource sets intersect. Conflicting tasks never run in the same colour wave. Independent connected components always run concurrently.

## What you plug in

| Port | Your job |
| --- | --- |
| `ResourceGraphPort` | Transitive `{resourceClass, resourceId}` footprint for a task |
| `TaskWorker` | Side effects; return cursor / completed / failed |
| Optional Kafka | Consume group messages and call `InProcessDispatcher.dispatch` |

Resolutor never interprets `payload`. Speech-analysis specifics belong in those two ports - see [speech domain examples](../examples/speech-domain.md).

Type catalogue: [Main classes](classes.md). Copy-paste Spring beans: [Embedding](embedding.md).
