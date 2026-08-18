# Getting started

## Prerequisites

- JDK 21
- Docker (PostgreSQL for compose / Testcontainers)
- Optional: [mdBook](https://rust-lang.github.io/mdBook/) for this book, [k6](https://k6.io/) for `perf/k6`

## Run locally

```bash
docker compose up -d
```

Compose starts PostgreSQL and Resolutor. Kafka is off unless you enable the compose Kafka profile. Default HTTP port is `8080`.

Ingest a task:

```bash
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"topResourceClass":"project","topResourceId":"7","searchDsl":{},"payload":{}}'
```

Inspect the latest plan:

```bash
curl http://localhost:8080/api/v1/plans/latest
```

The default `TaskWorker` completes immediately so a fresh install can boot. Replace that bean - [Embedding](embedding.md). Speech-analysis `curl` examples: [Ingest and simulate](../examples/ingest-and-plan.md).

Dry-run compile without persisting:

```bash
curl -sS -X POST http://localhost:8080/api/v1/plans/simulate \
  -H "Content-Type: application/json" \
  -d '{"tasks":[{"topResourceClass":"tag-dict","topResourceId":"sales-v3","searchDsl":{"projectId":"42"},"payload":{}}]}'
```

## Build and test

```bash
./gradlew check
```

Runs Spotless, Error Prone, NullAway, unit tests, ArchUnit, and adapter tests. Testcontainers suites skip when Docker is unavailable.

Module tests:

```bash
./gradlew :resolutor-application:test
./gradlew :resolutor-app:test
```

JMH (planner microbenchmarks):

```bash
./gradlew :resolutor-application:jmh
```

## Package

```bash
./gradlew :resolutor-app:bootJar
docker build -t resolutor:local .
```

Kubernetes manifests are under `deploy/k8s/`. See [Configuration](configuration.md) for `resolutor.*` keys.
