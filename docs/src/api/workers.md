# Worker contract

Java type: `org.synanton.resolutor.application.port.out.TaskWorker`.

```java
public interface TaskWorker {
  Result execute(Task task);

  enum Outcome { COMPLETED, PAUSED, FAILED }

  record Result(Outcome outcome, @Nullable Cursor cursor, @Nullable String failureReason) {
    static Result completed() { … }
    static Result paused(Cursor cursor) { … }
    static Result failed(String reason) { … }
  }
}
```

## Rules

1. Do not write Resolutor `TaskState`.
2. Same `(taskId, cursor)` ⇒ same business effect (idempotent).
3. Cursor is an opaque string: `new Cursor("{\"talkId\":\"1001\"}")`. `Cursor.initial()` is the empty string. There is no `Cursor.of`.
4. Empty cursor after crash = restart from the beginning.
5. On timeout/interrupt, return `PAUSED` with the last committed cursor when possible.

## Kafka group payload

When `dispatch.mode=kafka`, each record is:

```json
{
  "componentId": "component-0",
  "orderedTasks": ["<uuid>", "<uuid>"],
  "waves": [
    { "colour": 0, "taskIds": ["<uuid>"] },
    { "colour": 1, "taskIds": ["<uuid>"] }
  ]
}
```

Missing `waves` ⇒ treat `orderedTasks` as fully serial. Key = `componentId`. Topic default `resolutor.plan.groups`.

After deserialize, execute with `InProcessDispatcher.dispatch(SequentialGroup)` in-process, or replicate wave semantics in an external consumer.
