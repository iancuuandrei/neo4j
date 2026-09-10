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
| Direct PPBFS product-state lookup | StatefulShortestPath / PPBFS | **Proposed** | 4.373x PA deep; 5.289x CA deep; +1 MiB gate shift disclosed | [Case study](docs/contributions/ppbfs-direct-state-index.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-direct-state-index) · [Closed research PR #1](https://github.com/iancuuandrei/neo4j-contributions/pull/1) | [neo4j/neo4j#13966](https://github.com/neo4j/neo4j/issues/13966) (discussion; no upstream PR) |
| Deferred PPBFS product-state indexing | StatefulShortestPath / PPBFS | **Proposed** | PA d250+ 4.370x; CA d250+ 4.098x; Hetionet ~1.0x | [Case study](docs/contributions/ppbfs-deferred-state-index.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-deferred-state-index) | [neo4j/neo4j#13966](https://github.com/neo4j/neo4j/issues/13966) (discussion; no upstream PR) |
| PPBFS WALK post-saturation bookkeeping | StatefulShortestPath / PPBFS | **Proposed** | Real-world qualified: LJ 56/68 improved (93.5% post-sat removed); web 6-7x timing; LJ LIMIT 75 baseline >90s vs 53 rows in 52ms; JFR PPBFS 47% to 11% | [Case study](docs/contributions/ppbfs-walk-post-saturation-bookkeeping.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-walk-post-saturation-bookkeeping) | [neo4j/neo4j#13968](https://github.com/neo4j/neo4j/issues/13968) (no upstream PR) |
| PPBFS frontier work selection | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-frontier-work-selection.md) | Not created | Not submitted |
| PPBFS transition dispatch | StatefulShortestPath / PPBFS | **Planned** | Not measured | [Case study](docs/contributions/ppbfs-transition-dispatch.md) | Not created | Not submitted |
<| SSP to FSP specialization for simple QPP node groups | StatefulShortestPath / FindShortestPaths | **PR Open** | chain16 1.89x; chain64 1.92x; diamond 1.42x; tree b4/d6 5.49x; A-specific 1.47x | [Case study](docs/contributions/p10-shortest-path-specialization.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ssp-to-fsp-node-groups) | [neo4j/neo4j#13969](https://github.com/neo4j/neo4j/issues/13969) · [PR neo4j/neo4j#13970](https://github.com/neo4j/neo4j/pull/13970) |
| Execution model fix for unsupported runtime fallback | Cypher planner / Community runtime fallback | **PR Open** | Exact #13937: baseline pipelined ~8.4s vs slotted ~0.86s; patched pipelined ~0.32–0.46s, slotted-identical plan | [Case study](docs/contributions/community-pipelined-fallback-execution-model.md) | [Branch investigate/13937](https://github.com/iancuuandrei/neo4j-contributions/tree/investigate/13937) | [neo4j/neo4j#13937](https://github.com/neo4j/neo4j/issues/13937) · [PR neo4j/neo4j#13972](https://github.com/neo4j/neo4j/pull/13972) |
| Repeated nested COUNT evaluation in optional matches | Cypher planner / OPTIONAL MATCH | **Submitted for upstream review** | Structural 3,978 → 18 nested COUNT evaluations by plan construction (no wall-clock claim); 17/17 new regression tests | [Case study](docs/contributions/13924-optional-count-hoist.md) | [Clean branch](https://github.com/iancuuandrei/neo4j-contributions/tree/fix/13924-optional-count-hoist) | [neo4j/neo4j#13973](https://github.com/neo4j/neo4j/pull/13973) |

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
