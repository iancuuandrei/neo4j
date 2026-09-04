# Direct PPBFS product-state lookup

## Status

**Benchmarking.** An experimental implementation exists on
[`research/ppbfs-lab`](https://github.com/iancuuandrei/neo4j/tree/research/ppbfs-lab).
The clean [`contrib/ppbfs-direct-state-index`](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-direct-state-index)
branch is prepared from upstream but intentionally contains no implementation
commit yet. Nothing has been submitted upstream.

## Summary

This investigation asks whether PPBFS should maintain one direct repository from
`(data node ID, NFA state ID)` to its canonical `NodeState`, instead of searching
historical BFS-level maps on some encounters. The implementation will graduate to
the contribution branch only if correctness, latency, memory, regression, and
statistical gates all pass.

For shared architecture and experiment context, see the
[PPBFS Research Lab](../research/ppbfs-lab.md).

## Problem

**Source-confirmed:** At the audited upstream baseline,
`FoundNodes.get(long nodeId, int stateId)` checks active structures and then scans
historical BFS levels newest-to-oldest. `BFSExpander.encounter` uses that lookup
for accepted product-state encounters. The source documents lookup complexity as
linear in history length.

## Why it matters

Depth-heavy StatefulShortestPath traversals can accumulate many history levels.
Repeated misses or old-history hits may therefore turn state lookup bookkeeping
into a material cost even when graph DB hits are unchanged. Whether this matters
for representative real workloads is an empirical question; the source shape
alone is not sufficient evidence.

## Existing Neo4j architecture

The relevant execution surface includes `PGPathPropagatingBFS`, `FoundNodes`,
`BFSExpander`, `NodeState`, `ProductGraphTraversalCursor`, `PathTracer`, and
`TwoWaySignpost`. One mutable `NodeState` reference represents each discovered
`(nodeId,stateId)` product state across traversal directions and signpost updates.

## Root-cause analysis

**Source-confirmed:** Baseline lookup probes the next-level buffer, active forward
frontier, optional active backward frontier, and then each historical frontier.
The private history exists for lookup; path reconstruction and signpost state live
on canonical objects rather than being derived from their historical container.

## Complexity analysis

Let `h_i` be historical maps examined by lookup `i` and `X` the number of
encounters.

```text
Before: expected Θ(X + Σ h_i) lookup bookkeeping
Chain worst case: Θ(D²) lookup bookkeeping across depth D
Experimental direct repository: expected Θ(X) lookup bookkeeping
```

This does not change the total complexity of cursor evaluation, propagation,
path tracing, or result enumeration.

## Design

The first experimental candidate uses a heap-tracked primitive-long outer map and
dense NFA-state buckets as the canonical repository. Active frontiers remain
separate structural indexes for level progression. Retired frontier structures
can release their collection memory while canonical `NodeState` references remain
owned for the query lifetime.

## Alternatives considered

- Always-on duplicate side index: lower lifecycle disruption but retains all baseline history and duplicates references.
- Adaptive side index: avoids shallow insertion cost but introduces activation, backfill, and temporary-peak complexity.
- Sparse/dense adaptive buckets: potentially better for sparse large NFAs, but justified only if measured dense-bucket memory fails the gate.
- No change: required outcome if broad correctness/performance/memory gates do not pass.

No adaptive or sparse candidate has been implemented because current evidence has
not shown a memory problem requiring that added complexity.

## Implementation

Experimental commit: [`caf33be9f5d`](https://github.com/iancuuandrei/neo4j/commit/caf33be9f5de2d8d7f4a291abd466cf808aaf801).

Relevant experimental files are limited to the PPBFS runtime utility and its
tests/hooks. Required invariants include one object reference per product-state
identity, unchanged directional discovery, BFS level semantics, signpost and
propagation behavior, scoped heap accounting, exception behavior, and public
operator compatibility.

## Correctness

**Experimental:** Focused baseline and candidate PPBFS suites passed locally, and
paired roadNet-PA queries returned identical path-length multisets. These checks
are not yet the full required differential, bidirectional, cancellation,
near-memory-limit, and runtime-spec qualification.

## Benchmark methodology

```text
baseline: neo4j/neo4j @ f213380f812b820a1b312e2ea52cb3d8f1931ccc
candidate: baseline + isolated research commits through caf33be9f5d
primary JDK: Eclipse Temurin 21.0.12.1
hardware: AMD Ryzen 9 9955HX, 16 cores / 32 logical processors, ~31.2 GiB RAM
primary real graph so far: SNAP roadNet-PA
query: plan-verified StatefulShortestPath(Into, Trail), SHORTEST 2
```

See [PPBFS benchmark suite](../benchmarks/ppbfs-benchmark-suite.md) for the full
protocol and incomplete-work inventory.

## Results

The following are **experimental local observations, not validated impact**:

| Evidence | Baseline | Candidate | Interpretation |
| --- | ---: | ---: | --- |
| Controlled chain, depth 4096 | 8,382,465 historical probes / 4,097 lookups | Direct lookup path | Reproduced the predicted depth-sensitive mechanism |
| JFR samples containing `FoundNodes.get` | 468 / 919 (50.9%) | 2 / 817 (0.24%) | Time moved out of the suspected method in this controlled run |
| Matched 772-hop PROFILE time | ~31.55 s | ~5.59 s | Single plan-verified observation; identical 6,127,334 DB hits |
| Matched PROFILE memory | 1,042,920,640 B | 1,035,462,144 B | No memory increase in this observation |
| First formal fork, 250 / 500 / 772 hops | baseline | 4.38× / 7.49× / 13.04× faster | One paired JVM fork only; insufficient for a statistical claim |

No validated headline impact is reported because additional JVM forks, real
topologies, common-case regressions, memory limits, and confidence intervals are
unfinished.

## Regressions and trade-offs

- Shallow cases may pay direct insertion/global-map costs; systematic common-case results are pending.
- Dense buckets may be inefficient for large sparse NFAs; occupancy and near-limit tests are pending.
- Global-map resizing and cache locality have not been fully characterized.
- The canonical layout changes structural memory lifetime and therefore carries medium invariant risk.

## Upstream process

```text
Issue: Not submitted upstream
PR: Not submitted upstream
Maintainer feedback: None
Final outcome: Pending benchmark validation
```

## Relevant links

- [Shared PPBFS research branch](https://github.com/iancuuandrei/neo4j/tree/research/ppbfs-lab)
- [Prepared clean contribution branch](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-direct-state-index)
- [Research source audit](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/reports/SOURCE_AUDIT.md)
- [Mathematical model](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/reports/MATHEMATICAL_MODEL.md)
- [Experimental log](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/reports/EXPERIMENT_LOG.md)
