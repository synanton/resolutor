# Embedding Resolutor

Resolutor is a library-shaped Spring Boot app. Your speech-analysis (or other) service either **runs `resolutor-app`** and supplies beans, or depends on the modules and writes its own composition root.

Minimum you must provide:

1. `ResourceGraphPort` - what the task touches
2. `TaskWorker` - what to do
3. PostgreSQL (Flyway) if you use the JPA adapters

## 1. Gradle

If you consume the published jars (same coordinates as this repo):

```kotlin
dependencies {
  implementation("org.synanton.resolutor:resolutor-app:<version>")
  // or pick modules:
  // implementation("org.synanton.resolutor:resolutor-application:<version>")
}
```

In this repository, implement the beans in `resolutor-app` or a sibling module that `resolutor-app` depends on.

## 2. Resource graph

Deterministic for a planning window. On failure **throw** (planner skips the task). Never return a partial set.

```java
package com.example.speech.resolutor;

import java.util.LinkedHashSet;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.synanton.resolutor.application.port.out.ResourceGraphPort;
import org.synanton.resolutor.domain.resource.Resource;
import org.synanton.resolutor.domain.task.Task;

public final class SpeechResourceGraph implements ResourceGraphPort {

  private final TalkCatalog talks;
  private final ObjectMapper json;

  public SpeechResourceGraph(TalkCatalog talks, ObjectMapper json) {
    this.talks = talks;
    this.json = json;
  }

  @Override
  public Set<Resource> resolve(Task task) {
    JsonNode dsl;
    try {
      dsl = json.readTree(task.searchDsl());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("searchDsl is not JSON", e);
    }
    String projectId = dsl.path("projectId").asText();
    Set<Resource> out = new LinkedHashSet<>();
    out.add(Resource.of("project", projectId));
    out.add(task.topResource());

    String talkId = dsl.path("talkId").asText(null);
    if (talkId != null && !talkId.isBlank()) {
      Talk talk = talks.require(talkId);
      out.add(Resource.of("connection", talk.connectionId()));
      out.add(Resource.of("talk", talk.id()));
      out.add(Resource.of("transcription", talk.transcriptionId()));
      return Set.copyOf(out);
    }

    talks
        .inProjectRange(projectId, dsl.path("from").asText(), dsl.path("to").asText())
        .forEach(
            talk -> {
              out.add(Resource.of("connection", talk.connectionId()));
              out.add(Resource.of("talk", talk.id()));
            });
    String tagSet = dsl.path("tagSetId").asText(null);
    if (tagSet != null) {
      out.add(Resource.of("tag-set", tagSet));
    }
    return Set.copyOf(out);
  }
}
```

Wire instead of (or in addition to) `ResourceGraphHttpAdapter` with `@Primary` / `@ConditionalOnProperty`.

HTTP alternative: keep the shipped adapter and implement the JSON contract in [Resource graph HTTP](../api/resource-graph.md).

## 3. Task worker

```java
package com.example.speech.resolutor;

import org.synanton.resolutor.application.port.out.TaskWorker;
import org.synanton.resolutor.domain.task.Cursor;
import org.synanton.resolutor.domain.task.Task;

public final class RecalculateTagsWorker implements TaskWorker {

  private final TagEngine engine;

  public RecalculateTagsWorker(TagEngine engine) {
    this.engine = engine;
  }

  @Override
  public Result execute(Task task) {
    RecalcJob job = RecalcJob.parse(task.searchDsl(), task.payload());
    String afterTalkId = task.cursor() == null ? null : job.afterTalkId(task.cursor().value());

    Page<Talk> page = engine.nextTalks(job, afterTalkId, job.pageSize());
    if (page.isEmpty()) {
      return Result.completed();
    }
    for (Talk talk : page.items()) {
      engine.applyTagSet(talk, job); // idempotent upsert
    }
    if (page.hasMore()) {
      return Result.paused(new Cursor("{\"afterTalkId\":\"" + page.lastId() + "\"}"));
    }
    return Result.completed();
  }
}
```

Spring:

```java
@Configuration
public class SpeechResolutorConfig {

  @Bean
  @Primary
  TaskWorker speechTaskWorker(TagEngine engine) {
    return new RecalculateTagsWorker(engine);
  }

  @Bean
  @Primary
  ResourceGraphPort speechResourceGraph(TalkCatalog talks, ObjectMapper json) {
    return new SpeechResourceGraph(talks, json);
  }
}
```

`@ConditionalOnMissingBean` on the stock no-op worker means a single `TaskWorker` bean replaces it. Use `@Primary` if both would otherwise exist.

## 4. Ingest from your API

Do not invent a second scheduler. Your “Recalculate tags” button calls Resolutor:

```java
import java.util.UUID;
import org.synanton.resolutor.application.port.in.NewTaskCommand;
import org.synanton.resolutor.application.port.in.TaskIngestionPort;
import org.synanton.resolutor.domain.resource.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/recalc")
class RecalcController {

  private final TaskIngestionPort ingestion;

  RecalcController(TaskIngestionPort ingestion) {
    this.ingestion = ingestion;
  }

  @PostMapping
  UUID start(@RequestBody RecalcRequest req) {
    String dsl =
        """
        {"projectId":"%s","from":"%s","to":"%s","tagSetId":"%s","engine":"%s"}
        """
            .formatted(req.projectId(), req.from(), req.to(), req.tagSetId(), req.engine());
    String payload =
        """
        {"createdBy":"%s","jobKind":"recalculate-tags"}
        """.formatted(req.actor());
    String topClass = "llm".equals(req.engine()) ? "tag-llm" : "tag-dict";
    return ingestion
        .ingest(new NewTaskCommand(Resource.of(topClass, req.tagSetId()), dsl, payload, req.timeoutAt()))
        .value();
  }
}
```

Or `POST /api/v1/tasks` from another service (see [Ingest and simulate](../examples/ingest-and-plan.md)).

## 5. Custom planner pass (optional)

```java
public final class DenyOrgWidePass implements ExecutionPlanPass {

  @Override
  public String name() {
    return "deny-org-wide";
  }

  @Override
  public PassState apply(PassState state) {
    return state; // inspect state.graph() / components; never add edges
  }
}
```

Register by constructing `ExecutionPlanner` with `List.of(ConnectedComponentsPass.INSTANCE, GraphColoringPass.INSTANCE, new DenyOrgWidePass())`.

## 6. Simulate without side effects

```java
ExecutionPlan preview =
    planQuery.simulate(
        List.of(
            new NewTaskCommand(
                Resource.of("tag-dict", "talk-1001"),
                """{"projectId":"42","talkId":"1001","tagSetId":"sales-v3"}""",
                "{}",
                null),
            new NewTaskCommand(
                Resource.of("tag-llm", "talk-1001"),
                """{"projectId":"99","talkId":"1001","tagSetId":"qa-v1"}""",
                "{}",
                null)));

boolean sameComponent =
    preview.metrics().connectedComponents() == 1
        && preview.metrics().conflictsDetected() >= 1;
```

Full offline JUnit example: [Compile a batch in a test](../examples/compile-offline.md).
