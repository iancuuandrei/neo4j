# WITHHELD — proposed upstream issue

Status: `DO NOT POST`. The experiment ended in a memory-gate NO-GO and selected
no production design. This local draft exists only to make the upstream-contact
decision explicit.

## Candidate title

StatefulShortestPath: history-depth-linear product-state lookup investigation

## Draft

At `neo4j/neo4j@f213380f812b820a1b312e2ea52cb3d8f1931ccc`,
`BFSExpander.encounter` resolves `(nodeId,stateId)` through `FoundNodes.get`,
which probes active structures and historical BFS levels. Controlled chain
instrumentation reached 8,382,465 history probes for 4,097 accepted lookups at
depth 4096, and a roadNet-PA profile attributed 50.9% of sampled baseline
execution to `FoundNodes.get`.

A canonical node-major prototype reduced the deep roadNet-PA geometric-mean
latency by 4.373x, 95% CI `[2.126x,8.993x]`, while preserving returned path
lengths. However, it failed at `db.memory.transaction.max=92m` where unchanged
upstream passed. State-major primitive maps first passed at 94 MiB, and an
adaptive two-inline-slot node bucket also failed at 92 MiB. All candidates used
tracked allocations and passed focused identity/differential tests.

Selected design: none. The unchanged level-partitioned repository is retained.
No compatibility, persistence, API, or migration change is proposed.

If this topic is revisited, the maintainer question is whether a different
memory contract or a fundamentally different non-duplicating representation is
worth exploring. Under the present requirement that a patch pass every limit
the baseline passes, there is no patch to review.

Evidence: `BENCHMARK_REPORT.md`, `reports/SOURCE_AUDIT.md`, and the append-only
raw roots recorded in the dated result reports.
