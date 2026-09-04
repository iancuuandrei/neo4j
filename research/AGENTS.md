# Neo4j research workspace

## Scope

This file governs `research/`. Also follow the root `AGENTS.md` and all standards
in `docs/standards/`.

## Research branch rules

- Research may contain instrumentation, prototypes, comparative variants,
  harnesses, and reports. It is not an upstream-ready diff.
- Define the specification and frozen benchmark contract before choosing a
  candidate or interpreting performance.
- Preserve baseline and variants at the same exact upstream SHA.
- Keep reusable infrastructure in the topic's shared `common/` area rather than
  duplicating it per experiment.
- Retain provenance for abandoned candidates and negative results.
- Never weaken correctness, memory, cancellation, or lifecycle checks.
- Graduate through a new clean `contrib/*` branch. Do not merge research history.

## Artifacts

- Large or rebuildable artifacts live in the shared ignored store on `D:`.
- Git contains reproducible scripts, manifests, checksums, small summaries, and
  reports—not downloaded graphs, stores, caches, builds, profiles, or bulky runs.
- Raw runs are immutable and append-only. Retries use new names.
- Every report resolves to exact raw inputs and its analysis command.

## PPBFS organization

Related StatefulShortestPath/PPBFS experiments belong in one `research/ppbfs/`
lab with shared dataset, query, instrumentation, metadata, analysis, and report
tooling. Separate experiment deltas by subdirectory. Do not create a second
benchmark harness for each candidate.
