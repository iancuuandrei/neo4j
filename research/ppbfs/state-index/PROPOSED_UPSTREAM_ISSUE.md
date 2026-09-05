# Draft upstream issue — do not post automatically

## Title

StatefulShortestPath: investigate history-depth-linear product-state lookup in FoundNodes

## Draft

We investigated a query-local lookup behavior in `StatefulShortestPath` at `neo4j/neo4j@f213380f812b820a1b312e2ea52cb3d8f1931ccc` (`2026.07`). `BFSExpander.encounter` resolves `(nodeId,stateId)` through `FoundNodes.get`; after checking active structures, `get` scans historical BFS levels newest-to-oldest.

Controlled chain instrumentation recorded 8,382,465 historical probes for 4,097 accepted lookup attempts at depth 4096. In a roadNet-PA JFR, `FoundNodes.get` appeared in 468/919 baseline execution samples (50.9%). A query-local canonical node-major prototype reduced that to 2/817 samples (0.24%).

Five-paired-fork unprofiled timing found:

- roadNet-PA d250/d500/d772 aggregate: 4.373x, 95% CI `[2.126x,8.993x]`;
- independent roadNet-CA d250+ aggregate: 5.289x `[4.808x,5.818x]`;
- web-Stanford d2–140 aggregate: 1.079x `[1.060x,1.098x]`;
- low-diameter as-Skitter control: 1.014x `[0.989x,1.039x]`.

All included queries returned identical results and PROFILE-confirmed `StatefulShortestPath(Into, Trail)`. Focused identity, bidirectional, lifecycle, interruption, and generated differential tests passed.

There is a measured cost. On roadNet-PA d250, unchanged upstream passes a 92 MiB transaction-memory limit while the prototype first passes at 93 MiB. The shift was workload-dependent: PA d100/d500/d772 were equal; roadNet-CA observed 102→104 MiB at d250, 1288→1296 MiB at d500, and equality at d100/d800. PROFILE memory ranged from small/negative road-network deltas to roughly +10% on selected web/Skitter cases. A representative JFR workload estimated +5.5–7.0% total allocation, with 23 vs 25 GCs and similar absolute GC pause (0.966 vs 0.934 seconds).

We also tried state-major primitive maps (C2) and adaptive tiny-sparse→dense node buckets (C3). C2 first passed the PA d250 limit at 94 MiB and was slower than C1 in screening; C3 did not recover the 92 MiB boundary. They are retained as non-selected comparators.

An external falsification suite added native-semantics workloads. cit-Patents was practically non-inferior under a +/-5% margin (1.220x, 95% CI `[0.977,1.524]`, 90% CI `[1.029,1.448]`). Ten paired Hetionet H3 forks established equivalence within +/-5% (0.996x, 90% CI `[0.962,1.032]`). LiveJournal remained inconclusive (0.948x, 95% CI `[0.782,1.149]`), so a possible shallow high-fanout regression is disclosed rather than dismissed. A generated gMark instance was too shallow and variable to resolve the question.

The original experiment correctly classified C1 as failing a strict identical-limit gate. The broader evidence now leaves an engineering trade-off rather than a correctness failure or broad performance regression.

Questions for maintainers:

1. Is a small increase in query-memory headroom acceptable for this level of deep `StatefulShortestPath` speedup?
2. Is there an internal data structure or intended ownership model that would be preferable for canonical `(nodeId,stateId)` lookup?
3. Would a minimal C1 patch plus focused correctness/memory tests be useful for review, or should this remain an investigation first?

We are not claiming the prototype should be accepted as-is. Detailed protocol, raw hashes, memory curves, and negative candidate results are available in the research branch.
