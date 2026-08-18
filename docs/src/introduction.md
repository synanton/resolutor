# Introduction

Resolutor is a **compiler-inspired execution planner**. It analyses which tasks share business resources, builds a conflict graph, and compiles an immutable `ExecutionPlan` so work runs concurrently only when it is safe.

This book is the **developer documentation**:

| Part | Audience | Contents |
| --- | --- | --- |
| [Development guide](guide/index.md) | People changing Resolutor or embedding it | Modules, [main classes](guide/classes.md), [embedding](guide/embedding.md), pipeline, tests |
| [API reference](api/index.md) | HTTP and port consumers | REST, resource-graph contract, worker outcomes, metrics |
| [Examples: speech analysis](examples/speech-domain.md) | Integrators | Tag recalculation on talk transcriptions |

Canonical design notes still live beside this book:

- [`design.md`](../design.md) - model and rationale
- [`implementation-plan.md`](../implementation-plan.md) - delivery phases
- Interactive OpenAPI when the app is running: `/swagger-ui.html`

## Build this book

Install [mdBook](https://rust-lang.github.io/mdBook/) 0.4+, then from the repository root:

```bash
cd docs
mdbook serve --open
```

HTML is written to `docs/book/` (gitignored). `mdbook build` produces the same tree without a local server.

## What Resolutor does not do

Resolutor does not transcribe audio, run an LLM, or own your CMS graph. Your application implements `ResourceGraphPort` and `TaskWorker`. Resolutor only **schedules** opaque tasks against opaque `{class, id}` resources.
