# Resource graph for speech analysis

Implement `POST /resources` (or your `resolutor.resource-graph.endpoint`) in the speech-analysis service.

## Algorithm (project-scoped job)

Parse `searchDsl` (JSON text) for `projectId`, `from`, `to`, `tagSetId`, optional `talkId`, `engine`.

1. Load project `P`. Add `{project, P}`.
2. Add `{tag-set, tagSetId}` and each `{tag, tagId}` in the set (include **dependency** tags).
3. Load connections linked to `P`. Add each `{connection, C}`.
4. If `talkId` is set, add that talk (if it belongs to one of those connections and has a transcription in range).
5. Else list talks on those connections with transcription and `talkedAt ∈ [from, to]`. Add each `{talk, T}` and optionally `{transcription, tr}`.
6. Add `{organization, org}` / `{space, space}` only if you **want** org- or space-wide serialisation (usually omit).

## Shared connection

Projects 42 and 99 both link `conn-9`. Talk `1001` is on `conn-9`.

Job A (project 42) returns `project:42`, `connection:conn-9`, `talk:1001`, `tag-set:sales-v3`, …  
Job B (project 99) returns `project:99`, `connection:conn-9`, `talk:1001`, `tag-set:qa-v1`, …

Intersection: `connection:conn-9` **and** `talk:1001`. Either edge is enough to serialise. Prefer listing **talks**; listing every connection without talks over-serialises unrelated date ranges on that trunk.

## Sample response

```json
{
  "resources": [
    { "resourceClass": "project", "resourceId": "42" },
    { "resourceClass": "tag-set", "resourceId": "sales-v3" },
    { "resourceClass": "tag", "resourceId": "language" },
    { "resourceClass": "tag", "resourceId": "sentiment" },
    { "resourceClass": "connection", "resourceId": "conn-9" },
    { "resourceClass": "talk", "resourceId": "1001" },
    { "resourceClass": "talk", "resourceId": "1002" },
    { "resourceClass": "transcription", "resourceId": "1001" },
    { "resourceClass": "transcription", "resourceId": "1002" }
  ]
}
```

`sentiment` depends on `language`; both appear so any other job touching those tag definitions on the same talks conflicts correctly. Dependency **order** is still the worker’s job (or FIFO ingest).

## Tests

Use `FixedResourceGraphPort` in application tests: register each task with the set you would return in production. Assert two project jobs that share `talk:1001` land in one group; a talk on another connection is a second group.

Compose profile **will not** call this HTTP API - do not expect production-grade isolation under `compose`.
