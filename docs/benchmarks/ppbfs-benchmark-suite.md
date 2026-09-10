# PPBFS benchmark suite

## Status

**Reusable; direct-state-index family qualified with a disclosed memory trade-off, with a
deferred-indexing follow-up active.** Shared harnesses and evidence exist on
[`research/ppbfs-lab`](https://github.com/iancuuandrei/neo4j-contributions/tree/research/ppbfs-lab).
Multi-topology results exist for the C1 and C4 candidates (see the case studies);
the strict identical-memory gate remains a disclosed trade-off, not a pass criterion
met by the candidates.

## Philosophy

Controlled graphs explain algorithmic scaling; real datasets carry performance
claims. Every comparison uses one exact upstream baseline and that same baseline
plus one isolated candidate. Instrumented causal builds are separated from timing
builds.

## Current baseline discipline

PPBFS campaigns pin one exact baseline per study. The state-index campaigns used:

```text
repository: neo4j/neo4j
upstream branch: 2026.07
baseline SHA: f213380f812b820a1b312e2ea52cb3d8f1931ccc
primary JDK: Eclipse Temurin 21
```

Later campaigns (WALK bookkeeping, P10, #13937) use upstream `2026.08` at
`736cad02a36bb4a0d32c1064f44768339c814269`; each case study records its own baseline.

Run metadata records baseline and variant SHAs, JDK/JVM, OS/CPU/RAM, configuration,
dataset/query hashes, warmup, repetitions, timestamp, and exclusions.

## Datasets

| Dataset | Purpose | Current preparation status |
| --- | --- | --- |
| SNAP roadNet-CA | Primary high-diameter stress graph | Downloaded, imported, benchmarked (state-index + C4 campaigns) |
| SNAP roadNet-PA | Independent high-diameter replication | Downloaded, imported, deterministic pairs persisted, benchmarked (state-index + C4 campaigns) |
| SNAP web-Stanford | Directed reconvergent topology | Downloaded, imported, benchmarked (WALK bookkeeping qualification) |
| SNAP as-Skitter | Low-diameter, high-fan-out negative control | Downloaded, benchmarked (WALK bookkeeping qualification) |
| LDBC SNB SF10 | Standardized property-graph/common-case regression | Not prepared |

Checksums and inclusion reasons live in the research branch dataset catalog.
Large archives and imported stores are not tracked.

## Controlled complexity tests

Only chain, reconvergent diamonds, and layered DAG are retained as the core
controlled family. They test whether measured history probes and latency follow
the expected curve; they are not headline evidence.

## Query and operator verification

Every Cypher workload is first captured with `EXPLAIN` or `PROFILE`. Direct PPBFS
evidence must execute `StatefulShortestPath(All)` or
`StatefulShortestPath(Into)`. Queries planned to `ShortestPath`,
`FindShortestPaths`, or another implementation are excluded from direct speedup
claims and retained only as general regressions where useful.

The current road-network query uses `SHORTEST 2` and was captured as
`StatefulShortestPath(Into, Trail)`.

## Execution protocol

1. Build baseline and candidate from the same upstream SHA.
2. Bind distributions/JARs to hashes and capture configuration.
3. Use identical imported data and deterministic query manifests.
4. Warm JVM and page cache separately from cold-cache runs.
5. Alternate/randomize variant order and query order.
6. Use several JVM forks and retain every raw sample.
7. Compare exact result sets before timing interpretation.
8. Report per-query distributions, medians, meaningful tails, geometric means, confidence intervals, and fork variance.
9. Collect JFR/internal counts to confirm that time moved out of the targeted mechanism.
10. Report memory, allocation, GC, near-limit behavior, regressions, and exclusions.

No outlier is removed without a pre-established documented rule.

## Acceptance gates

- No result-semantic or exception-semantic difference.
- No broad common-case regression; preferred geometric-mean regression ≤2%.
- Representative real benefit meeting the predeclared threshold.
- Causal metrics consistent with the proposed mechanism.
- Preferred tracked-heap increase ≤5% on common/shallow and ≤10% on benefited deep cases.
- No unexpected baseline-versus-patch memory-limit failure.
- Multiple JVM forks, confidence intervals, reproducibility, and complete raw data.

## Result layout

Small canonical CSV/JSON and reports are versioned under the shared research
branch. Large raw runs, JFR recordings, stores, and distributions live in the
external artifact hierarchy and are referenced by metadata/checksum rather than
copied into portfolio documents.

The direct-state-index case study records the measured results with the memory-gate
trade-off explicitly disclosed; the deferred-indexing follow-up extends the same
protocol.
