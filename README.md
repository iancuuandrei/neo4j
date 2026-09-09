# Neo4j Contributions

This is my personal fork of [Neo4j](https://github.com/neo4j/neo4j), used for
upstream-oriented database-engine work and reproducible research into graph
traversal, path-query execution, and query optimization. Production candidates
are kept on isolated `contrib/*` branches; experimental code and benchmark
infrastructure remain on `research/*` branches so prospective upstream changes
stay reviewable.

## Contributions

| Contribution | Area | Status | Headline impact | Technical write-up | Implementation | Upstream |
| --- | --- | --- | --- | --- | --- | --- |
| Direct PPBFS product-state lookup | StatefulShortestPath / PPBFS | **Maintainer discussion warranted** | 4.373x PA deep; 5.289x CA deep; small measured memory/allocation trade-off | [Case study](docs/contributions/ppbfs-direct-state-index.md) | [Clean branch](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-direct-state-index) · [Research PR](https://github.com/iancuuandrei/neo4j/pull/1) | Not submitted upstream |
| PPBFS WALK post-saturation bookkeeping | StatefulShortestPath / PPBFS | **Candidate** | Real-world qualified: LJ 56/68 improved (93.5% post-sat removed); web 6-7x timing; LJ LIMIT 75 baseline >90s vs 53 rows in 52ms; JFR PPBFS 47% to 11% | [Case study](docs/contributions/ppbfs-walk-post-saturation-bookkeeping.md) | [Clean branch](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-walk-post-saturation-bookkeeping) | Not submitted upstream |
| PPBFS frontier work selection | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-frontier-work-selection.md) | Not created | Not submitted |
| PPBFS transition dispatch | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-transition-dispatch.md) | Not created | Not submitted |
| SSP to FSP specialization for simple QPP node groups | StatefulShortestPath / FindShortestPaths | **Candidate** | chain16 1.89x; chain64 1.92x; diamond 1.42x; tree b4/d6 5.49x; A-specific 1.47x | [Case study](docs/contributions/p10-shortest-path-specialization.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ssp-to-fsp-node-groups) | [neo4j/neo4j#13969](https://github.com/neo4j/neo4j/issues/13969) |

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
