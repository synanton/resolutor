# Dictionary and LLM workers

Both engines are **low CPU** on the Resolutor host. Dictionary is **fast** (in-process lookups). LLM is **slow** (HTTP to a model). Encode that in duration estimates and backpressure, not in extra locks.

## `application.yml` (host app)

```yaml
resolutor:
  planner:
    planner-version: v4
    colouring: true
    locality: true
    cost: true
    task-durations:
      tag-dict: PT50MS
      tag-llm: PT8S
  backpressure:
    enabled: true
    classes:
      tag-llm:
        max-inflight-messages: 16
        max-emission-rate-per-hour: 2000
      tag-dict:
        max-inflight-messages: 5000
        max-emission-rate-per-hour: 200000
```

## Worker sketch (per-talk task)

```java
import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.task.Task;

public final class SpeechTagWorker implements TaskWorker {

  private final Talks talks;
  private final Tags tags;
  private final Dictionaries dictionaries;
  private final LlmClient llm;

  public SpeechTagWorker(Talks talks, Tags tags, Dictionaries dictionaries, LlmClient llm) {
    this.talks = talks;
    this.tags = tags;
    this.dictionaries = dictionaries;
    this.llm = llm;
  }

  @Override
  public Result execute(Task task) {
    Job job = Job.parse(task.searchDsl(), task.payload());
    var tx = talks.requireTranscription(job.talkId());
    for (TagDef tag : job.tagSet().topologicalOrder()) {
      String value =
          job.engine() == Engine.DICTIONARY
              ? dictionaries.apply(tx, tag)
              : llm.complete(tx, tag, job.model());
      tags.upsert(job.talkId(), job.tagSetId(), tag.id(), value);
    }
    return Result.completed();
  }
}
```

Idempotent `upsert` keyed by `(talk_id, tag_set_id, tag_id)` so a replay or a second project’s set does not clobber the other.

## Worker sketch (coarse job + cursor)

Cursor example: `{"talkId":"1001"}` = last **completed** talk. Build it with `new Cursor("{\"talkId\":\"" + id + "\"}")` (`Cursor.of` does not exist).

```java
Page page = talks.nextPage(job.project(), job.range(), cursorTalkId, pageSize: 20);
if (page.isEmpty()) {
  return Result.completed();
}
for (Talk talk : page.items()) {
  applyTags(talk, job); // dictionary or LLM
}
progressPort.updateProgress(task.id(), new ProgressDelta(page.size(), 0, 0));
if (page.hasMore()) {
      return Result.paused(new Cursor("{\"talkId\":\"" + page.lastId() + "\"}"));
}
return Result.completed();
```

Need `ProgressPort` injected if you report counts (`progress.updateProgress(task.id(), new ProgressDelta(page.size(), 0, 0))`). The snippet below omits it for brevity; see [Java ports](../api/java-ports.md).

## Dependency + two engines

If dictionary tags feed an LLM tag:

1. Ingest dictionary tasks first (or one talk-task that runs dict then LLM in-process).
2. If split: both include `talk:{id}`; FIFO ensures dict completes before LLM when they are in the same component.
3. Do not mark the LLM tag `COMPLETED` in your product until its dependencies exist - that is your DB invariant, not Resolutor’s.

## Locality

v4 locality groups a wave by resource class/id. Per-talk tasks with top class `tag-dict` cluster by talk id string after class - useful when the worker keeps a per-talk cache. LLM tasks in another class form other waves/groups.

## What not to lock

Do not take a Redis lock on `talk:{id}` “just in case”. If the graph lists that talk, Resolutor already serialises writers. Extra locks reintroduce the contention the planner removes.
