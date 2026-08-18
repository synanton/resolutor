# Resource graph HTTP

Implemented by `ResourceGraphHttpAdapter`. Circuit breaker instance: `resource-graph`.

## Request

`POST {resolutor.resource-graph.endpoint}`  
`Content-Type: application/json`

```json
{
  "taskId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "topResource": {
    "resourceClass": "project",
    "resourceId": "42"
  },
  "searchDsl": "{\"from\":\"2026-03-01T00:00:00Z\",\"to\":\"2026-03-31T23:59:59Z\",\"tagSetId\":\"sales-v3\"}"
}
```

| Field | Type | Notes |
| --- | --- | --- |
| `taskId` | string UUID | Planner task |
| `topResource.resourceClass` | string | From ingest |
| `topResource.resourceId` | string | From ingest |
| `searchDsl` | **string** | JSON text as stored on the task |

## Response

```json
{
  "resources": [
    { "resourceClass": "project", "resourceId": "42" },
    { "resourceClass": "connection", "resourceId": "conn-9" },
    { "resourceClass": "talk", "resourceId": "talk-1001" },
    { "resourceClass": "tag-set", "resourceId": "sales-v3" }
  ]
}
```

Always include the top resource if it participates in conflicts. Duplicates are harmless (set semantics).

Null or empty `resources` ⇒ Resolutor uses only the top resource.

## Failure

Timeout, 5xx, or open circuit: the planner **skips** the task this tick (WARN log). Do not return a truncated footprint to “be helpful” - that under-detects conflicts.
