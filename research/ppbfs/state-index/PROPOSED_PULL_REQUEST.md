# Draft upstream pull request — do not open automatically

## Title

perf(cypher): add direct PPBFS product-state lookup

## Summary

Add one query-local canonical node-major repository to `FoundNodes` so accepted `(nodeId,NFA stateId)` lookups no longer scan every prior BFS level. Preserve frontier/history structures for traversal ordering and retirement semantics; preserve exactly one identity-bearing `NodeState` per product state.

## Why

At upstream baseline `f213380f812b820a1b312e2ea52cb3d8f1931ccc`, controlled depth-4096 execution records 8,382,465 historical probes for 4,097 accepted lookups and JFR places `FoundNodes.get` in 50.9% of baseline execution samples. The clean candidate reduces that sample share to 0.24%.

## Evidence

| Workload | C1 speedup | 95% CI |
| --- | ---: | ---: |
| roadNet-PA d250/d500/d772 | 4.373x | [2.126x, 8.993x] |
| roadNet-CA all d10–800 | 2.340x | [2.268x, 2.414x] |
| roadNet-CA d250+ | 5.289x | [4.808x, 5.818x] |
| web-Stanford | 1.079x | [1.060x, 1.098x] |
| as-Skitter control | 1.014x | [0.989x, 1.039x] |
| cit-Patents d1–12 | 1.220x | [0.977x, 1.524x] |
| Hetionet H3 extension | 0.996x | [0.954x, 1.041x] |
| LiveJournal extension | 0.948x | [0.782x, 1.149x] |

All timed cases returned identical results and executed the intended operator. Under the predeclared +/-5% practical margin, Hetionet H3 is equivalent (90% CI `[0.962,1.032]`) and cit-Patents is practically non-inferior (`[1.029,1.448]`). LiveJournal remains inconclusive and is an explicit common-case risk, not evidence of neutrality.

## Explicit trade-off

C1 formally fails the research study's strict identical-memory-limit gate: PA d250 moves from 92 to 93 MiB minimum observed passing configuration. Other representative points were equal or shifted by at most 1.96% in configured headroom. PROFILE memory can be about +10% on selected contrasting-topology cases, and representative JFR estimates +5.5–7.0% total allocation. GC count did not increase and absolute pause was similar.

This must be discussed with maintainers before an upstream PR is opened. The pull request must not hide or relabel the memory cost.

## Tests

- canonical identity before/after frontier retirement;
- bidirectional sharing and duplicate rejection;
- normal close and interruption memory cleanup;
- illegal buffer transitions;
- existing generated/differential PPBFS coverage;
- focused query-memory accounting.

Local clean-branch result: 76 focused tests, 0 failures/errors, 5 existing skips; Spotless PASS. Full runtime-util module using the repository-supported sequential profile: 526 tests, 0 failures/errors, 5 existing skips, PASS. The earlier parallel paging-file failure is retained as environmental provenance.

## Scope

The upstream branch contains only production `FoundNodes` plumbing and focused upstream-worthy tests. It excludes research scripts, datasets, reports, JFR, feature flags, hooks, C2, C3, and machine-specific paths.

## Rollback

Revert the query-local canonical repository and restore history scanning. No data migration, store-format, configuration, or public API change is involved.
