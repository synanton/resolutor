# Configuration

Prefix `resolutor.*`. Java defaults on `ResolutorProperties` apply when YAML omits a key.

## Planner

| Key | Default | Meaning |
| --- | --- | --- |
| `tick-interval` | `PT1S` | Compile + dispatch tick |
| `batch-size` | `100` (`200` in `prod`) | Max tasks per tick |
| `order-policy` | `FIFO` | `FIFO`, `PRIORITY`, `DEADLINE` |
| `max-bucket-size` | `10000` | Conflict-bucket warn cap |
| `planner-version` | `v4` in `application.yml` (`v1` if unset) | Label on each plan |
| `colouring` | `true` | Intra-group waves |
| `locality` | `true` | Cluster wave tasks by class/id |
| `cost` | `true` | Longest-first colouring + critical path |
| `backpressure-reorder` | `true` | Partial component admit |
| `default-task-duration` | `PT0.1S` | Estimate fallback |
| `task-durations` | `{}` | Map resource class → duration |

Speech-analysis hint: set `task-durations.tag-dict: PT50MS` and `task-durations.tag-llm: PT8S` (or whatever your p50 is).

## Dispatch and Kafka

| Key | Default | Meaning |
| --- | --- | --- |
| `dispatch.mode` | `in-process` | `kafka` publishes groups |
| `dispatch.task-timeout` | `PT5M` | Per-task wall clock |
| `dispatch.lock-at-most` | `PT10M` | ShedLock hold |
| `kafka.groups-topic` | `resolutor.plan.groups` | Key = component id |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka mode only |

## Backpressure and graph

| Key | Default | Meaning |
| --- | --- | --- |
| `backpressure.enabled` | `true` | Per-class inflight + rate |
| `backpressure.defaults.max-inflight-messages` | `1000000` | Default inflight |
| `backpressure.defaults.max-emission-rate-per-hour` | `500000` | Default rate |
| `backpressure.classes.<name>.*` | - | Per-class overrides |
| `resource-graph.endpoint` | `http://localhost:9000/resources` | Resolve URL |
| `resource-graph.timeout` | `PT2S` | HTTP timeout |

Cap LLM recals with a class override, for example `backpressure.classes.tag-llm.max-inflight-messages: 20`.

## Profiles

- `compose` - Docker Compose; resource graph = top resource only
- `prod` - JSON logs, batch size 200

Datasource: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
