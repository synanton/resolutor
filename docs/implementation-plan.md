# Resolutor - Implementation Plan

**Status:** v1 implementation complete (release 1.0 is a manual checklist). v2–v4 are planned, not started.
**Target releases:** v1 (core planner) through v4 (cost-based optimisation), per the roadmap in `README.md`.
**Companion docs:** [`design.md`](./design.md), [`../README.md`](../README.md)

This plan turns the design in `docs/design.md` into a concrete engineering roadmap. v1 commits to a stack, module layout, data model, and phased delivery. **§18** continues that sequence for v2 (plan IR), v3 (graph colouring), and v4 (cost-based optimisation).

---

## 1. Scope for v1

Per the roadmap, v1 delivers **core planning + resumable cursors + basic backpressure**. Concretely, v1 ships:

- Task ingestion via REST (`POST /api/v1/tasks`) and status/progress/complete endpoints (§25 of design).
- Planner pipeline: resolve → conflict graph → connected components → ordering → backpressure filter → `ExecutionPlan` (§11).
- In-process dispatcher on Java 21 virtual threads; each connected component executes sequentially, components run concurrently (§8.2).
- Resumable cursors, at-least-once execution, timeout handling (§19, §23).
- Probabilistic backpressure (Count-Min Sketch + ring buffer) fully in-memory, reconstructed on startup (§21, Appendix A).
- ShedLock-backed leadership so only one planner instance is active at a time.
- Prometheus metrics + basic Grafana dashboard.
- PostgreSQL 14+ as the only required external dependency. Kafka is a **pluggable adapter**, off by default in v1.

**Explicitly out of scope for v1:** intra-component parallelism via graph coloring (v3), cost-based optimization (v4), distributed multi-planner execution (§31), full event-sourced audit trail.

---

## 2. Tech Stack

| Area | Choice | Notes |
|---|---|---|
| Language | Java 21 | Virtual threads, records, sealed types, pattern matching. `--enable-preview` **not** required. |
| Build | **Gradle 8.x (Kotlin DSL)** | Multi-project build, version catalog in `gradle/libs.versions.toml`. |
| Framework | Spring Boot 3.3.x | Web MVC (virtual-thread executor), Actuator, Validation. Reactive stack **not** used. |
| Persistence | Spring Data JPA + Hibernate 6 | JSONB via `hypersistence-utils`. Flyway for migrations. |
| DB | PostgreSQL 14+ | JSONB for `resources` and `payload`; advisory locks only for ShedLock. |
| Leadership | ShedLock (JDBC provider) | Coarse-grained lock around the planner tick. |
| HTTP client | Spring `RestClient` (JDK `HttpClient` under the hood) | Used by the default `ResourceGraphPort` adapter. |
| Messaging (optional) | Spring Kafka | Behind `resolutor.dispatch.mode=kafka`. Off in v1 default. |
| Observability | Micrometer → Prometheus | `/actuator/prometheus`. OpenTelemetry hooks left as a seam. |
| Resilience | Resilience4j | Circuit breakers around `ResourceGraphPort` and Kafka. |
| Testing | JUnit 5, AssertJ, Testcontainers (Postgres, Kafka), ArchUnit, JMH (perf) | See §12. |
| Static analysis | Spotless (google-java-format), Error Prone, NullAway, SpotBugs | Fail the build on violations. |
| CI | GitHub Actions | Matrix: JDK 21 Temurin, Postgres 14 + 16. |
| Container | Multi-stage Dockerfile (distroless JRE 21) | Optional alternative: `./gradlew bootBuildImage`. |

**Version pinning** lives in `gradle/libs.versions.toml`. Renovate/Dependabot updates it.

---

## 3. Module Layout

Gradle multi-project. Dependency direction is **strictly inward**: adapters depend on application, application depends on domain, domain depends on nothing Spring.

**Base package:** `org.synanton.resolutor` (domain, application, adapters, and the Spring Boot app).

```
resolutor/
├── settings.gradle.kts
├── build.gradle.kts                    # shared conventions plugin
├── gradle/libs.versions.toml
├── buildSrc/                           # convention plugins (java, spotless, testing)
├── docs/                               # design.md, implementation-plan.md, ...
│
├── resolutor-domain/                     # pure Java, no Spring, no I/O
│   └── src/main/java/org/synanton/resolutor/domain/
│       ├── task/                       # Task, TaskState, Cursor, TaskId
│       ├── resource/                   # Resource, ResourceClass, ResourceId
│       ├── graph/                      # ConflictGraph, Edge, ConnectedComponent
│       ├── plan/                       # ExecutionPlan, SequentialGroup, PlanMetrics
│       └── policy/                     # OrderingPolicy (sealed), Priority, Fifo, ...
│
├── resolutor-application/                # ports + services; depends on domain only
│   └── src/main/java/org/synanton/resolutor/application/
│       ├── port/in/                    # TaskIngestionPort, PlanQueryPort, ProgressPort
│       ├── port/out/                   # ResourceGraphPort, TaskRepositoryPort,
│       │                               # ProgressRepositoryPort, PlanPublisherPort,
│       │                               # LeadershipPort, MetricsPort, DispatcherPort
│       ├── planner/                    # ExecutionPlanner + optimization passes
│       ├── dispatch/                   # DispatchService (virtual-thread runtime)
│       ├── backpressure/               # BackpressureManager, CountMinSketch, RingBuffer
│       ├── progress/                   # ProgressTracker
│       └── lifecycle/                  # PlannerTick, LeadershipManager
│
├── resolutor-adapter-persistence-jpa/    # JPA entities, Spring Data repos, mappers
├── resolutor-adapter-web/                # Spring MVC controllers, DTOs, validation
├── resolutor-adapter-resource-http/      # default HTTP ResourceGraphPort impl
├── resolutor-adapter-kafka/              # optional DispatcherPort/PublisherPort impl
├── resolutor-adapter-metrics/            # Micrometer bindings
│
└── resolutor-app/                        # Spring Boot @SpringBootApplication
    ├── src/main/java/org/synanton/resolutor/App.java
    └── src/main/resources/
        ├── application.yml
        ├── application-prod.yml
        └── db/migration/               # Flyway V1__init.sql, ...
```

**ArchUnit tests** (in `resolutor-application`) enforce:
- `domain` classes reference nothing in `application` or adapters.
- `application.port.*` interfaces are the only cross-boundary contracts.
- No adapter imports another adapter.

---

## 4. Domain Model

All domain types are **records or sealed interfaces**. No JPA, no Spring, no Jackson annotations in this module - mappers live in adapters.

```java
// resolutor-domain (package org.synanton.resolutor.domain)
public record TaskId(UUID value) { }

public record Resource(String klass, String id) {
    public Resource {
        Objects.requireNonNull(klass);
        Objects.requireNonNull(id);
    }
}

public enum TaskState { RECEIVED, PENDING, STARTED, PROCESSING, PAUSED, COMPLETED, TIMEOUT, FAILED }

public record Cursor(String value) { }   // opaque, adapter-defined shape

public record Task(
    TaskId id,
    Resource topResource,
    Set<Resource> resolvedResources,     // populated by planner via ResourceGraphPort
    JsonNode searchDsl,
    JsonNode payload,
    Cursor cursor,
    TaskState state,
    Instant createdAt,
    Instant timeoutAt,
    long version
) { }

public record ConflictGraph(Set<TaskId> vertices, Set<Edge> edges) {
    public record Edge(TaskId a, TaskId b) { /* undirected: canonicalize on construction */ }
}

public record SequentialGroup(String componentId, List<TaskId> orderedTasks) { }

public record ExecutionPlan(
    Instant generatedAt,
    String plannerVersion,
    Duration planningDuration,
    String orderPolicy,
    List<SequentialGroup> groups,
    PlanMetrics metrics,
    Map<TaskId, String> taskToComponent  // optional diagnostics
) { }

public record PlanMetrics(
    int totalTasks,
    int connectedComponents,
    int largestComponent,
    double parallelismFactor,
    double serializationRatio,
    int conflictsDetected
) { }

public sealed interface OrderingPolicy permits Fifo, Priority, Deadline, Custom {
    List<TaskId> order(List<Task> tasksInComponent);
}
```

Design notes:
- `ExecutionPlan` is deep-immutable. Collections are wrapped with `List.copyOf` / `Set.copyOf` at construction.
- `Resource` equality is structural (`{class, id}` pair) - this is what makes the graph work.
- `ConflictGraph.Edge` canonicalizes vertex order (lexicographic on `TaskId.value`) so `equals`/`hashCode` are symmetric.

---

## 5. Ports (Application Boundaries)

Inbound ports (driven by adapters):

```java
public interface TaskIngestionPort {
    TaskId ingest(NewTaskCommand cmd);
}

public interface PlanQueryPort {
    Optional<ExecutionPlan> latestPlan();
    Optional<ExecutionPlan> planFor(Instant at);   // v2+, keep the seam now
}

public interface ProgressPort {
    void updateProgress(TaskId id, ProgressDelta delta);
    void forceComplete(TaskId id);
    TaskStatusView status(TaskId id);
}
```

Outbound ports (driven **by** application, implemented by adapters):

```java
public interface ResourceGraphPort {
    /** Deterministic, stable resource footprint for a task. */
    Set<Resource> resolve(Task task);
}

public interface TaskRepositoryPort {
    List<Task> loadPendingBatch(int limit);
    void saveState(TaskId id, TaskState state, long expectedVersion);
    Optional<Task> findById(TaskId id);
    // ... minimal set to keep planner testable
}

public interface ProgressRepositoryPort { /* ... */ }

public interface PlanPublisherPort {
    void publish(ExecutionPlan plan);   // stores latest for /plans/latest, emits metrics
}

public interface DispatcherPort {
    /** Execute a single group sequentially; returns per-task results. */
    GroupResult dispatch(SequentialGroup group);
}

public interface LeadershipPort {
    <T> Optional<T> runIfLeader(Duration lockAtMost, Supplier<T> work);
}

public interface MetricsPort { /* counters, gauges, timers */ }
```

Every port is stubbed with an in-memory fake in `resolutor-application/src/testFixtures` so the planner and dispatcher can be unit-tested with zero I/O.

---

## 6. Planning Pipeline (Implementation)

The planner is a **pure function** from `List<Task>` to `ExecutionPlan`, wrapped in a service that pulls tasks in, publishes the plan out.

```java
final class ExecutionPlanner {
    ExecutionPlan compile(List<Task> tasks, PlannerConfig cfg) {
        var timer = Stopwatch.start();

        // 1. Resolve resources (cached per task-id within this compile call).
        var resolved = resolveAll(tasks);

        // 2. Build conflict graph - invert resource → tasks, emit edges per bucket.
        ConflictGraph graph = ConflictGraphBuilder.build(resolved);

        // 3. Optimization pass: connected components (union-find, O(V+E)).
        List<Set<TaskId>> components = ConnectedComponents.of(graph);

        // 4. Ordering policy applied per component.
        var ordered = components.stream()
            .map(c -> cfg.orderingPolicy().order(tasksIn(c, tasks)))
            .toList();

        // 5. Backpressure filter - drop or defer tasks that exceed per-class limits.
        var filtered = backpressure.filter(ordered);

        // 6. Assemble ExecutionPlan.
        return ExecutionPlanFactory.build(filtered, graph, timer.elapsed(), cfg);
    }
}
```

Algorithm choices:
- **Graph representation:** adjacency list keyed by `TaskId`. Edges deduped via a `HashSet<Edge>` with canonical ordering.
- **Connected components:** iterative union-find with path compression + union by rank. Avoids recursion / stack overflow on wide graphs.
- **Bucket cap:** if a resource has an unusually large bucket (> `resolutor.planner.max-bucket-size`, default 10_000), log a warning and short-circuit - a runaway bucket usually indicates a bad `ResourceGraphPort` implementation.
- **Determinism:** `TaskId` iteration order is sorted before component discovery so replans on the same input produce byte-identical plans.

**Optimization passes** live in `application.planner.pass.*` as `ExecutionPlanPass` implementations. v1 ships `ConnectedComponentsPass` only. v2+ passes plug in without touching the runtime.

---

## 7. Runtime / Dispatcher

- Java 21 virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`, held in a `DispatchExecutor` bean.
- Each `SequentialGroup` is submitted as a single virtual thread. Inside the thread, tasks execute **sequentially** in the order given by the ordering policy.
- Groups run concurrently, bounded by `resolutor.dispatch.max-concurrent-groups` (default = number of components; no artificial cap).
- Structured concurrency (`StructuredTaskScope.ShutdownOnFailure` is **not** used here - group failures should not cancel siblings). Instead we use plain `Future` handles and aggregate results.
- Per-task execution:
  1. Transition `PENDING → STARTED` (optimistic lock via `version`).
  2. Invoke domain-supplied worker (see §9 - a `TaskWorker` port called by the dispatcher).
  3. Advance cursor + progress after each page.
  4. Transition to `COMPLETED` / `PAUSED` / `TIMEOUT` / `FAILED`.
- Timeout enforcement uses a scheduled reaper that flips `timeout_at < now()` tasks to `TIMEOUT`, preserving the cursor for retry.

---

## 8. Backpressure

Direct implementation of Appendix A:

```java
final class CountMinSketch {
    private final long[][] table;   // depth × width
    private final int width = 65_536;
    private final int depth = 5;
    // hashes: independent Murmur3 seeds
}

final class RingBuffer {
    private final long[] buckets = new long[60];   // one per minute
    // sliding 1-hour window; O(1) increment, O(60) sum
}

interface BackpressureManager {
    boolean admit(Resource topResource);
    void onEmitted(Resource topResource);
    void onCompleted(Resource topResource);
    void reconstructFromDb();   // called on planner leadership acquisition
}
```

- One `CountMinSketch` and one `RingBuffer` per **resource class** (not per resource - cardinality would explode).
- Reconstruction on leadership acquisition runs the SQL in Appendix A and seeds the sketch. Ring buffer starts empty (documented acceptable loss on restart).
- Limits configured per class under `resolutor.backpressure.classes.<name>.*`, falling back to `resolutor.backpressure.default.*`.

---

## 9. Data Model & Migrations

Flyway migrations in `resolutor-app/src/main/resources/db/migration/`.

`V1__init.sql`:

```sql
CREATE TABLE tasks (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload             JSONB NOT NULL,
    search_dsl          JSONB NOT NULL,
    resources           JSONB,                              -- cached resolved set
    cursor              TEXT,
    state               TEXT NOT NULL,                      -- CHECK against enum
    timeout_at          TIMESTAMPTZ,
    top_resource_class  TEXT NOT NULL,
    top_resource_id     TEXT NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_tasks_state_created     ON tasks(state, created_at);
CREATE INDEX idx_tasks_top_resource      ON tasks(top_resource_class, top_resource_id);
CREATE INDEX idx_tasks_timeout           ON tasks(timeout_at) WHERE state IN ('STARTED','PROCESSING');

CREATE TABLE task_progress (
    task_id        UUID PRIMARY KEY REFERENCES tasks(id) ON DELETE CASCADE,
    total_count    BIGINT NOT NULL DEFAULT 0,
    success_count  BIGINT NOT NULL DEFAULT 0,
    failed_count   BIGINT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0
);

-- ShedLock
CREATE TABLE shedlock (
    name        VARCHAR(64) PRIMARY KEY,
    lock_until  TIMESTAMPTZ NOT NULL,
    locked_at   TIMESTAMPTZ NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);

-- Latest plan (single-row table; content is JSONB for /plans/latest)
CREATE TABLE execution_plan_latest (
    id            SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    plan          JSONB NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL
);
```

- `resources` is denormalized (cached resolved set) to survive planner restarts without re-resolving every task.
- Optimistic concurrency is enforced via the `version` column on `tasks` and `task_progress`.
- No unique index or FK on resources - Resolutor never interprets them.

---

## 10. Configuration

`application.yml`:

```yaml
resolutor:
  planner:
    tick-interval: 500ms
    batch-size: 100
    order-policy: FIFO
    max-bucket-size: 10000
  dispatch:
    mode: in-process           # in-process | kafka
    max-concurrent-groups: 0   # 0 = unbounded
    task-timeout: 5m
  backpressure:
    enabled: true
    default:
      max-inflight-messages: 1_000_000
      max-emission-rate-per-hour: 500_000
    classes:
      project:
        max-inflight-messages: 250_000
  resource-graph:
    endpoint: http://localhost:9000/resources
    timeout: 2s
    circuit-breaker:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 30s

spring:
  threads:
    virtual:
      enabled: true            # servlet requests on virtual threads
  datasource:
    url: jdbc:postgresql://localhost:5432/resolutor
  jpa:
    open-in-view: false
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

`ResolutorProperties` is a `@ConfigurationProperties("resolutor")` record with `@Validated` on all bounds.

---

## 11. Observability

Micrometer registrations under `resolutor.*`:

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `resolutor.tasks.state` | Gauge | `state` | Live count per state. |
| `resolutor.plan.tasks.total` | Gauge | - | `totalTasks` from latest plan. |
| `resolutor.plan.components` | Gauge | - | Connected components in latest plan. |
| `resolutor.plan.parallelism` | Gauge | - | `parallelismFactor`. |
| `resolutor.plan.serialization_ratio` | Gauge | - | `serializationRatio`. |
| `resolutor.plan.build.duration` | Timer | - | Planner compile time. |
| `resolutor.dispatch.group.duration` | Timer | `component_id` | Time to execute a group. |
| `resolutor.backpressure.inflight` | Gauge | `resource_class` | CMS estimate. |
| `resolutor.backpressure.rate` | Gauge | `resource_class` | Ring buffer sum. |
| `resolutor.resource_graph.calls` | Counter | `outcome` | success / failure / timeout. |

Structured JSON logs via Logback + `logstash-logback-encoder`. Correlation ID propagated through `MDC` from the ingress controller.

A minimal Grafana dashboard JSON ships in `deploy/grafana/resolutor-overview.json`.

---

## 12. Testing Strategy

| Layer | Tooling | What it covers |
|---|---|---|
| Domain unit | JUnit 5 + AssertJ | `ConflictGraph`, union-find, `ExecutionPlan` invariants, ordering policies. Pure, no Spring. |
| Application unit | JUnit 5 + in-memory port fakes | Planner pipeline end-to-end without I/O. **Runs the design's running example (Delete Project 7, §10)** as a golden test - the produced plan must have exactly 2 components, largestComponent=4. |
| Backpressure | JUnit 5 + property-based (jqwik) | CMS false-positive bounds; ring buffer window correctness. |
| Adapter | Spring `@DataJpaTest`, Testcontainers Postgres | Repositories, Flyway migrations up/down. |
| Integration | `@SpringBootTest` + Testcontainers (Postgres, mock resource service via WireMock) | Full slice: ingest → plan → dispatch → complete. |
| Contract | Spring Cloud Contract (optional) | REST + `ResourceGraphPort` HTTP shape. |
| Architecture | ArchUnit | Enforces module boundaries listed in §3. |
| Performance | JMH | Graph build, union-find, CMS admit throughput. Budgets in `perf/` docs. |
| Load | k6 script in `perf/k6/` | Reproduces the §30 evaluation (500 tasks, conflict prob 5–30%). |

Coverage target: 85% line / 75% branch on domain + application. Adapters excluded from thresholds.

---

## 13. CI / CD

GitHub Actions:

- `build.yml` - on push/PR: `./gradlew check spotlessCheck` on JDK 21 (Temurin). Testcontainers uses the runner's Docker.
- `codeql.yml` - CodeQL Java scan weekly.
- `release.yml` - on tag `v*`: publish to Maven Central via `maven-publish` + `signing`, build & push OCI image via `bootBuildImage`.
- Renovate weekly PRs for dependency bumps.
- Codecov upload from `build.yml`.

Branch protection on `main`: green CI + 1 review, no force-push, linear history.

---

## 14. Deployment

- **Dockerfile:** multi-stage (Gradle builder → distroless JRE 21 runtime). `bootBuildImage` remains an optional alternative.
- **docker-compose.yml** (dev): Postgres 14 + resolutor + optional Kafka (profile `kafka`).
- **Kubernetes** (`deploy/k8s/`): Deployment (1–3 replicas), Service, ConfigMap, Secret template, PodDisruptionBudget, ServiceMonitor for Prometheus Operator. ShedLock guarantees single-leader planner regardless of replica count.
- Health probes: `/actuator/health/liveness` and `/readiness` (readiness gated on DB + migrations complete).
- Graceful shutdown: 30s grace, dispatcher drains in-flight groups, refuses new plan ticks.

---

## 15. Delivery Phases (v1)

Each phase ends with a green CI build and a demoable increment. Post-v1 work is in **§18**.

| Phase | Duration (est.) | Deliverable |
|---|---|---|
| **0. Bootstrap** | 1–2 d | Gradle multi-project, buildSrc conventions, Spotless/ErrorProne/NullAway wired, empty modules compile, ArchUnit rule stubs, GitHub Actions build.yml green. |
| **1. Domain core** | 2–3 d | All records/sealed types from §4; `ConflictGraphBuilder`, `ConnectedComponents`, `OrderingPolicy` implementations; running-example golden test passes. |
| **2. Application services** | 3–5 d | Ports, `ExecutionPlanner`, `BackpressureManager` (CMS + ring buffer), `ProgressTracker`, `LeadershipManager`; all unit-tested with in-memory port fakes. |
| **3. Persistence adapter** | 2–3 d | JPA entities, mappers to/from domain records, Flyway `V1__init.sql`, `@DataJpaTest` coverage, `TaskRepositoryPort`/`ProgressRepositoryPort` impls. |
| **4. Web adapter** | 2 d | REST controllers for the 5 endpoints in §25, DTOs + Bean Validation, `problem+json` error responses, OpenAPI via springdoc. |
| **5. Runtime + integration** | 3–4 d | `DispatchService` on virtual threads, `PlannerTick` scheduled + ShedLock-guarded, `PlanPublisher` writes to `execution_plan_latest`, `ResourceGraphHttpAdapter` with Resilience4j, end-to-end `@SpringBootTest` green. |
| **6. Observability + packaging** | 2 d | Micrometer metrics wired, Grafana JSON, structured logs, Dockerfile, docker-compose, k8s manifests, README quick-start actually works. |
| **7. Perf + hardening** | 3–5 d | JMH benches, k6 load script reproducing §30 numbers within tolerance, tune batch/tick defaults, chaos test: kill planner mid-tick, verify no lost/dup tasks. |
| **8. Release 1.0** | 1 d | **Manual (future):** tag `v*`, sign, publish to Maven Central + GHCR, cut release notes referencing design.md. Not part of the v1 implementation PRs. |

Total for v1: ~3–4 weeks for one focused engineer, ~2 weeks with two.

---

## 16. Risks & Open Questions

- **Resource resolution latency dominates planning time.** If `ResourceGraphPort` is slow, batch throughput collapses. Mitigation: per-tick resource cache, circuit breaker, and future v2 pass that resolves resources concurrently on virtual threads.
- **CMS false positives** can starve a resource class under sustained load. The sketch parameters (width=65536, depth=5) give ~0.02% error at 1M distinct items per class - acceptable, but we should expose the sketch size in config for tuning.
- **Cursor semantics are implementer-defined.** `docs/cursor-contract.md` is part of the v2 definition of done so third-party workers behave predictably.
- **Plan staleness window** (design.md §19) assumes ms–s. If a business operation invalidates resources mid-execution, tasks fail and retry. This needs a worked example in the operator guide.
- **Kafka adapter deferred to v2 phase 12.** Topic layout: one topic per component id, or a partitioned topic with component-id as key, so the `DispatcherPort` does not reshape.
- **Build tool license/policy for Buildpacks vs. Dockerfile** - v1 ships a multi-stage Dockerfile; Buildpacks remain optional.

---

## 17. Definition of Done for v1

1. `docker compose up -d` boots Postgres + Resolutor; README quick-start `curl` commands succeed.
2. The running example from `design.md` §10 produces the exact `ExecutionPlan` shape asserted in the golden test.
3. Load test at conflict probability 20% completes within 20% of the numbers in §30 on a comparable machine.
4. All CI checks green: unit, integration, ArchUnit, Spotless, Error Prone, NullAway, SpotBugs, coverage thresholds.
5. Prometheus dashboard shows live values for every metric in §11.
6. Killing the leader mid-tick causes zero task loss and zero duplicate task completion, verified by chaos test.
7. `docs/design.md`, `docs/implementation-plan.md`, `docs/api.md`, and `docs/configuration.md` all reference each other and are consistent.

---

## 18. Delivery Phases (v2–v4)

These versions start after v1.0 is tagged. They match the README roadmap. Design.md §31 names graph colouring “Planner v2”; in this plan colouring is **v3** so v2 can finish the ExecutionPlan IR and monitoring seams that v1 left as stubs.

Each phase still ends with a green CI build. Do not start a later version’s phases until the previous version’s definition of done is met.

### v2 - ExecutionPlan as first-class IR

v1 already emits an immutable `ExecutionPlan` and Prometheus metrics. v2 makes the plan a **stored, queryable, transformable IR** and deepens observability (design.md §9, implementation-plan §5 `planFor(Instant)` seam).

| Phase | Duration (est.) | Deliverable |
|---|---|---|
| **9. Plan store + API** | 3–4 d | Persist every compiled plan (not only `execution_plan_latest`). `GET /api/v1/plans/{id}`, `GET /api/v1/plans?at=`, implement `PlanQueryPort.planFor(Instant)`. Plans remain deep-immutable; history is append-only. |
| **10. Pass pipeline** | 3–4 d | Introduce `ExecutionPlanPass` in `application.planner.pass`. Move connected-components behind the SPI. Resolve `ResourceGraphPort` concurrently on virtual threads with a per-tick cache. No change to dispatcher semantics. |
| **11. Plan observability** | 2–3 d | OpenTelemetry traces around compile/dispatch. Plan-explain payload (conflict density, average degree, `taskToComponent`). Grafana panels for plan history and compile vs dispatch time. Simulation endpoint: submit a dry-run batch, return the plan without dispatch. |
| **12. Kafka dispatch adapter** | 3–4 d | Optional `resolutor.dispatch.mode=kafka`: one topic (or partition key) per component id as sketched in §16. Resilience4j around produce/consume. Default remains in-process. |

**v2 definition of done:** a plan compiled at time *t* can be fetched later and is byte-identical to what was dispatched; a new pass can be registered without changing `InProcessDispatcher`; Kafka mode is documented and off by default; `docs/cursor-contract.md` exists so third-party workers match pause/resume semantics.

### v3 - Intra-group parallelism (graph colouring)

v1 serialises each connected component (design.md §8). v3 colours the conflict subgraph so **independent tasks inside a component run concurrently** while adjacent tasks stay serialised (design.md §6, §31).

| Phase | Duration (est.) | Deliverable |
|---|---|---|
| **13. Colouring domain + pass** | 4–5 d | Domain types for colour / independent-set waves. `GraphColoringPass` on each component (greedy colouring, deterministic vertex order). Adjacent tasks never share a colour. Golden test on the Delete Project 7 example: serial groups remain conflict-free; independent tasks inside a component gain extra waves. |
| **14. Wave dispatcher** | 3–4 d | Runtime interprets colour waves: tasks of the same colour in a component may run in parallel; waves stay ordered. Preserve at-least-once, cursors, and timeout reaper. Config flag to fall back to v1 serial groups. |
| **15. Colouring metrics + eval** | 2–3 d | Meters: chromatic number, intra-component parallelism factor, wave count. JMH + k6 vs v1 on the §30 conflict sweep; document speedup and any correctness caveats. |

**v3 definition of done:** no two tasks that share a resource run in the same wave; chaos recovery still completes each task once; colouring can be disabled to reproduce v1 plans; eval numbers are recorded in `perf/`.

### v4 - Cost-based optimisation

Uses the cost model in design.md §17. Passes **reorder** work without changing conflict correctness: shorter critical path, better locality, respect backpressure.

| Phase | Duration (est.) | Deliverable |
|---|---|---|
| **16. Cost model** | 3–4 d | Per-task duration estimates (config, histogram from dispatch metrics, or worker-supplied). Critical-path length on the coloured (or serial) group DAG. Plan metrics include estimated vs actual duration. |
| **17. Locality + backpressure reorder** | 4–5 d | Resource-locality pass (cluster tasks that share a resource class/id when colouring allows). Backpressure-aware reordering: delay or deprioritise classes near CMS/ring-buffer limits instead of dropping whole components when a cheaper order exists. |
| **18. Optimisation eval** | 2–3 d | Replay §30 workloads comparing v1 serial, v3 colouring, and v4 cost/locality. Document when the extra passes pay for themselves (batch size, conflict probability, task duration). |

**v4 definition of done:** optimisation passes never introduce a new conflict edge violation; estimated duration is within a documented error band on the synthetic workload; locality/backpressure passes are toggleable.

**Still out of scope after v4** (design.md §31): distributed multi-planner execution, full event-sourced audit trail. Track those as a later major version, not v4.
