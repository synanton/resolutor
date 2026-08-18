# API reference

Base path: **`/api/v1`**. JSON request/response unless noted. Errors: RFC 7807 **`application/problem+json`**.

Interactive OpenAPI: `/swagger-ui.html` (springdoc). Correlation: send `X-Request-Id`; the same value is echoed and logged as `correlationId`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/v1/tasks` | Ingest |
| `GET` | `/api/v1/tasks/{id}` | Status |
| `POST` | `/api/v1/tasks/{id}/progress` | Progress deltas |
| `POST` | `/api/v1/tasks/{id}/complete` | Force-complete |
| `GET` | `/api/v1/plans/latest` | Latest plan (`204` if none) |
| `GET` | `/api/v1/plans/{id}` | Plan by id |
| `GET` | `/api/v1/plans?at=` | Plan at or before instant |
| `GET` | `/api/v1/plans/{id}/explain` | Density / degree |
| `POST` | `/api/v1/plans/simulate` | Dry-run compile |

Actuator (not under `/api/v1`): `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

Java types for those routes: [Java ports](java-ports.md). Domain records: [Main classes](../guide/classes.md).

Chapters: [Java ports](java-ports.md) · [Tasks](tasks.md) · [Plans](plans.md) · [Errors](errors.md) · [Resource graph](resource-graph.md) · [Workers](workers.md) · [Observability](observability.md).
