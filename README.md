# Neo4j Contributions — Andrei Iancu

This is my personal fork of [Neo4j](https://github.com/neo4j/neo4j), used for
upstream-oriented database-engine work and reproducible research into graph
traversal, path-query execution, and query optimization. Production candidates
are kept on isolated `contrib/*` branches; experimental code and benchmark
infrastructure remain on `research/*` branches so prospective upstream changes
stay reviewable.

## Contributions

| Contribution | Area | Status | Headline impact | Technical write-up | Implementation | Upstream |
| --- | --- | --- | --- | --- | --- | --- |
| Direct PPBFS product-state lookup | StatefulShortestPath / PPBFS | **Benchmarking** | Experimental evidence exists; validation is incomplete | [Case study](docs/contributions/ppbfs-direct-state-index.md) | [Prepared clean branch](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-direct-state-index) · [Research candidate](https://github.com/iancuuandrei/neo4j/tree/research/ppbfs-lab) | Not submitted |
| PPBFS frontier work selection | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-frontier-work-selection.md) | Not created | Not submitted |
| PPBFS transition dispatch | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-transition-dispatch.md) | Not created | Not submitted |

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
