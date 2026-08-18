# Resolutor

**Compiler‑inspired execution planning instead of runtime locking.**


![Java 21](https://img.shields.io/badge/java-21-orange)
![Spring Boot 3.x](https://img.shields.io/badge/spring--boot-3.x-brightgreen)
![Gradle](https://img.shields.io/badge/Tools-Gradle-informational?style=flat&logo=gradle&logoColor=white&color=FFD804))
![License Apache 2.0](https://shields.io/badge/license-Apache%202-blue)

------

Resolutor replaces runtime locking with **compiler‑inspired execution planning**. It analyzes resource dependencies, builds a conflict graph and generates an immutable execution plan for safe, deterministic concurrent execution.

**No runtime locks. No retry storms. No deadlocks.**

**Learn more:** [Developer book](docs/README.md) · [Architecture](docs/architecture.md) · [Design](docs/design.md) · [Configuration](docs/configuration.md)

------

## What It Does (in 15 seconds))

```
Incoming Tasks                    ExecutionPlan
─────────────────            ──────────────────────────
Delete Project 7 ───┐
Update Talk 41   ───┤
Remove Project 7 ───┼─────►  Execution Group A (serial):
Archive Talk 41  ───┤         [Delete Project 7 →
Create Project 1 ───┘          Remove Project 7 →
                               Update Talk 41 →
                               Archive Talk 41]

                               Execution Group B (parallel):
                               [Create Project 1]
```



Resolutor detects which tasks share resources (Project 7) and serialises them,  while independent tasks (Book Room 3) run concurrently – **without runtime locks**.

------

## Why Not Airflow, Temporal, or Locks?

| Feature                  | Resolutor | Airflow | Temporal | Distributed Locks |
| ------------------------ | ------- | ------- | -------- | ----------------- |
| Plans resource conflicts | ✅       | ❌       | ❌        | ❌                 |
| Requires runtime locks   | ❌       | ❌       | ❌        | ✅                 |
| Resumable execution      | ✅       | ❌       | ✅        | ❌                 |
| Domain‑agnostic          | ✅       | ❌       | ❌        | ✅                 |
| Deterministic schedule   | ✅       | ❌       | ❌        | ❌                 |

------

## When to Use Resolutor

**Use Resolutor if:**

- ✅ Tasks modify **shared business resources** (projects, users, documents, etc.)
- ✅ Runtime locking (DB, Redis, ZooKeeper) hurts throughput or causes deadlocks
- ✅ Workloads are **batch‑or‑queue based** and can be planned ahead
- ✅ You need **resumability** for long‑running data processing

**Don't use Resolutor if:**

- ❌ Tasks are completely independent (a simple queue is enough)
- ❌ You need complex workflow orchestration (use Airflow/Temporal)
- ❌ Simple cron scheduling is all you need

------

## Quick Start (under 1 minute)

```
# Start PostgreSQL + Resolutor (Kafka is optional and off by default)
docker compose up -d

# Ingest a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"topResourceClass":"project","topResourceId":"7","searchDsl":{},"payload":{}}'

# See the latest ExecutionPlan (debugging)
curl http://localhost:8080/api/v1/plans/latest
```



The planner runs automatically. That's it.

------

## Key Features

- **First‑class ExecutionPlan** – immutable, replayable, and optimisable IR.
- **Conflict‑graph compilation** – builds a dependency graph of resources.
- **Externalised resource semantics** – works with *any* business domain.
- **Conflict isolation** – partitions conflicting tasks into independent execution groups.
- **Resumable cursors** – large tasks pause and resume without reprocessing.
- **Probabilistic backpressure** – per‑class throttling using Count‑Min Sketch and ring buffers (no Redis).
- **Java 21 virtual threads** – high concurrency with low overhead.

------

## Architecture (Simplified)

text

```
Tasks → ResourceGraphPort → Conflict Graph (IR)
                                       │
                                       ▼
                           Optimisation Passes
                                       │
                                       ▼
                              ExecutionPlan
                                       │
                                       ▼
                              Runtime (Dispatcher)
```



------

## Core Concepts

- **Task** – unit of work with a top‑level resource, search DSL, cursor, and state.
- **Resource** – opaque `{class, id}` (Resolutor never interprets it).
- **Conflict Graph** – `G=(V,E)` where `V` = tasks and `E` = shared resources.
- **ExecutionPlan** – immutable plan with sequential groups and metrics.

------

## Configuration (minimal)

yaml

```
resolutor:
  planner:
    batch-size: 100
    order-policy: FIFO
  backpressure:
    enabled: true
    default:
      max-inflight-messages: 1_000_000
      max-emission-rate-per-hour: 500_000
```



Full options: [Configuration Guide](docs/configuration.md).

------

## API

| Method | Endpoint                      | Description              |
| ------ | ----------------------------- | ------------------------ |
| `POST` | `/api/v1/tasks`               | Ingest a task            |
| `GET`  | `/api/v1/tasks/{id}`          | Status & progress        |
| `POST` | `/api/v1/tasks/{id}/progress` | Update counts            |
| `POST` | `/api/v1/tasks/{id}/complete` | Force‑complete           |
| `GET`  | `/api/v1/plans/latest`        | Get latest ExecutionPlan |

Full API docs: [API Reference](docs/api.md).

------

## Deployment

bash

```
docker build -t synanton/resolutor:2.0 .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod synanton/resolutor:2.0
```



Kubernetes manifests available in `deploy/k8s/`. Recommended: 1–3 replicas with ShedLock for leadership election.

------

## Roadmap

| Version | Focus                                                                     |
| ------- |---------------------------------------------------------------------------|
| **v1**  | Core planning (conflict isolation), resumable cursors, basic backpressure |
| **v2**  | ExecutionPlan as first‑class IR (history, passes, OpenTelemetry); optional Kafka dispatch |
| **v3**  | Fine‑grained parallelism inside groups (graph colouring)                  |
| **v4**  | Cost‑based optimisation, resource locality, backpressure‑aware reordering |

------

## Implementation Status

Tracks [`docs/implementation-plan.md`](docs/implementation-plan.md) §15 (v1) and §18 (v2–v4).

### v1 - Core planner

Implementation of v1 phases 0–7, v2 phases 9–12, v3 phases 13–15, and v4 phases 16–18 is **complete**. Cutting 1.0 is a manual ops step (phase 8).

| Phase | Deliverable | Status |
| ----- | ----------- | ------ |
| **0. Bootstrap** | Gradle multi‑project, buildSrc conventions, Spotless/ErrorProne/NullAway, ArchUnit, CI. | ✅ Complete |
| **1. Domain core** | Records/sealed types, `ConflictGraphBuilder`, `ConnectedComponents`, ordering policies, running‑example golden test. | ✅ Complete |
| **2. Application services** | Ports, `ExecutionPlanner`, `BackpressureManager`, `ProgressTracker`, `LeadershipManager`. | ✅ Complete |
| **3. Persistence adapter** | JPA entities, mappers, Flyway `V1__init.sql`, `TaskRepositoryPort` / `ProgressRepositoryPort` impls. | ✅ Complete |
| **4. Web adapter** | REST controllers, DTOs + Bean Validation, `problem+json` errors, OpenAPI via springdoc. | ✅ Complete |
| **5. Runtime + integration** | Ingest as `PENDING`; `InProcessDispatcher` on virtual threads under ShedLock; timeout reaper and `STARTED` recovery; `ResourceGraphHttpAdapter` skips on failure; Testcontainers end‑to‑end smoke test. | ✅ Complete |
| **6. Observability + packaging** | Micrometer → Prometheus, Grafana JSON, JSON logs, Dockerfile / compose / k8s. | ✅ Complete |
| **7. Perf + hardening** | JMH benches, k6 script, chaos recovery test, tick/batch defaults. | ✅ Complete |
| **8. Release 1.0** | Tag, sign, Maven Central + GHCR, release notes. | 📋 Manual (future) - see below |

### v2 - ExecutionPlan IR

| Phase | Deliverable | Status |
| ----- | ----------- | ------ |
| **9. Plan store + API** | Plan history, `GET /plans/{id}`, `planFor(Instant)`. | ✅ Complete |
| **10. Pass pipeline** | `ExecutionPlanPass` SPI, concurrent resource resolution. | ✅ Complete |
| **11. Plan observability** | OpenTelemetry, plan explain/simulate, richer Grafana. | ✅ Complete |
| **12. Kafka dispatch adapter** | Optional `dispatch.mode=kafka`. | ✅ Complete |

### v3 - Graph colouring

| Phase | Deliverable | Status |
| ----- | ----------- | ------ |
| **13. Colouring domain + pass** | Deterministic colouring; no adjacent tasks share a colour. | ✅ Complete |
| **14. Wave dispatcher** | Parallel independent sets inside a component. | ✅ Complete |
| **15. Colouring metrics + eval** | Chromatic / intra-component metrics; perf vs v1. | ✅ Complete |

### v4 - Cost-based optimisation

| Phase | Deliverable | Status |
| ----- | ----------- | ------ |
| **16. Cost model** | Duration estimates and critical-path metrics. | ✅ Complete |
| **17. Locality + backpressure reorder** | Locality clustering; backpressure-aware order. | ✅ Complete |
| **18. Optimisation eval** | Compare v1 / v3 / v4 on the §30 workload. | ✅ Complete |

------

## Release checklist (phase 8, manual)

Not automated in this repository. When cutting a 1.0:

1. Tag `v*` (for example `v1.0.0`) from `main` after CI is green.
2. Sign artifacts and publish Java modules to Maven Central (`maven-publish` + `signing`).
3. Build and push the OCI image to GHCR (`docker build` / `bootBuildImage`).
4. Write GitHub release notes that point at `docs/design.md` (planning model) and `docs/implementation-plan.md` (what shipped).

------

## Contributing

We welcome contributions! See [CONTRIBUTING.md](https://CONTRIBUTING.md). Key areas:

- New optimisation passes
- Additional adapters (databases, message queues)
- Performance benchmarks
- Documentation and examples

------

## License

Apache 2.0 License – see [LICENSE](LICENSE) for details.

------

*Resolutor – Plan. Parallelize. Protect.*
