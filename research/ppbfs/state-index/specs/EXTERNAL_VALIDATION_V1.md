# PPBFS C1 external-validation protocol v1

## Status and authority

Status: accepted for experiment on 2026-09-05. This protocol governs the
falsification-oriented P1-P5 continuation of the direct product-state lookup
study. The user goal is authoritative; `docs/standards/BENCHMARKS.md` and
`docs/standards/EVIDENCE.md` define evidence handling.

Repository: `neo4j/neo4j`. Research branch: `research/ppbfs-lab`. Exact
upstream base: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`. B0 timing build:
`1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381`. Frozen C1 experiment:
`caf33be9f5de2d8d7f4a291abd466cf808aaf801`. Clean candidate start:
`0a8ef51868e9c8f52dfd49c516d0a14a0295b2c6`.

## Objective and non-goals

Try to falsify the claim that C1 is generally useful for PPBFS rather than a
deep homogeneous-road special case. Do not redesign C1, implement C4, select a
new candidate, alter production code during dataset qualification, open an
upstream issue/PR, or rerun completed SNAP evidence without a demonstrated
harness need.

## Fixed comparison and correctness gate

Every formal comparison is the same B0 plus exactly C1. Before timing, each
case must have a frozen, hashed manifest and `EXPLAIN` or `PROFILE` evidence.
Classify it as `PPBFS-DIRECT-EVIDENCE`, `NON-PPBFS-REGRESSION-CONTROL`, or
`EXCLUDED`; never aggregate categories. B0 and C1 must match row count,
multiplicity, path lengths/mode, endpoints, errors, and, where meaningful,
relationship-type sequence and predicates. A difference stops that dataset.

## Predeclared P1-P5 hypotheses and selection

1. **FinBench SF1:** unknown benefit if an official path query naturally uses
   PPBFS. Pin the official transaction implementation at
   `5a4f9d7b7bc5daf48370fd9a4640684f67712428`. Qualify every official Neo4j
   complex read without semantic rewriting. Current source inspection predicts
   no direct evidence: bounded `*` expands should use variable-length expansion
   and TCR3's `shortestPath` should use its separate implementation. Planner
   evidence, not this prediction, decides. Do not download/import SF1 if plan
   qualification proves all official path queries non-PPBFS.
2. **Hetionet v1.0:** positive or neutral is expected; its native 11 node and 24
   relationship types test richer automata. Pin source repository commit
   `8a6cc0c5604d0e2908786e4de38af67b8e46ee4a`. Derive H1-H5 from the published
   metagraph, freeze deterministic semantic queries before timing, and preserve
   native directions/types. Do not collapse types.
3. **Controlled RPQ:** speedup should track B0 historical-probe burden more
   closely than graph size. The modern `RoanH/gMark` does not generate graph
   instances; use the original `gbagan/gmark` reference where practical, or a
   documented faithful deterministic RPQ generator. Sweep depth 5-500 and NFA
   state count 1-64 across sparse-to-dense measured occupancy, fanout,
   selectivity, reconvergence, and Into/All forms. Retain the entire frozen
   matrix, including losses.
4. **cit-Patents:** expect a small-to-moderate gain on the directed DAG-like
   graph, not road-like multi-x behavior. Select deterministic reachable pairs
   by distance regime before timing; retain unreachable attempts separately.
5. **LiveJournal:** expect neutral performance on a large low-diameter social
   graph. A reproducible slowdown over 5% is investigated and reported; over
   10% is serious evidence.

## Measurement contract

Formal important aggregates use at least five independent paired fresh-JVM
forks, complete warmup, multiple repetitions, the within-fork median, paired
log speedups, geometric means, and two-sided 95% Student-t intervals. Variant
order is deterministically seeded. `PROFILE` is only for operator, result,
DB-hit, and tracked-memory evidence; headline latency is unprofiled.

For heterogeneous and controlled RPQ cases record NFA states `Q`, unique
product states `U`, unique graph nodes `N`, occupancy `rho = U/(N*Q)`, mean,
median, p95, and maximum active states per node, depth, lookup count, historical
probes, and accepted product-state encounters. Representative regimes receive
tracked-memory and JFR allocation/GC measurements; not every case does.

## Provenance and storage

For every external dataset record source, version/date, license, URL, archive
SHA-256, converted hash, counts, relation types, converter SHA, seed, manifest
SHA, import command, and store provenance. Bulky or raw artifacts are
append-only under `D:\dev\neo4j-research`; Git contains only scripts,
manifests, checksums, small summaries, and reports. Failed, excluded, neutral,
and negative runs are never overwritten or hidden.

## Stop and decision rule

Stop after P1-P5 answer the principal property-graph heterogeneity, automaton
occupancy, DAG, low-diameter, and standardized-workload questions. Optional
datasets require a named remaining uncertainty. The result must be one of the
four classifications allowed by the controlling goal; dataset count is not
treated as independent regime coverage.
