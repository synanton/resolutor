# Module layout

Gradle projects under the canonical package `org.synanton.resolutor`.

| Module | Depends on | Responsibility |
| --- | --- | --- |
| `resolutor-domain` | - | Records: task, resource, graph, plan, policies |
| `resolutor-application` | domain | Ports, planner, dispatcher, backpressure, leadership |
| `resolutor-adapter-web` | application | REST, validation, `problem+json`, springdoc |
| `resolutor-adapter-persistence-jpa` | application | Flyway, JPA, plan JSONB |
| `resolutor-adapter-resource-http` | application | `ResourceGraphPort` over HTTP + Resilience4j |
| `resolutor-adapter-kafka` | application, dispatcher | Optional group publish/consume |
| `resolutor-adapter-metrics` | application | Micrometer / Prometheus |
| `resolutor-app` | all adapters | `App`, `application.yml`, composition root |

ArchUnit in `resolutor-application` forbids domain → Spring, domain → application, application → adapters.

Class-by-class map: [Main classes](classes.md). Wiring your `TaskWorker` / `ResourceGraphPort`: [Embedding](embedding.md).

## Where to change what

| Change | Place |
| --- | --- |
| New plan metric / wave field | `resolutor-domain` then JSON mapper + DTOs |
| New optimisation pass | `application.planner.pass.ExecutionPlanPass` |
| New REST route | `resolutor-adapter-web` |
| SQL schema | Flyway under `resolutor-app/src/main/resources/db/migration` |
| Default worker | Override `TaskWorker` `@Bean` in your app / `ApplicationBeansConfig` |
