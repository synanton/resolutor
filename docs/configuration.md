# Configuration

Spring Boot YAML under `resolutor.*`. Defaults also live on `ResolutorProperties`.

| Key | Default | Meaning |
| --- | --- | --- |
| `resolutor.planner.tick-interval` | `PT1S` | Planner + dispatch tick. |
| `resolutor.planner.batch-size` | `100` (`200` in `prod`) | Max tasks compiled per tick. |
| `resolutor.planner.order-policy` | `FIFO` | `FIFO`, `PRIORITY`, or `DEADLINE`. |
| `resolutor.planner.max-bucket-size` | `10000` | Warn threshold for a resource's conflict bucket. |
| `resolutor.planner.planner-version` | `v3` (`v1` if unset) | Label stored on each compiled plan. |
| `resolutor.planner.colouring` | `true` | Graph colouring inside components. `false` reproduces v1 serial groups (one task per wave). |
| `resolutor.planner.locality` | `true` | Cluster tasks in a wave by resource class/id. |
| `resolutor.planner.cost` | `true` | Longest-first colouring and critical-path estimates. |
| `resolutor.planner.backpressure-reorder` | `true` | Admit a subset of a component instead of dropping it whole. |
| `resolutor.planner.default-task-duration` | `PT0.1S` | Fallback duration when no class override or histogram exists. |
| `resolutor.planner.task-durations` | `{}` | Map of resource class → estimated duration. |
| `resolutor.dispatch.mode` | `in-process` | `in-process` or `kafka`. |
| `resolutor.dispatch.task-timeout` | `PT5M` | Per-task wall clock during dispatch. |
| `resolutor.dispatch.lock-at-most` | `PT10M` | ShedLock hold covering compile **and** dispatch (in-process). Kafka mode holds the lock through produce only. |
| `resolutor.kafka.groups-topic` | `resolutor.plan.groups` | Topic for sequential groups when mode is `kafka`. Key = component id. |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Used only when `dispatch.mode=kafka`. |
| `resolutor.backpressure.enabled` | `true` | Probabilistic per-class admit filter. |
| `resolutor.resource-graph.endpoint` | `http://localhost:9000/resources` | HTTP graph service (not used under `compose`). |
| `resolutor.resource-graph.timeout` | `PT2S` | HTTP client timeout. |

Profiles:

- `compose` - Docker Compose; resource graph is the task's top resource only.
- `prod` - JSON logs, batch size 200.

Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

Datasource: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
