# Direct PPBFS product-state lookup

## Status

**Maintainer discussion warranted; not submitted upstream.** The complete study is preserved in draft [research PR #1](https://github.com/iancuuandrei/neo4j/pull/1). A minimal review branch exists at [`contrib/ppbfs-direct-state-index`](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-direct-state-index), independently rooted at the exact upstream baseline.

Final research classification: `GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`.

## Problem and design

At `neo4j/neo4j@f213380f812b820a1b312e2ea52cb3d8f1931ccc`, `FoundNodes.get(nodeId,stateId)` checks active structures and then scans historical BFS levels. `BFSExpander` uses this lookup for accepted product-state encounters.

The C1 candidate retains active frontier maps for traversal but replaces historical lookup with one query-local canonical node-major repository. Retired frontier containers can then be released while each identity-bearing `NodeState` remains directly addressable.

```text
Before: expected Theta(X + sum(history levels probed))
C1:     expected Theta(X) lookup bookkeeping
```

## Causal evidence

- Controlled chain depth 4096: 8,382,465 historical probes for 4,097 accepted lookups.
- B0 JFR: `FoundNodes.get` in 468/919 execution samples (50.9%).
- C1 JFR: 2/817 samples (0.24%).

## Multi-topology results

Five paired fresh-JVM forks were used for each formal dataset claim; confidence intervals are two-sided paired-log 95% intervals over per-fork medians.

| Workload | C1 speedup | 95% CI |
| --- | ---: | ---: |
| roadNet-PA d250/d500/d772 | 4.373x | [2.126x, 8.993x] |
| roadNet-CA all d10–800 | 2.340x | [2.268x, 2.414x] |
| roadNet-CA d250+ | 5.289x | [4.808x, 5.818x] |
| web-Stanford | 1.079x | [1.060x, 1.098x] |
| as-Skitter low-diameter control | 1.014x | [0.989x, 1.039x] |

Every included query returned equal results and PROFILE-confirmed `StatefulShortestPath(Into, Trail)`. The depth response replicated independently on roadNet-CA; the low-diameter control remained neutral.

## Memory and allocation trade-off

The original strict result remains valid: roadNet-PA d250 passes at 92 MiB on B0 and first passes at 93 MiB on C1. Representative curves found equal thresholds at PA d100/d500/d772 and CA d100/d800, with CA shifts of 102→104 MiB at d250 and 1288→1296 MiB at d500.

PROFILE tracked-memory deltas ranged from small/negative on many road cases to roughly +10% on selected web/Skitter cases. A representative qualified JFR run estimated +5.5–7.0% total C1 allocation, with 23 vs 25 GC events and similar absolute GC pause.

This is a measured engineering trade-off, not a correctness failure or broad latency regression. B0 and C1 are both Pareto-relevant: B0 wins strict memory compatibility; C1 wins latency and lookup complexity.

## Alternatives

- C2 state-major primitive maps first passed PA d250 at 94 MiB and screened slower than C1.
- C3 adaptive tiny-sparse→dense buckets did not recover the 92 MiB boundary.
- C4 flat primitive product-state table remains unimplemented; maintainer guidance on existing collections/ownership is more valuable before another candidate.

C2/C3 remain preserved as non-selected comparators, not erased failures.

## Correctness and validation

The clean branch contains only the production `FoundNodes` change and focused tests. Spotless passed. The focused run executed 76 tests with zero failures/errors and five existing skips. The full runtime-util module attempt was blocked by a Windows paging-file reservation failure in a parallel test JVM and is not claimed as PASS.

## Upstream posture

No upstream issue or PR has been opened. The proposed first contact is a measured investigation asking whether the small query-memory headroom increase is acceptable and whether Neo4j prefers a different internal collection or ownership model.

## Links

- [Final research report](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/BENCHMARK_REPORT.md)
- [Pareto analysis](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/FINAL_PARETO_ANALYSIS.md)
- [Memory trade-off](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/MEMORY_TRADEOFF_ANALYSIS.md)
- [Allocation/GC analysis](https://github.com/iancuuandrei/neo4j/blob/research/ppbfs-lab/research/ppbfs/state-index/ALLOCATION_GC_ANALYSIS.md)
