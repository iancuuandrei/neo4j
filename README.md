# Neo4j Contributions

This is my personal fork of [Neo4j](https://github.com/neo4j/neo4j), used for
upstream-oriented database-engine work and reproducible research into graph
traversal, path-query execution, and query optimization. Production candidates
are kept on isolated `contrib/*` branches; experimental code and benchmark
infrastructure remain on `research/*` branches so prospective upstream changes
stay reviewable.

## Contributions

Each item links to its full presentation; branches and upstream
references stay one click away so this list stays one line per item.

- [Direct PPBFS product-state lookup](docs/contributions/ppbfs-direct-state-index.md) — **Proposed** · 4.373x PA deep, 5.289x CA deep · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-direct-state-index) · [upstream #13966](https://github.com/neo4j/neo4j/issues/13966)
- [Deferred PPBFS product-state indexing](docs/contributions/ppbfs-deferred-state-index.md) — **Proposed** · PA d250+ 4.370x, CA d250+ 4.098x · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-deferred-state-index) · [upstream #13966](https://github.com/neo4j/neo4j/issues/13966)
- [PPBFS WALK post-saturation bookkeeping](docs/contributions/ppbfs-walk-post-saturation-bookkeeping.md) — **Proposed** · LJ 56/68 improved, web 6–7x timing · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-walk-post-saturation-bookkeeping) · [upstream #13968](https://github.com/neo4j/neo4j/issues/13968)
- [PPBFS frontier work selection](docs/contributions/ppbfs-frontier-work-selection.md) — **Planned**
- [PPBFS transition dispatch](docs/contributions/ppbfs-transition-dispatch.md) — **Planned**
- [SSP to FSP specialization for simple QPP node groups](docs/contributions/p10-shortest-path-specialization.md) — **PR open** · up to 5.49x · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ssp-to-fsp-node-groups) · [PR neo4j/neo4j#13970](https://github.com/neo4j/neo4j/pull/13970)
- [Execution model fix for unsupported runtime fallback](docs/contributions/community-pipelined-fallback-execution-model.md) — **PR open** · pipelined ~8.4s → ~0.4s · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/investigate/13937) · [PR neo4j/neo4j#13972](https://github.com/neo4j/neo4j/pull/13972)
- [Repeated nested COUNT evaluation in optional matches](docs/contributions/13924-optional-count-hoist.md) — **Submitted for upstream review** · 3,978 → 18 nested COUNT evaluations · [branch](https://github.com/iancuuandrei/neo4j-contributions/tree/fix/13924-optional-count-hoist) · [PR neo4j/neo4j#13973](https://github.com/neo4j/neo4j/pull/13973)

## Current research

- [StatefulShortestPath / PPBFS](docs/research/ppbfs-lab.md) — active shared lab for source analysis, instrumentation, real-graph workloads, controlled complexity tests, and isolated candidate designs.
- [Cyclic pattern execution](docs/research/cyclic-pattern-execution.md) — planned investigation; no implementation or findings yet.
- [Path cardinality](docs/research/path-cardinality.md) — planned investigation; no implementation or findings yet.
- [Graph Data Science](https://github.com/iancuuandrei/graph-data-science) — maintained as a separate fork because it is a separate upstream repository.

## Methodology

Performance work compares one exact upstream SHA with that same SHA plus one
isolated patch. Claims progress from source-confirmed, to experimental, to
systematically validated evidence; regressions and negative results are retained.
See the [PPBFS benchmark methodology](docs/benchmarks/ppbfs-benchmark-suite.md).

## Repository structure

- `portfolio/*` — public-facing documentation; never submitted upstream.
- `research/*` — instrumentation, prototypes, shared benchmarks, and evidence.
- `contrib/*` — one clean, independently reviewable upstream candidate per branch.

The operating workflow is documented in [docs/WORKFLOW.md](docs/WORKFLOW.md).

## Upstream and attribution

Neo4j is developed by Neo4j and its contributors. Most code in this repository is
upstream Neo4j source. Only explicitly identified personal branches and commits
represent my work. Existing upstream licenses and attribution remain authoritative.
The baseline's [upstream project README](docs/upstream/README.asciidoc) is preserved
unchanged in substance for reference.
