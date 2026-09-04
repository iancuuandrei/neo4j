# C3 adaptive product-state bucket experiment

Status: accepted for experiment  
Date: 2026-09-04  
Owner: local Neo4j contribution workspace  
Repository: `neo4j/neo4j`  
Branch: `research/ppbfs-lab`  
Authoritative upstream base: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`  
Research ancestry at acceptance: `f4958427680`

## Problem and trigger

B0's history scan is material on deep StatefulShortestPath traversals. C1
removes it with node-major dense canonical buckets, but fails at a transaction
memory limit where B0 passes. C2 uses state-major primitive maps, yet valid
measurements first pass at 94 MiB versus B0's 92 MiB and C1's 93 MiB, and its
screening point estimate is 10.1% slower than C1 overall and 14.4% slower on
deep roadNet-PA pairs. The predeclared C3 trigger is therefore satisfied.

## Goals

- Preserve direct canonical `(nodeId, stateId)` lookup and C1-like node-major
  locality.
- Avoid allocating `O(|Q|)` reference slots for every low-occupancy data node.
- Pass every correctness, lifecycle, cleanup, and result-equivalence gate.
- First pass at every tested transaction-memory limit where B0 passes.
- Recover at least 90-95% of C1 performance on relevant workloads without a
  repeatable common-case regression above 5%.
- Choose the sparse/dense crossover from measured occupancy, lookup, end-to-end,
  tracked-memory, allocation/GC, and near-limit evidence.

## Non-goals

- No primitive-map middle representation in the first C3 prototype.
- No frontier-layout, frontier-selection, transition-dispatch, cursor, planner,
  Cypher-semantics, or result-order change.
- No upstream-ready branch, issue, PR, or production default decision.
- Controlled graphs do not establish the main external performance claim.

## Invariants

- One canonical `NodeState` identity per `(nodeId, stateId)` key.
- Canonical registration precedes frontier-buffer visibility.
- Canonical ownership outlives frontier retirement and is shared by forward and
  backward search.
- State IDs are in `[0, |Q|)` and remain sequentially indexable when dense.
- Promotion is one-way, preserves every existing identity, and cannot expose a
  partially copied bucket.
- Closing the enclosing scoped tracker releases all repository structural
  accounting; `NodeState` memory is not double-counted.
- Path multiplicity, ordering, cancellation, target saturation, and traversal
  semantics remain unchanged.

## First prototype

```text
HeapTrackingLongObjectHashMap<StateBucket>
  nodeId -> StateBucket

StateBucket
  sparse: up to two inline (stateId, NodeState) slots
  dense:  HeapTrackingArrayList<NodeState>[|Q|]
```

The sparse capacity is an experiment parameter with only two initial values:

- `1`: promote when inserting the second distinct state;
- `2`: promote when inserting the third distinct state.

Inline slots avoid child-array allocation for the tiny regime. A bucket object
is charged once with `HeapEstimator.shallowSizeOfInstance`. Dense storage uses
the existing heap-tracked list. `NodeState` objects are references only and are
already accounted by their owner.

No capacity above two and no middle map will be implemented unless final
occupancy evidence shows a material unresolved middle regime.

## Occupancy contract

For each canonical data-node bucket `v`:

```text
k_v   = number of distinct canonical state IDs retained for v
rho_v = k_v / |Q|
```

Research-only, property-gated hooks record aggregate final occupancy histograms,
NFA state count, total distinct nodes/product states, promotion count, and final
sparse/dense bucket counts. They must not emit per-lookup rows. Instrumented
runs establish occupancy only and are excluded from final timing/memory claims.
Final candidate measurements run with telemetry disabled.

## Crossover protocol

1. Run identical controlled and real manifests with sparse capacities 1 and 2.
2. Record final `k_v`/`rho_v` distributions and promotion counts.
3. Measure direct lookup and insertion/promotion cost in a warmed controlled
   harness across hit/miss and occupancy cases.
4. Run the 92 MiB roadNet-PA gate before broader timing for each viable form.
5. Compare the surviving form with B0, C1, and C2 using the frozen server
   harness and exact runtime-artifact hashes.
6. Select capacity 2 only if its avoided promotions/memory and lookup results
   are Pareto-better than capacity 1; otherwise retain capacity 1.
7. If neither form passes B0's memory boundary, reject C3 without adding a
   middle map. A middle map cannot repair a low-occupancy boundary failure.

## Validation gates

- Focused promotion identity, duplicate rejection, node isolation, miss, and
  sparse/dense cleanup tests: `PASS`.
- Isolated frontier-retirement memory decrease with canonical identity retained:
  `PASS` for forward and bidirectional paths.
- Early close and cancellation leave tracked memory exactly zero: `PASS`.
- Existing focused and generated differential PPBFS suites: `PASS`.
- Spotless: `PASS`.
- 92 MiB fixed-limit query: candidate must pass because B0 passes.
- Tracked heap: neutral/better than B0 preferred, never worse than the existing
  predeclared shallow/deep limits.
- Performance: retain at least 90-95% of C1 on relevant workloads and no
  repeatable common-case regression above 5%.
- Allocation/GC and multi-topology evidence remain required before graduation.

## Alternatives and rejection rules

- C1 dense-only: rejected at the 92 MiB gate.
- C2 state-major maps: rejected as final at a 94 MiB boundary and by screening
  performance.
- Always-sparse linear list: rejected for unbounded lookup cost as `k_v` grows.
- Three-tier sparse/map/dense: deferred; complexity is unjustified without a
  measured middle regime.
- If C3 does not pass the near-limit gate, retain no direct repository candidate
  from this experiment and report the optimization as unsupported under current
  constraints.

## Compatibility, failure, and rollback

The experiment changes only the private canonical repository inside
`FoundNodes`. It has no persistence, wire, API, security, privacy, recovery, or
generated-source impact. Failure rollback is removal of C3 commits or selection
of B0; no data migration is involved.

## Graduation

Experiment success does not authorize an upstream PR. A clean `contrib/*`
branch must start from current upstream and contain only the selected production
bucket, upstream-worthy tests, and maintainable documentation/benchmark changes.
Research telemetry, reports, local paths, and candidate-selection machinery are
excluded from that branch.
