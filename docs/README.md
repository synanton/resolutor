# Developer documentation (mdBook)

API reference, development guide, and speech-analysis examples.

```bash
# https://rust-lang.github.io/mdBook/
mdbook serve --open
```

Run this from the `docs/` directory. Output: `docs/book/` (gitignored).

| Book section | Path |
| --- | --- |
| Intro | `src/introduction.md` |
| Development guide | `src/guide/` (includes [main classes](src/guide/classes.md), [embedding](src/guide/embedding.md)) |
| API reference | `src/api/` (includes [Java ports](src/api/java-ports.md)) |
| Speech-analysis examples | `src/examples/` |

Design notes that are **not** in the book (paper / roadmap): `design.md`, `implementation-plan.md`.
