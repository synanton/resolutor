# Plans

Plans are immutable snapshots. History is append-only (Flyway `execution_plan` / latest pointer).

## Shared body

```json
{
  "id": "0c5b3c2a-1111-4b2a-9c00-aaaaaaaaaaaa",
  "generatedAt": "2026-03-15T10:00:00Z",
  "plannerVersion": "v4",
  "planningDuration": "PT0.042S",
  "orderPolicy": "FIFO",
  "groups": [
    {
      "componentId": "component-0",
      "orderedTasks": ["…", "…"],
      "waves": [
        { "colour": 0, "taskIds": ["…"] },
        { "colour": 1, "taskIds": ["…", "…"] }
      ]
    }
  ],
  "metrics": {
    "totalTasks": 2,
    "connectedComponents": 1,
    "largestComponent": 2,
    "parallelismFactor": 0.5,
    "serializationRatio": 1.0,
    "conflictsDetected": 1,
    "chromaticNumber": 2,
    "waveCount": 2,
    "intraComponentParallelism": 0.5,
    "estimatedDurationMillis": 8000
  },
  "taskToComponent": {
    "7c9e6679-7425-40de-944b-e07fc1f90ae7": "component-0"
  }
}
```

| Metric | Meaning |
| --- | --- |
| `parallelismFactor` | `components / max(1, largestComponent)` |
| `serializationRatio` | `largestComponent / totalTasks` |
| `chromaticNumber` | max colour + 1 |
| `waveCount` | sum of waves across groups |
| `intraComponentParallelism` | `maxWaveSize / largestComponent` |
| `estimatedDurationMillis` | critical path (v4) |

`planningDuration` is ISO-8601 duration. Task ids in `orderedTasks` / `waves` / `taskToComponent` keys are UUIDs.

## `GET /api/v1/plans/latest`

**200** body above, or **204** if nothing published yet.

## `GET /api/v1/plans/{id}`

**200** or **404**.

## `GET /api/v1/plans?at={instant}`

Latest plan with `generatedAt <= at`. **204** if none. Instant is ISO-8601 (`2026-03-15T10:00:00Z`).

## `GET /api/v1/plans/{id}/explain`

```json
{
  "id": "0c5b3c2a-1111-4b2a-9c00-aaaaaaaaaaaa",
  "metrics": { },
  "conflictDensity": 1.0,
  "averageDegree": 1.0,
  "taskToComponent": { }
}
```

`conflictDensity` = `2E / (V(V-1))` for `V > 1`, else `0`. `averageDegree` = `2E / V` for `V > 0`.

## `POST /api/v1/plans/simulate`

Dry-run compile. **Does not** persist tasks or dispatch. Body: `{ "tasks": [ <ingest-shaped objects> ] }` (`tasks` non-empty).

Response is a normal plan body (`id` is generated for the simulation only and is not stored unless you ingest for real).

Use simulate to see whether two project recals share talks (one group vs two) before firing production jobs.
