# Tasks

## `POST /api/v1/tasks`

Create a `PENDING` task. **201** `{ "id": "<uuid>" }`.

```json
{
  "topResourceClass": "project",
  "topResourceId": "42",
  "searchDsl": {
    "from": "2026-03-01T00:00:00Z",
    "to": "2026-03-31T23:59:59Z",
    "tagSetId": "sales-v3"
  },
  "payload": {
    "engine": "dictionary",
    "createdBy": "user:18"
  },
  "timeoutAt": "2026-04-01T00:00:00Z"
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `topResourceClass` | yes | Non-blank; backpressure key |
| `topResourceId` | yes | Non-blank |
| `searchDsl` | no | JSON object; stored as text; default `{}` |
| `payload` | no | JSON object; worker-only; default `{}` |
| `timeoutAt` | no | ISO-8601 instant; dispatch timeout if elapsed |

Resolutor does not validate `searchDsl` / `payload` schema. Your resource graph and worker do.

## `GET /api/v1/tasks/{id}`

**200**

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "state": "PAUSED",
  "progress": {
    "totalCount": 1200,
    "successCount": 400,
    "failedCount": 2,
    "pendingCount": 798
  }
}
```

`progress` is `null` until the first progress update. Unknown id → **404** problem.

`state` is the `TaskState` name: `PENDING`, `STARTED`, `PAUSED`, `COMPLETED`, `FAILED`, `TIMEOUT`.

## `POST /api/v1/tasks/{id}/progress`

**204**. Body - non-negative deltas, accumulated on the server:

```json
{
  "successDelta": 50,
  "failedDelta": 0,
  "totalDelta": 0
}
```

Workers (or a sidecar) call this while paging talks. `totalDelta` is typically set once when the date-range count is known.

## `POST /api/v1/tasks/{id}/complete`

**204**. Forces `COMPLETED`. Idempotent if already completed. Use for operator override, not the normal worker path (`TaskWorker` `COMPLETED` is enough).

## Path rules

`{id}` must be a UUID. Otherwise **400** malformed path variable.
