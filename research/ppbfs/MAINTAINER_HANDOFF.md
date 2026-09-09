# PPBFS P1/P2 maintainer handoff (unified, current as of 2026-09-09)

Prior C4-only handoff superseded (history preserved in Git). Scope: what to review, what was
measured, what decision is needed. Personal repository: `iancuuandrei/neo4j-contributions`.
Issue: `neo4j/neo4j#13966` (open, assigned `alexfoxgill`; maintainer benchmarking pending).

## 1. Problem

`FoundNodes.get(nodeId, stateId)` checks active structures, then scans every retired BFS level
newest-first, so canonical product-state lookup scales with retained history depth (measured:
8,382,465 historical probes for 4,097 lookups on a controlled depth-4096 chain; ~50.9% of B0
samples on a representative deep road query). Separately, per-node state buckets allocate and
scan `O(S)` (`newEmptyArrayList(nfaStateCount)` per visited node) even when only `k << S`
states are active (measured: 99.2% null scans at S=255).

## 2. P1 C1 — always-on direct canonical index

`contrib/ppbfs-direct-state-index` (`0a8ef5…`, frozen): long-lived canonical node→state
repository; frontiers released on retire. Simplest implementation; strong deep-query speedups
(C1-class ≈4.1–4.4× roadNet d250+); small allocation/memory tax (PA d250 limit 93 vs 92 MiB).
Draft PR #2 (open, personal fork).

## 3. P1 C4 — deferred retired-state index (H=8)

`contrib/ppbfs-deferred-state-index` (`7411ac…`, frozen): baseline probing for 8 history
levels, then ownership-transfer/merge of retiring frontier buckets into one direct index.
C1-class deep speed (4.370x [3.838, 4.976] PA; 4.098x [3.231, 5.197] CA), better
shallow/allocation balance (H3 1.003x equivalent; below C1 allocation in every qualified JFR),
more lifecycle complexity. Draft PR #3 (open, personal fork). Full detail in the prior handoff
sections preserved below (activation, ownership, H=8 rationale, convention check).

## 4. P2 follow-up — fixed sorted-vector state bucket

`StateBucket`: only active states, ascending state-ID order, linear lookup with early exit,
append fast-path, exact memory tracking. No adaptive promotion, hash, range, or specialization
(each rejected with evidence). Standalone: +16.6% [5.4, 28.1] (S=255), +5.1% (S=507);
tracked memory −2.5–4×; falsification survived through k=256 at g/i≈1150 (kill shot 0.996
[0.967, 1.022]); small-S/dense/tiny/bidi/high-fanout neutral. Not proposed standalone.
Evidence: `p2/FINAL_REPORT.md`, `p2/FINAL_QUALIFICATION.md`.

## 5. P1+P2 interaction

- C1 → C1+P2 (`research/ppbfs-p2-c1-interaction`): −2.6–4.2× bucket memory; chain255 +51%
  [1.369, 1.624]; roadNet-PA deep preserved (0.973; 772-pair 1.043; results matched).
- C4 → C4+P2 (`research/ppbfs-p2-c4-interaction`): same memory shape; chain255 +50%;
  roadNet-772 pooled-6 ≈0.949 (bounded, no destruction); retired merge stays
  occupied-proportional (audit passed, no redesign).
- All oracles identical across B0/V/C1/C1P2/C4/C4P2; server result sets matched everywhere.

## 6. Strongest measured regression (disclosed)

~6% C1+P2 (~3% C4+P2) on lookup-saturated tiny-NFA canonical H3 traffic (10 forks each,
order-balanced pooled: 0.9389 / 0.9701; k≈3–4, so promotion cannot fix that corner).
Nothing is described as regression-free.

## 7. Branch map

```text
contrib/ppbfs-direct-state-index      0a8ef5…  C1 clean candidate (frozen)
contrib/ppbfs-deferred-state-index    7411ac…  C4 clean candidate (frozen)
research/ppbfs-p2-sorted-vector       a007e43… fixed-V research (frozen)
research/ppbfs-p2-c1-interaction      7743248… C1+P2 research (frozen)
research/ppbfs-p2-c4-interaction      9c8624c… C4+P2 research (frozen)
research/ppbfs-p2-final-qualification 8797dcb… authoritative P2 evidence (frozen)
research/ppbfs-lab                    THIS BRANCH — canonical docs + P2 archive (p2/)
archive/ppbfs-lab-pre-p1-p2-status-sync-2026-09-09   pre-sync P1 state (immutable)
```

## 8. Decision needed from Neo4j

> C1 remains the simpler P1 design; C4 trades additional lifecycle complexity for a better
> common-case resource balance. P2 now removes much of the state-bucket memory tax from either
> design but introduces a measured ~3–6% lookup-saturated tiny-NFA corner cost. The research
> side is complete; the remaining question is which P1 architecture, if either, Neo4j's
> internal benchmarks prefer.

## 9. P7 follow-up — WALK post-saturation bookkeeping

After a target saturates, `PathTracer` still enumerates every represented path
combination to establish bookkeeping. P7 memoizes that residual work over compact
`(NodeState, sourceLength)` states — WALK-only (TRAIL/ACYCLIC validity is
history-dependent), unbound-only (bound searches terminate globally on
saturation). Measured on Walk repeated diamonds d=6: 948 baseline pushes vs 200
P7 pushes (4.74x); `PGPathPropagatingBFSP7Test` proves identical rows, schedules,
target-signpost registrations and prunes, with bound/Trail zero-delta controls.
Real-world qualification (STRONG REAL-WORLD CASE): LJ 56/68 improved with 93.5%
of post-saturation pushes removed; web-Stanford 93.4% removed, 6–7x timing,
timeouts 6→1; as-Skitter 96.8% removed; Hetionet neutral; real Cypher end-to-end
(LJ 2022306 LIMIT 75: baseline >90s timeout vs 53 rows in 52ms); JFR PPBFS
47%→11%. Branch: `contrib/ppbfs-walk-post-saturation-bookkeeping` (base
`736cad02a36bb4a0d32c1064f44768339c814269`, upstream/2026.08). Upstream issue
`neo4j/neo4j#13968` open for architecture feedback; no PR. Evidence:
`p7/FINAL_REPORT.md`, `p7/FINAL_QUALIFICATION.md`, `p7/MATERIALITY_REPORT.md`.

---

## Prior C4-only handoff sections (preserved)

### C4 problem restatement

`FoundNodes.get(nodeId, stateId)` checks active structures and then scans every retired BFS
level (see §1).

### C1 vs C4 (as previously handed off)

| | C1 | C4 |
| --- | --- | --- |
| direct lookup | always on | after eight retired levels |
| canonical storage | separate bucket structure | ownership transfer/coalescing |
| implementation | simpler | additional retirement lifecycle |
| measured deep speed | strongest | C1-class |
| measured allocation | above B0 | below C1 in every qualified JFR; still above B0 on deep roads |

### C4 performance (prior)

roadNet-PA d250+ B0/C4 4.370x [3.838, 4.976]; roadNet-CA d250+ 4.098x [3.231, 5.197];
Hetionet H3 10 forks 1.003x [0.973, 1.033]; LiveJournal C1/C4 1.189x [1.004, 1.408].
Clean extraction reproduced 7.589x (PA d772) / 7.267x (CA d800) in bounded confirmation.

### C4 memory (prior)

C4 below C1 in every qualified JFR; ≈B0-level on external controls; +3.8–5.7% vs B0 on deep
roads; PA d250 limits B0 92 / C1 93 / C4 94 MiB; CA d250 B0 102 / C4 104 MiB. Workload-dependent
tracked observations, not general claims.

### C4 correctness/why-H=8/conventions (prior)

Clean patch tests (activation, identity, transfer, merge, bidirectional, empty-retirement,
cleanup): 114 focused + full 529-test suite green (five skips); H=8 a conservative point in a
broad measured region (`c4/C4_THRESHOLD_SENSITIVITY.md`); existing tracked collections and
scoped-memory lifecycle; no public API/store changes.
