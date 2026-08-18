# Speech analysis domain

This chapter maps a **speech-analysis** product onto Resolutor. The product stores recorded **talks** (calls) with **transcriptions**, attaches **tags**, and lets users or a **system robot** ask for tags to be **recalculated** over a date range for a **project** and a **tag set**.

Resolutor does not know about audio. You encode the hierarchy as resources and ingest recalculation as tasks.

## Hierarchy

```
Organization
  └── Space            (user workspace)
        └── Connection (audio source / trunk / integration)
              └── Talk (one conversation + transcription)
                    └── Tag instances (on the talk)
Project ──────────────┘  (many-to-many with connections)
  └── Tag set (dictionary and/or LLM tag definitions)
        └── Tag (may depend on another tag)
```

Facts you must preserve in the resource graph:

| Fact | Why it matters for planning |
| --- | --- |
| Recalc is **scoped to a project** | Top resource is usually `project` |
| A **connection** can belong to **several projects** | The same talk can appear in two recals |
| Several **connections** live in one **space** | Space-level jobs can fan out |
| Several **spaces** live in one **organization** | Org-level robots must not lock the whole org unless intended |
| A **tag** is stored on a **talk that has a transcription** | Mutating tags conflicts on `talk` (and maybe `transcription`) |
| Tag **B may depend on tag A** | Same talk: compute A before B (serial on `talk`, or one worker that respects the DAG) |
| **Dictionary** tags: fast, low CPU | Short duration estimate; high inflight OK |
| **LLM** tags: slow, low CPU (I/O or accelerator) | Long duration; tight inflight / rate on class `tag-llm` |

## Resource classes (recommended)

Use stable string classes. Ids are your primary keys (stringified).

| Class | Id example | Include when |
| --- | --- | --- |
| `organization` | `org-1` | Org-wide robot jobs only |
| `space` | `space-7` | Rare; usually too coarse |
| `connection` | `conn-9` | Recal that scans that connection |
| `project` | `42` | Every project-scoped recalc |
| `talk` | `talk-1001` | Every job that writes tags on that talk |
| `transcription` | `tr-1001` | If tag write also locks the transcript row |
| `tag-set` | `sales-v3` | Recal of that set |
| `tag` | `tag-sentiment` | Fine-grained per-tag tasks (optional) |
| `tag-dict` / `tag-llm` | engine name | **Top** resource for backpressure class |

Backpressure keys off **top** resource class. Prefer:

- Dictionary batch: `topResourceClass: "tag-dict"`
- LLM batch: `topResourceClass: "tag-llm"`

and still **return** `project`, `talk`, … from the graph so conflicts are correct.

## Who creates jobs

| Actor | How |
| --- | --- |
| User | UI/API in *your* app → `POST /api/v1/tasks` with `payload.createdBy: "user:{id}"` |
| System robot | Cron / queue in *your* app → same ingest with `payload.createdBy: "robot:nightly-tags"` |

Resolutor does not authenticate actors. Your API should.

## Java types you touch

| Speech-analysis idea | Resolutor type |
| --- | --- |
| Recalc job | `Task` + `NewTaskCommand` |
| Project / talk / connection | `Resource.of("project", id)` etc. |
| Date range + tag set + engine | `Task.searchDsl()` JSON |
| `createdBy` user or robot | `Task.payload()` JSON |
| Resume after page of talks | `Cursor` |
| Isolation | `ResourceGraphPort.resolve` returns overlapping `talk` ids |
| Dictionary vs LLM work | `TaskWorker` + top class `tag-dict` / `tag-llm` |
| Preview conflicts | `PlanQueryPort.simulate` or `ExecutionPlanner.compile` |

Walkthroughs: [Embedding](../guide/embedding.md) (beans), [Ingest](ingest-and-plan.md) (curl), [Offline compile](compile-offline.md) (JUnit).

