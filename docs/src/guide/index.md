# Development guide

Resolutor is a Gradle multi-project on **Java 21** and **Spring Boot 3.3**. Domain and application code have no Spring dependency. Adapters (JPA, HTTP, Kafka, Micrometer) implement ports.

Typical loop:

1. Ingest tasks (`POST /api/v1/tasks`).
2. A ShedLock leader tick loads a batch, resolves resources, compiles a plan, dispatches groups.
3. A `TaskWorker` performs the business action and returns `COMPLETED`, `PAUSED(cursor)`, or `FAILED`.

Start with [Getting started](getting-started.md), then [Architecture](architecture.md), [Main classes](classes.md), and [Embedding](embedding.md).
