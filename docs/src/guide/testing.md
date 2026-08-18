# Testing

## Unit tests

Application tests use fakes in `resolutor-application` test fixtures: `InMemoryTaskRepository`, `FixedResourceGraphPort`, `NoOpTaskWorker`, `NoOpMetricsPort`.

```java
FixedResourceGraphPort graph = new FixedResourceGraphPort();
graph.register(task, Set.of(Resource.of("talk", "1001"), Resource.of("project", "42")));

ExecutionPlanner planner =
    new ExecutionPlanner(
        graph,
        new BackpressureManager(BackpressureConfig.disabled()),
        new NoOpMetricsPort(),
        PlannerConfig.defaults("v4"));

assertThat(planner.compile(List.of(task)).groups()).hasSize(1);
```

Gradle:

```kotlin
testImplementation(testFixtures(project(":resolutor-application")))
```

Speech-analysis isolation test (shared talk vs disjoint talk): [Compile a batch in a test](../examples/compile-offline.md).

Golden conflict example: `RunningExampleTest` (design §10). Colouring path-of-three: `ColouringExampleTest`. v4: `CostLocalityBackpressureTest`.

## Architecture

`HexagonalArchitectureTest` - keep new code on the correct side of the ports.

## Web

`@WebMvcTest` on controllers; `problem+json` assertions in `TaskControllerTest` / `PlanControllerTest`.

## Persistence

`@DataJpaTest` + Testcontainers PostgreSQL. Skips without Docker.

## Chaos

`ChaosRecoveryTest`: a `STARTED` orphan completes **once** on the next leadership cycle.

## Style

Spotless **google-java-format**. Public types need Javadoc. JSpecify `@Nullable`. No Lombok.

```bash
./gradlew spotlessApply check
```
