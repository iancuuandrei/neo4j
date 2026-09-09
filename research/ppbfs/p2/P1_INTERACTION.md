# P2–P1 interaction (`MEASURED` screening + unit oracles; no frozen branch modified)

Branches (P2 delta only; production-file identity verified by hash):
`research/ppbfs-p2-c1-interaction` ← `contrib/ppbfs-direct-state-index` (`0a8ef5…`);
`research/ppbfs-p2-c4-interaction` ← `contrib/ppbfs-deferred-state-index` (`7411ac…`).
`StateBucket.java` SHA-256 `9F4321…` identical in V/C1P2/C4P2; harness `60C2A2…` identical in all
six worktrees. Only the P2 winner (fixed V) was combined — no candidate cross-product.

## C1 + V

Transfer: canonical `allStates` plus frontier/buffer buckets → `StateBucket`; `closeLevel`
reuses `StateBucket.close` (exact release pairing preserved); canonical-identity guard now lives
in `put`. C1's direct-lookup architecture and retire discipline are untouched.

- Correctness: C1's `FoundNodesTest` 5/5, full PPBFS suites 152/152, cross-implementation oracles
  identical (B0/V/C1/C1P2 on 9 workloads; server result sets matched for B0/V).
- Memory (screening medians): C1→C1+P2 = 2.62× (S=255), 4.24× (S=507), 1.47× (star); C1+P2 ends
  BELOW B0 (981 KB vs 2,664 KB on chain255: compact buckets + C1 frontier release compose).
- Latency (screening): C1+P2 ≤ C1 on all 9 workloads (chain255 14.0 vs 20.9 ms; star 417 vs 475).
- Primary question: YES — P2 recovers a large fraction of C1's canonical-store allocation
  (2.6–4.2× on sparse regimes) while C1's deep-query mechanism (direct canonical lookup, no
  history probes) is preserved structurally and covered by C1's own tests.

## C4 + V

Transfer: frontier/buffer/retired buckets → `StateBucket`; `mergeBuckets` rewritten from O(S)
slot-scan to occupied-only iteration (`for (incomingState : incoming) existing.put(...)` —
O(k₁·k₂) worst-case linear puts at tiny k, no size-equality precondition needed); transfer
(zero-copy bucket move) and `incoming.close()` release semantics preserved.

- Correctness: C4's `FoundNodesDeferredIndexTest` 8/8 (incl. transfer-identity and merge tests),
  full PPBFS suites 155/155, oracles identical across all six implementations.
- Memory: C4→C4+P2 = 2.60× / 4.22× / 1.47× (same shape as C1 — retired buckets dominate).
- Latency (screening): C4+P2 ≤ C4 everywhere (chain255 14.4 vs 22.4 ms).
- Merge behavior: the sorted-compact merge does work proportional to occupied states as
  predicted; no merge-specific regression observed (deep reconvergent coverage: grid30, diamond,
  h3analogs; oracles match).

## Pre-existing C1 limitation (observed, not caused, not fixed here)

C1 (with AND without P2) does not complete `grid60-s63` within 15–60 min while B0/V do;
B0 itself exceeds 25 min on it (harness-quadratic `InMemoryGraph`, see end-to-end notes), so the
workload was dropped for all variants. C1's scale-triggered pathology on very wide
reconvergence predates P2, is out of scope, and is recorded for the C1 owners — P2 neither
fixes nor worsens it (identical non-completion with/without P2).

## Role specialization

Not pursued: a single fixed V serves frontier (iteration+lookup), history/canonical (lookup),
and retired (lookup+merge) roles with measured parity-or-better in each. The incompatible-
crossover condition for specialization never materialized (dense89 and tiny controls neutral).
