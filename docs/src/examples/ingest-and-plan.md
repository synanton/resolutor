# Ingest and simulate

Examples assume Resolutor at `http://localhost:8080` and project `42` (sales), tag set `sales-v3`.

## User: dictionary recalc for a month

```bash
curl -sS -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: ui-recalc-42" \
  -d '{
    "topResourceClass": "tag-dict",
    "topResourceId": "sales-v3",
    "searchDsl": {
      "projectId": "42",
      "from": "2026-03-01T00:00:00Z",
      "to": "2026-03-31T23:59:59Z",
      "tagSetId": "sales-v3",
      "engine": "dictionary"
    },
    "payload": {
      "createdBy": "user:18",
      "jobKind": "recalculate-tags"
    }
  }'
```

Response: `{ "id": "<task-uuid>" }`.

## Robot: LLM recalc (tighter timeout)

```bash
curl -sS -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "topResourceClass": "tag-llm",
    "topResourceId": "sales-v3",
    "searchDsl": {
      "projectId": "42",
      "from": "2026-03-01T00:00:00Z",
      "to": "2026-03-07T23:59:59Z",
      "tagSetId": "sales-v3",
      "engine": "llm",
      "model": "tags-v2"
    },
    "payload": {
      "createdBy": "robot:nightly-tags",
      "jobKind": "recalculate-tags"
    },
    "timeoutAt": "2026-03-16T06:00:00Z"
  }'
```

## Two projects, one connection - simulate before ingest

Dry-run two overlapping jobs (fine-grained: one task per talk). Talk `1001` is on a shared connection.

```bash
curl -sS -X POST http://localhost:8080/api/v1/plans/simulate \
  -H "Content-Type: application/json" \
  -d '{
    "tasks": [
      {
        "topResourceClass": "tag-dict",
        "topResourceId": "talk-1001",
        "searchDsl": {
          "projectId": "42",
          "talkId": "1001",
          "tagSetId": "sales-v3",
          "engine": "dictionary"
        },
        "payload": { "createdBy": "user:18" }
      },
      {
        "topResourceClass": "tag-llm",
        "topResourceId": "talk-1001",
        "searchDsl": {
          "projectId": "99",
          "talkId": "1001",
          "tagSetId": "qa-v1",
          "engine": "llm"
        },
        "payload": { "createdBy": "robot:nightly-tags" }
      }
    ]
  }'
```

If the resource graph includes `talk:1001` on **both**, expect `connectedComponents: 1`, `conflictsDetected >= 1`, and two colours or a serial pair - they must not share a wave.

If a third task targets `talk:1002` on a disjoint connection, it should appear in another group and run in parallel.

## Poll status

```bash
curl -sS http://localhost:8080/api/v1/tasks/<task-uuid>
curl -sS http://localhost:8080/api/v1/plans/latest
curl -sS http://localhost:8080/api/v1/plans/<plan-uuid>/explain
```

Worker progress (e.g. 50 talks tagged):

```bash
curl -sS -X POST http://localhost:8080/api/v1/tasks/<task-uuid>/progress \
  -H "Content-Type: application/json" \
  -d '{"successDelta":50,"failedDelta":0,"totalDelta":0}'
```

## Per-talk ingest (sketch)

Your expander (not Resolutor) lists talk ids, then:

```bash
for talk in 1001 1002 1003; do
  curl -sS -X POST http://localhost:8080/api/v1/tasks \
    -H "Content-Type: application/json" \
    -d "{
      \"topResourceClass\": \"tag-dict\",
      \"topResourceId\": \"${talk}\",
      \"searchDsl\": {
        \"projectId\": \"42\",
        \"talkId\": \"${talk}\",
        \"tagSetId\": \"sales-v3\",
        \"engine\": \"dictionary\"
      },
      \"payload\": { \"createdBy\": \"robot:expander\", \"jobId\": \"job-88\" }
    }"
done
```

FIFO ingest order is the serial order inside a clique. Ingest dependency tags first if you use per-tag tasks.
