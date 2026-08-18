# Observability

## Health

| Path | Use |
| --- | --- |
| `/actuator/health/liveness` | Process up |
| `/actuator/health/readiness` | DB / migrations |

## Prometheus

`/actuator/prometheus`. Selected meters:

| Meter | Kind | Meaning |
| --- | --- | --- |
| `resolutor.tasks.ingested` | counter | Ingest count |
| `resolutor.tasks.state` | gauge | Tagged by `state` |
| `resolutor.plan.build.duration` | timer | Compile |
| `resolutor.plan.tasks.total` | gauge | Last plan |
| `resolutor.plan.components` | gauge | Last plan |
| `resolutor.plan.parallelism` | gauge | Last plan |
| `resolutor.plan.serialization_ratio` | gauge | Last plan |
| `resolutor.plan.chromatic_number` | gauge | v3 |
| `resolutor.plan.wave_count` | gauge | v3 |
| `resolutor.plan.intra_component_parallelism` | gauge | v3 |
| `resolutor.plan.estimated_duration_millis` | gauge | v4 critical path |
| `resolutor.plan.actual_duration_millis` | gauge | Last dispatch wall time |
| `resolutor.plan.actual.duration` | timer | Dispatch wall time |
| `resolutor.task.duration` | timer | Per `resource_class` |
| `resolutor.dispatch.group.duration` | timer | Per group, `outcome` |
| `resolutor.backpressure.denied` | counter | Per `resource_class` |
| `resolutor.backpressure.inflight` | gauge | Per class |
| `resolutor.backpressure.rate` | gauge | Per class |
| `resolutor.resource_graph.calls` | counter | `outcome` = success/failure/timeout |

Observations: `resolutor.plan.compile`, `resolutor.plan.dispatch` (OpenTelemetry bridge when tracing is on).

Grafana JSON: `deploy/grafana/resolutor-overview.json`.
