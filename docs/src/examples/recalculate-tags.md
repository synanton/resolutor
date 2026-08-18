# Tag recalculation

A **recalculation task** recomputes a tag set on talks whose `talkedAt` (or equivalent) falls in `[from, to]`, limited to connections linked to a **project**.

## Coarse vs fine ingest

### A. One Resolutor task per project job (simple)

Ingest a single task. The worker pages talks with a cursor (`{"afterTalkId":"…"}`). Progress deltas count talks.

**Conflicts:** the resource graph must list **every talk** (and the project, tag set, connections) in range. Two jobs that share any talk share an edge and will not run in the same wave.

**Pros:** few tasks, easy progress. **Cons:** a large range is one long task; colouring cannot overlap talks inside that job (the worker can still process talks sequentially internally).

### B. One Resolutor task per talk (maximum parallelism)

Your app expands the date range, then ingests one task per talk (or pages expansion itself). Each graph is `{project, connection, talk, tag-set, tags…}`.

Talks that do not share resources run in parallel. Two projects sharing a connection still serialise **per shared talk**, not per project.

**Pros:** colouring and locality pay off. **Cons:** ingest volume; use `batch-size` and cursors on the expander if needed.

### C. Hybrid (recommended)

1. User/robot creates a **job** in your DB (`recalc_jobs`).
2. An expander worker (or the first Resolutor task) inserts per-talk (or per-talk-page) Resolutor tasks.
3. Dictionary talks and LLM talks use different **top** classes for backpressure.

## Tag dependencies

If `sentiment` depends on `language`:

- **Same worker invocation:** apply tags in topological order on that talk. One task per talk. Simplest.
- **Separate tasks per tag:** both include `talk:{id}`. They form a connected pair (serial). Ingest **language** before **sentiment** (FIFO / `createdAt`) so the dependent tag runs second. Do not ingest them as independent if the graph omits `talk`.

Never run dictionary and LLM writers on the same talk concurrently if they update the same tag row set - both footprints must include that `talk`.

## Cross-project overlap

```
Connection C1 ── Project P-Sales
         └── Project P-QA

Talk T (on C1, 2026-03-10) ∈ both projects
```

Recalculate P-Sales tags and P-QA tags for March:

- Both graphs include `talk:T` → **one component**, two waves or serial - **no lost updates**.
- They may still run **different tag sets**; your worker should write tag-set-scoped rows (`(talk_id, tag_set_id, tag_id)`) so P-QA does not delete P-Sales tags. Isolation is for **row contention**, not product merge.

## Date range

Put the range in `searchDsl`, not in `topResourceId`. The graph service filters talks. Two ranges that do not share talks do not conflict even on the same project (if talks are listed individually). If the graph only returns `project` (compose mode), **all** recals on that project serialise - too coarse for production.

## Engine

| Engine | CPU | Wall time | Suggested `task-durations` | Backpressure |
| --- | --- | --- | --- | --- |
| Dictionary | low | low | `tag-dict: PT50MS` per talk | High inflight |
| LLM | low on the planner host | high (API/GPU) | `tag-llm: PT5S`–`PT30S` | Low inflight, modest rate/hour |

Colouring does not make an LLM talk faster; it overlaps **other talks** that do not share resources.
