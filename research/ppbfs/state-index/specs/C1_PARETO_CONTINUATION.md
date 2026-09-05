# C1 Pareto continuation protocol

## Status and authority

- Status: accepted for continuation experiment
- Protocol version: 1
- Date frozen: 2026-09-05 Europe/Bucharest
- Repository: `neo4j/neo4j`
- Research branch: `research/ppbfs-lab`
- Exact upstream base: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`
- B0 runtime source: `1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381`
- C1 runtime source: `caf33be9f5de2d8d7f4a291abd466cf808aaf801`
- B0 runtime-util JAR SHA-256: `CD1C9AA5348807F4F576C2E4A07F261720EC421125C6F7515DF5039CE5713C4D`
- C1 runtime-util JAR SHA-256: `6B95EA3B02CBB97719E0FCEFE915D4FC30A394ECBEB06A9742108AAA48AA6C47`

The original acceptance contract and its evidence remain valid. C1 formally
failed the predeclared identical-memory-limit compatibility gate because B0
passed the roadNet-PA d250 query at 92 MiB and C1 did not. This continuation
treats that result as a measured compatibility trade-off, not by itself as a
complete production-policy decision.

## Question and hypothesis

The decision question is whether C1's direct canonical `(nodeId,stateId)`
repository provides a large and sufficiently general StatefulShortestPath
benefit with bounded memory, allocation, GC, and common-case costs to warrant
maintainer discussion.

The causal hypothesis is that B0 history probing grows with search depth, while
C1 makes lookup expected-constant-time. The predicted benefit is strongest on
deep road networks, possibly positive or neutral on directed web traversal, and
small on low-diameter as-Skitter. Extra C1 bookkeeping may be visible in tracked
memory and allocation even where scan removal has little latency value.

## Scope and non-goals

This protocol compares only B0 and C1 for new measurements. Existing C2 and C3
results remain preserved comparators. It does not implement C4, change query
semantics, alter Neo4j production code, overwrite retained evidence, or post an
upstream issue or pull request. LDBC SNB SF10 is attempted only if a prepared
store and naturally occurring PPBFS workload can be established without
disproportionate setup.

## Workloads and deterministic selection

- roadNet-PA: retain prior pairs and add decision-value replication where
  necessary; representative memory points are distances 100, 250, 500, 772.
- roadNet-CA: use the downloaded SNAP graph and deterministic BFS selection.
  Include shallow through 500+ distances when reachable, with more than one
  target per important bucket where runtime permits.
- web-Stanford: preserve raw edge direction; survey deterministic seeded source
  candidates, then select shallow, moderate, and deepest reproducibly reachable
  pairs without filtering on B0/C1 timing.
- as-Skitter: treat SNAP edges as undirected by deterministically materializing
  both relationship directions at import; select shallow through deepest
  reachable pairs without filtering on timing.

Every retained pair must return identical B0/C1 path-length multisets and its
PROFILE must contain `StatefulShortestPath`. Any bypass, timeout, semantic
difference, or failed run is retained and marked `EXCLUDED` or `FAIL`.

## Timing protocol

Timing uses the existing unprofiled HTTP runner, fresh Neo4j JVMs, one complete
manifest warmup, seeded variant order within each paired fork, the same imported
store content and configuration per variant, median latency per fork, paired log
ratios, geometric means, and two-sided 95% Student-t intervals.

Major topology aggregates target at least five independent paired forks.
roadNet-CA deep replication may use seven forks when runtime remains practical.
Use 3-10 within-fork repetitions according to query cost; repetitions within a
JVM are never counted as independent forks. Profile execution is used only for
operator, result, DB-hit, and tracked-memory evidence, never headline timing.

Broad common-case interpretation is predeclared as contextual guidance:
geometric-mean regression below 2% is likely neutral, 2-5% is a minor trade-off,
above 5% is serious, and above 10% is likely disqualifying absent exceptional
benefit. Confidence intervals and topology context take precedence over a point
estimate alone.

## Memory, scaling, allocation, and GC

The prior 92 MiB result is immutable. Selected PA and CA queries receive
coarse-to-fine fixed-limit probes sufficient to identify the minimum observed
passing configured limit; this is an observed threshold, not exact consumed
bytes. Report absolute and relative threshold differences.

PROFILE and research instrumentation collect available reached-work measures:
search depth, lookup attempts, historical probes, product-state/bucket counts,
allocated state slots, operator memory, and query high-water memory. Metrics not
available without a new intrusive hook remain `NOT RUN` rather than inferred.

JFR allocation/GC runs cover one shallow, one d250-like, and one deep PA/CA case
where runtime is practical. Compare total sampled/allocation evidence, GC counts
and pauses, major sites, direct-repository structures, and map/array resizing.
JFR is explanatory evidence and does not replace tracked-memory limits.

## Artifacts and environment

All downloads, prepared CSV, stores, distributions, logs, JFRs, and raw runs
remain append-only under `D:/dev/neo4j-research/artifacts/ppbfs/`. Git contains
only scripts, frozen manifests, small summaries, and reports. New run directories
use a new versioned name; failed or partial directories are never overwritten.
The benchmark remains on the recorded Windows 11, Ryzen 9 9955HX, 31.2 GiB RAM,
Temurin 21, 4 GiB heap, 8 GiB page-cache environment unless metadata says
otherwise.

## Classification and stop condition

The final table compares B0/C1/C2/C3 on latency, generality, lookup complexity,
tracked memory, minimum passing limit, allocation/GC, correctness, complexity,
and maintainability. Use only the four user-defined verdicts. A narrow 92-to-93
MiB shift alone cannot produce `NO-GO`; a true no-go requires substantial broad
performance, scaling-memory, allocation/GC, or correctness evidence.

C4 is permitted only if C1 is compelling across topologies, memory scaling is
the only blocker, structural evidence supports a flat primitive table, and the
prototype will not derail completion. Otherwise C4 remains `NOT RUN`.

If and only if the classification is `STRONG GO` or
`GO — MAINTAINER DISCUSSION WARRANTED`, prepare a clean minimal C1 branch from
the exact base. Never merge research history into it and never post upstream
without a separate human instruction.
