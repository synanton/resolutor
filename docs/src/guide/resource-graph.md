# Resource graph

`ResourceGraphPort.resolve(Task)` returns the transitive footprint. The HTTP adapter POSTs:

```json
{
  "taskId": "<uuid>",
  "topResource": { "resourceClass": "project", "resourceId": "42" },
  "searchDsl": "{\"from\":\"2026-01-01\",\"to\":\"2026-01-31\"}"
}
```

`searchDsl` is a **JSON string** on the wire (the task stores it as text). Response:

```json
{
  "resources": [
    { "resourceClass": "project", "resourceId": "42" },
    { "resourceClass": "talk", "resourceId": "1001" }
  ]
}
```

Empty/null `resources` ⇒ top resource only. Circuit-breaker failures skip the task for this tick.

Compose profile does **not** call HTTP: footprint is the top resource only. For speech-analysis conflicts you need a real graph (or tests with `FixedResourceGraphPort`).

Wire details: [Resource graph HTTP](../api/resource-graph.md). Domain example: [Speech resource graph](../examples/resource-graph-speech.md).
