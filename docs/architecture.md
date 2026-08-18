# Architecture

Resolutor is a hexagonal planner: domain and application modules stay free of Spring, and adapters implement ports.

The canonical write-up is [`design.md`](./design.md) (conflict graph, execution plan, backpressure, leadership). Module layout and delivery phases are in [`implementation-plan.md`](./implementation-plan.md).

```
Tasks → ResourceGraphPort → Conflict Graph
                                │
                                ▼
                     ExecutionPlan (immutable; groups → colour waves)
                                │
                                ▼
                          DispatcherPort
```
