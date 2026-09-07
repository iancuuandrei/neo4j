# P2 baseline bucket-instrumentation specification

Status: accepted for experiment

Date: 2026-09-07

Owner: local Neo4j contribution workspace

Repository: `iancuuandrei/neo4j`

Branch: `research/ppbfs-p2-baseline-instrumentation`

Authoritative causal base: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`

Research ancestry: `benchmark/ppbfs-external-observability` at
`3b0413e37db3fd08bbf32aa4b7b472780c9016a4`

Current-source cross-check: upstream `2026.08` at
`736cad02a36bb4a0d32c1064f44768339c814269`

## Decision boundary

This specification authorizes baseline observability only. It does not authorize
a sparse, hybrid, hash, bitmap, segmented, role-specialized, or upstream
candidate implementation.

The first decision is whether the allocation and traversal shape identified by
the `FoundNodes` source comment is material in qualified PPBFS workloads. A
representation candidate may be implemented only after the baseline evidence
establishes the relevant occupancy and operation regimes.

## Source-confirmed problem

`FoundNodes` stores each level-local data-node entry as a
`HeapTrackingArrayList<NodeState>` of exact logical size `nfaStateCount`. State
IDs index the list directly. `BFSExpander` scans the entire list to construct a
compact list of active NFA states, then performs direct state-ID lookups while
materializing relationship expansions.

The source explicitly records that this layout may over-allocate and create
sparse arrays for some NFAs. The same layout remains present in upstream
`2026.08`.

The prior occupancy hook is not a sufficient P2 oracle. It aggregates distinct
states by graph node across the whole search. P2 concerns each concrete
level-owned bucket. The same graph node may own separate buckets in the buffer,
frontiers, and multiple history levels, so global node occupancy can materially
overstate per-bucket occupancy.

## Goals

1. Measure exact final occupancy for every concrete level-owned state bucket.
2. Measure index-range shape: minimum and maximum state ID, span, contiguous
   runs, low-ID contiguity, state-ID frequency, and occupied 8/16/32-state
   chunks.
3. Measure the bucket operation mix:
   - writes and duplicate writes;
   - map probes and probes which reach a bucket;
   - bucket lookups, split by buffer, forward frontier, backward frontier,
     history, and `BFSExpander` direct lookup;
   - full bucket iterations, active states emitted, and null slots scanned.
4. Measure insertion ordering and bucket lifecycle without emitting one row per
   hot-path operation.
5. Emit one append-only JSONL summary per `FoundNodes` execution.
6. Keep instrumentation disabled by default and exclude the instrumented build
   from latency, allocation, GC, tracked-memory, and memory-limit claims.
7. Preserve P1 branches and evidence unchanged.

## Non-goals

- No P2 production representation.
- No P1 implementation or lifecycle change.
- No planner, Cypher semantics, cursor, transition-dispatch, or frontier
  selection change.
- No end-to-end performance conclusion from an instrumentation run.
- No upstream issue, PR, discussion, or contribution branch.
- No per-lookup file writes.
- No resident-memory claim from query-memory boundaries.

## Measurement unit

A bucket is one concrete `HeapTrackingArrayList<NodeState>` allocated for one
`nodeId` in one `frontierBuffer`. Its identity follows that list as the buffer
is committed to a frontier and later retained in history.

For bucket `b`:

```text
S_b = nfaStateCount
k_b = number of non-null state slots after buffer commit
rho_b = k_b / S_b
span_b = maxStateId_b - minStateId_b + 1
```

Occupancy is finalized at buffer commit because no subsequent mutation of that
level bucket is permitted by the baseline lifecycle.

## Architecture

Instrumentation is query-local to one `FoundNodes` instance. A property-gated
helper records aggregate counters and histograms. It may retain only the final
`k_b` needed to attribute later history and expansion operations. It must not
retain every active state ID after the commit-time shape scan.

Level-map identity, rather than per-bucket lifecycle objects, records commit and
retirement depth because all buckets in a level map share that lifecycle.

`FoundNodes` records:

- bucket creation and write events;
- level-map probes and bucket lookups;
- buffer commit and frontier retirement.

`BFSExpander` records:

- the one full scan of each expanded frontier bucket;
- direct state-ID lookups used to recover the source/target `NodeState`.

The existing aggregate `PPBFSHooks` instrumentation remains separate. P2 output
uses the property:

```text
ppbfs.p2.bucket-metrics.output=<absolute JSONL path>
```

An optional run label uses:

```text
ppbfs.p2.bucket-metrics.run-id=<manifest/fork identifier>
```

## Required summary fields

Each JSONL object must include:

- schema version, causal baseline SHA, query sequence, run ID, search mode, and
  NFA state count;
- created, committed, uncommitted, and unknown-operation counts;
- exact dense slots, active slots, and unused slots;
- occupancy, span, run-count, chunk-count, minimum-ID, maximum-ID, and active
  state-ID histograms;
- writes, duplicate writes, and insertion-order counters attributed to final
  occupancy;
- map probes, bucket-present probes, bucket lookups, and lookup hits by role and
  final occupancy;
- iterations by final occupancy, slots scanned, active states emitted, and null
  slots scanned;
- global-depth and direction-depth frontier-lifetime histograms;
- still-open level counts when execution stops before normal exhaustion.

Histograms must contain counts, not percentages. Derived reports compute
percentages and weighted quantiles from the raw counts.

## Invariants

- Instrumentation does not create, replace, remove, reorder, or retain a
  `NodeState` beyond the baseline ownership lifecycle.
- `FoundNodes.get` preserves its exact probe order and return semantics.
- Frontier iteration order and `statesList` order are unchanged.
- Bidirectional frontiers remain separate and continue to share one buffer.
- Memory tracking of production structures is unchanged.
- Telemetry data structures are ordinary untracked research overhead and are
  never used as evidence about candidate memory behavior.
- A configured output failure is explicit; it is not silently converted into a
  successful evidence row.
- Closing an exhausted, saturated, cancelled, or partially consumed search
  emits at most one summary.

## Concurrency and failure behavior

All mutable counters belong to one `FoundNodes` instance. Concurrent queries do
not share counters. Appending to a common output file is synchronized within
the JVM. Cross-process experiments must use separate output files per process or
fork.

When telemetry is disabled, no output file is opened. When enabled, an invalid
path or failed append causes an explicit unchecked I/O failure. Such a run is
`FAIL`, not a zero-valued observation.

## Resource boundary

Instrumentation can add material heap and CPU overhead, especially because
history buckets remain reachable for the whole baseline search. Instrumented
runs therefore use generous memory limits and are qualified only for occupancy
and operation-count evidence. Final B0/candidate timing, JFR allocation, GC,
and memory-boundary comparisons use instrumentation-free builds.

## Correctness oracle

The instrumented branch must preserve:

- focused PPBFS test results;
- full `community/cypher/runtime-util` test results;
- exact result hashes/lengths for every retained external workload;
- the expected `StatefulShortestPath` operator or direct PPBFS harness path.

A measurement row is rejected if result equivalence or operator qualification
fails.

## Validation commands

Run from a clean worktree with the repository-required JDK and Maven cache:

```powershell
mvn -pl community/cypher/runtime-util -Dtest=P2StateBucketTelemetryTest test
mvn -pl community/cypher/runtime-util -DsequentialTests test
mvn -pl community/cypher/runtime-util spotless:check
mvn -pl packaging/standalone/standalone-community -am -DskipTests package
```

Record exact executed, passed, failed, errored, and skipped counts. At the time
this specification was committed, these commands were `NOT RUN` in the current
assistant environment because the repository build toolchain and local dataset
store are unavailable there.

## Acceptance gates

Baseline instrumentation graduates to workload execution only if:

1. focused synthetic tests prove that two separate level buckets for the same
   graph node remain separate observations;
2. sparse, dense, non-contiguous, duplicate-write, history-lookup,
   bidirectional, partial-close, and normal-close cases produce expected
   counters;
3. focused and full runtime-util suites pass;
4. Spotless passes;
5. output is deterministic for deterministic controlled fixtures apart from
   explicit run/query identifiers;
6. telemetry remains off without the property;
7. no timing or memory claim is made from the telemetry build.

## Alternatives rejected for this phase

- Reusing global `(nodeId -> BitSet)` occupancy: wrong measurement unit.
- Emitting one event per lookup to disk: prohibitive hot-path I/O and excessive
  evidence volume.
- Scanning a bucket on every history lookup: changes the operation being
  measured and can make deep workloads unusable.
- Implementing an adaptive bucket before occupancy characterization: violates
  the predeclared causal order.
- Treating the prior C3 memory failure as a P2 rejection: C3 added a canonical
  repository while retaining dense frontier/history buckets; P2 asks whether
  those existing buckets should be replaced.

## Compatibility, security, privacy, migration, rollback

There is no persistence, wire, public API, authentication, authorization,
security, privacy, or data-migration change. Output contains aggregate numeric
algorithm metrics and an operator-supplied run label; manifests must not place
secrets or personal data in that label.

Rollback is deletion of this research branch. No production database migration
or cleanup is required.

## Human and maintainer decisions still required

No maintainer decision is requested at this phase. After qualified baseline
measurements, a new versioned specification must select the candidate shortlist
and predeclare candidate gates before implementation.