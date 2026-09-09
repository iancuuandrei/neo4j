# PPBFS current status (authoritative human-readable map)

Updated 2026-09-09. Historical experiment reports under `research/ppbfs/` remain accurate for
their stage; this document (with `MAINTAINER_HANDOFF.md`) is the current project status.
Supersedes earlier top-level verdicts such as “C1 rejected / B0 retained”, which described an
earlier research stage (preserved in `archive/ppbfs-lab-pre-p1-p2-status-sync-2026-09-09`).

Personal repository canonical name: `iancuuandrei/neo4j-contributions`
(renamed from `iancuuandrei/neo4j`; GitHub redirects the old name).

## Upstream

```text
historical causal baseline:  f213380f812b820a1b312e2ea52cb3d8f1931ccc (Neo4j 2026.07)
current upstream cross-check: 736cad02a36bb4a0d32c1064f44768339c814269 (upstream/2026.08)
upstream default branch:      2026.07 (tip == f213380f… as of 2026-09-09 fetch)
latest relevant release:      upstream/2026.08; 2026.09 does not exist (verified 2026-09-09T12:05Z)
```

All P1/P2 performance claims stay tied to the frozen `f213…` baseline unless a report says
otherwise. Verified 2026-09-09: upstream `FoundNodes` still does history-depth-linear lookup
and dense per-node `Object[S]` buckets — no independent P1/P2 equivalent exists upstream.

## P1 problem

`FoundNodes.get(nodeId, stateId)` probes the buffer, the frontier(s), then every retired BFS
level newest-first, so canonical product-state lookup scales with retained history depth
(measured: 8,382,465 historical probes for 4,097 lookups on a controlled depth-4096 chain).

## P1 C1 — always-on direct canonical index

`contrib/ppbfs-direct-state-index` (`0a8ef5…`, frozen): a long-lived canonical
node→state repository plus released frontiers. Simplest implementation; strong deep-query
speedups (C1-class ≈4.1–4.4× on roadNet d250+, statistically consistent with C4); small
allocation/memory tax (e.g. PA d250 minimum observed limit 93 vs 92 MiB for B0).
Draft: personal-fork PR #2 (open). Details: `state-index/` reports.

## P1 C4 — deferred retired-state index

`contrib/ppbfs-deferred-state-index` (`7411ac…`, frozen): keep baseline probing for H=8
history levels, then transfer/merge retiring frontier buckets into one direct retired index.
C1-class deep performance (4.370x [3.838, 4.976] PA, 4.098x [3.231, 5.197] CA), better
shallow/allocation balance (H3 1.003x equivalent; below C1 allocation in every qualified JFR),
more lifecycle complexity (transfer/merge/close ownership). Draft: personal-fork PR #3 (open).

## Current P1 maintainer state (verified 2026-09-09)

`neo4j/neo4j#13966` is open, assigned to `alexfoxgill`. Maintainer's last message (2026-09-07):
C1/C4 will be run through internal benchmarking tools. No subsequent maintainer verdict.

## P2 problem

Per-node NFA-state buckets allocate and scan `O(S)` (`newEmptyArrayList(nfaStateCount)` per
visited node; one full `S`-slot scan per expanded bucket) even when only `k << S` states are
active (measured: 99.2% null scans at S=255; steady-state k≈2).

## P2 final architecture (verdict B — useful mainly with P1)

Fixed sorted-vector `StateBucket` (ascending state-ID order, linear lookup with early exit,
append fast-path, exact memory tracking). No adaptive promotion, no hash, no range layer,
no role specialization — each rejected with measured evidence (`p2/CANDIDATE_DESIGNS.md`).
Branches: `research/ppbfs-p2-sorted-vector` (`a007e43…`),
`research/ppbfs-p2-c1-interaction` (`7743248…`),
`research/ppbfs-p2-c4-interaction` (`9c8624c…`),
`research/ppbfs-p2-final-qualification` (`8797dcb…`, authoritative evidence).

## P2 standalone evidence

Large-S sparse: +16.6% [5.4, 28.1] (S=255) and +5.1% (S=507) latency; tracked memory −2.5–4×;
lowest passing query-memory limit −2.5×. Large-k falsification: k≤256, S≤1026, per-bucket
g/i≈1150 — fixed-V survived (kill shot 0.996 [0.967, 1.022]); no dense crossover, no adaptive
branch. Small-S/dense/tiny/bidirectional/high-fanout: neutral (H3-B0V 1.017 non-inferior).

## P1+P2 evidence

- C1 → C1+P2: ~2.6–4.2× bucket-memory reduction; chain255 +51% [1.369, 1.624]; deep roadNet-PA
  behavior preserved (0.973 aggregate; 772-pair 1.043; result sets matched).
- C4 → C4+P2: same memory shape; chain255 +50%; roadNet-772 pooled-6 ≈0.949 (bounded, no
  destruction); C4 merge audit passed (occupied-only re-put wins everywhere; no v2 branch).

## Strongest regression (disclosed)

~6% C1+P2 (~3% C4+P2) on lookup-saturated tiny-NFA canonical H3 traffic (10 forks each,
order-balanced pooled: 0.9389 / 0.9701; k≈3–4, so adaptive promotion cannot fix that corner).
Do not describe any variant as regression-free.

## Final architecture recommendation

P1 remains the main architectural optimization. P2 is best treated as a companion to whichever
P1 design Neo4j prefers (extraction deltas ready for both `contrib` branches). No standalone P2
upstream PR is recommended. Research side complete; the remaining question is which P1
architecture, if either, Neo4j's internal benchmarks prefer.

Entry points: `p2/FINAL_REPORT.md`, `p2/FINAL_QUALIFICATION.md`, `MAINTAINER_HANDOFF.md`.
