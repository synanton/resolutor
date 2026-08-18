# Performance budgets (phase 7)

Reproduce design.md §30 locally after `docker compose up -d`.

## JMH (planner microbenchmarks)

```
./gradlew :resolutor-application:jmh
```

Indicative budgets on a 4 vCPU laptop (throughput, ops/s):

| Benchmark | N=100 | N=500 |
| --- | --- | --- |
| `compileGraphAndComponents` | ≥ 5_000 | ≥ 800 |
| `admitThroughput` | ≥ 50_000 | ≥ 10_000 |

These are smoke budgets, not CI gates. Record results in a PR when changing the planner.

## k6 (ingest load)

Requires [k6](https://k6.io/). Conflict probability rises as `POOL` shrinks (shared project ids).

```
k6 run -e BASE_URL=http://localhost:8080 -e TASKS=500 -e POOL=20 perf/k6/plan.js
```

| Conflict (approx.) | POOL | Expect |
| --- | --- | --- |
| ~5% | 100 | ingest p95 &lt; 500 ms, HTTP errors &lt; 1% |
| ~20% | 20 | same ingest SLO; plan `parallelismFactor` &lt; 1 |
| ~30% | 10 | same ingest SLO; larger `serializationRatio` |

Planning overhead is observed via `resolutor_plan_build_duration_seconds` on `/actuator/prometheus`.

## Graph colouring (v3)

Colouring adds intra-component waves (`resolutor_plan_chromatic_number`, `resolutor_plan_wave_count`, `resolutor_plan_intra_component_parallelism`). It does **not** help cliques: a fully connected component still has wave size 1. Speedup shows up on sparse conflict graphs (paths, trees) where independent tasks share a colour. Disable with `resolutor.planner.colouring=false` to compare against v1 serial groups. JMH: `colourComponents` vs `compileGraphAndComponents`.

## Cost / locality (v4)

Critical-path estimate is `max(group: sum(wave: max(task duration)))` with groups concurrent. Default task duration is 100 ms (`resolutor.planner.default-task-duration`); override per class with `resolutor.planner.task-durations`. Observed dispatch times feed the next compile.

Locality sorts within a wave by resource class/id and does not add conflict edges. `backpressure-reorder=true` keeps tasks whose class is under limit instead of dropping the whole component.

Colouring + locality help sparse graphs and mixed class pressure. They do not shrink cliques. Toggle `cost`, `locality`, and `backpressure-reorder` independently. Synthetic check: estimated duration for independent tasks is one default quantum (groups overlap), not the sum.
