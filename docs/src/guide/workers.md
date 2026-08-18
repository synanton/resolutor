# Workers and cursors

Implement `TaskWorker.execute(Task)` in the host application. Resolutor owns `TaskState`; the worker must not persist it.

```java
@Override
public Result execute(Task task) {
  Job job = Job.parse(task.searchDsl(), task.payload());
  Page page = engine.nextPage(job, task.cursor());
  if (page.isEmpty()) {
    return Result.completed();
  }
  engine.apply(page, job); // idempotent
  if (page.hasMore()) {
    return Result.paused(new Cursor("{\"afterTalkId\":\"" + page.lastId() + "\"}"));
  }
  return Result.completed();
}
```

`Cursor` has `new Cursor(String)` and `Cursor.initial()` (`""`). There is no `Cursor.of`.

## Outcomes

| Result | Dispatcher | When to use |
| --- | --- | --- |
| `COMPLETED` | `COMPLETED` | No more pages |
| `PAUSED(cursor)` | `PAUSED` | More talks/pages remain; cursor is opaque |
| `FAILED(reason)` | `FAILED` | Terminal; operator retries with a new ingest |

## Idempotency

At-least-once: the same task + cursor must not duplicate writes. If the page is already done, return `COMPLETED` or the next cursor.

## Crash recovery

- `STARTED` without cursor → `PENDING` (restart).
- `STARTED` with cursor → `PAUSED` (resume).
- `timeoutAt` elapsed or `task-timeout` overrun → `TIMEOUT`.

Prefer returning `PAUSED` with the last safe cursor on interruption.

## Kafka

Consumers must honour **wave order** inside a group (or `orderedTasks` if `waves` is absent). Independent groups may run in parallel.

Full contract: [Worker contract](../api/workers.md). Speech-analysis paging: [Dictionary and LLM workers](../examples/workers-dict-llm.md).
