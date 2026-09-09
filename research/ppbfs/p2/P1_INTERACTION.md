# P2–P1 interaction (final grade; no frozen branch modified)

Branches (P2 delta only; production-file identity verified by hash):
`research/ppbfs-p2-c1-interaction` ← `contrib/ppbfs-direct-state-index` (`0a8ef5…`);
`research/ppbfs-p2-c4-interaction` ← `contrib/ppbfs-deferred-state-index` (`7411ac…`).
`StateBucket.java` SHA-256 `9F4321…` identical in V/C1P2/C4P2. Timing harness `60C2A2…` for the
screening matrix, `5513DBFD…` (adds hostile-k128 + `p2.workloads` CSV) for all final rigs —
each byte-identical in all execution worktrees at measurement time. Only the P2 winner
(fixed V) was combined — no candidate cross-product.

## C1 + V

Transfer: canonical `allStates` plus frontier/buffer buckets → `StateBucket`; `closeLevel`
reuses `StateBucket.close` (exact release pairing preserved); canonical-identity guard now lives
in `put`. C1's direct-lookup architecture and retire discipline are untouched.

- Correctness: C1's `FoundNodesTest` 5/5, full PPBFS suites 152/152, cross-implementation oracles
  identical (B0/V/C1/C1P2 on 9 workloads; server result sets matched everywhere).
- Memory (deterministic): C1→C1+P2 = 2.62× (S=255), 4.24× (S=507), 1.47× (star); C1+P2 ends
  BELOW B0 (981 KB vs 2,664 KB on chain255: compact buckets + C1 frontier release compose).
- Latency, 10 fresh-JVM paired forks (C1/C1P2, >1 favors C1P2): chain255 **1.514 [1.369, 1.624]**,
  chain509 1.097 [1.082, 1.114], h3analog-bidi2 1.080 [0.996, 1.172], chain31/grid/star/diamond/
  tiny/hostile-k64 all neutral (0.965–1.000, CIs covering parity). All oracles match.
- Primary question: YES — P2 recovers a large fraction of C1's canonical-store allocation
  (2.6–4.2× on sparse regimes) while C1's deep-query mechanism (direct canonical lookup, no
  history probes) is preserved structurally and covered by C1's own tests.
- Boundary (server, Hetionet H3, 10 forks pooled, order-balanced): C1/C1P2 0.9389 — repeatable
  ~6% tax on lookup-saturated tiny-NFA canonical traffic, both order groups and all 23 pairs
  leaning the same way. Mechanism: canonical linear-lookup volume (d6-slice concentration +
  JFR leaf doubling); unfixable by promotion (k=3–4 buckets); disclosed, see FINAL_REPORT.md.

## C4 + V

Transfer: frontier/buffer/retired buckets → `StateBucket`; `mergeBuckets` rewritten from O(S)
slot-scan to occupied-only iteration (`for (incomingState : incoming) existing.put(...)`);
transfer (zero-copy bucket move) and `incoming.close()` release semantics preserved.

- Correctness: C4's `FoundNodesDeferredIndexTest` 8/8 (incl. transfer-identity and merge tests),
  full PPBFS suites 155/155, oracles identical across all six implementations.
- Memory (deterministic): C4→C4+P2 = 2.60× / 4.22× / 1.47× (same shape as C1).
- Latency, 10 paired forks (C4/C4P2): chain255 **1.500 [1.360, 1.605]**, chain509 1.118
  [1.065, 1.204], rest neutral (0.980–1.101, CIs covering parity). All oracles match.
- Merge audit (Part III): re-put beats dense O(S) in all 72+7 collection cells including the
  pure-prepend worst case (1.2× at k=128, ≈11 µs absolute); end-to-end grid-deep-merge under
  C4 activation: 1.066 [0.937, 1.167], oracle match. No v2 branch needed.
- Servers: roadNet-PA 772-deep, 6 forks pooled ≈0.949 (one fork 1.115; no destruction; result
  sets matched); Hetionet H3, 10 forks pooled 0.9701 (neutral-leaning).

## Servers: deep preservation

- roadNet-PA pairs (depth ≤772), C1/C1P2, 3 forks: 0.973 [0.913, 1.037]; 772-pair 1.043;
  all result-length sets matched. C1's deep benefit preserved (no destruction).
- roadNet-PA 772-deep, C4/C4P2, 6 forks pooled ≈0.949, range 0.846–1.115; result sets matched.
  Inconclusive-leaning (thin n on 2 s queries); bounded: no destruction of the deep benefit.

## Pre-existing C1 limitation (observed, not caused, not fixed here)

C1 (with AND without P2) does not complete `grid60-s63` within 15–60 min; B0 itself exceeds
25 min on it (harness-quadratic `InMemoryGraph`), so the workload was dropped for all variants.
C1's scale-triggered pathology on very wide reconvergence predates P2, is out of scope, and is
recorded for the C1 owners — P2 neither fixes nor worsens it.

## Role specialization

Not pursued: a single fixed V serves frontier (iteration+lookup), history/canonical (lookup),
and retired (lookup+merge) roles. The H3-P1 canonical-lookup tax (~3–6%) is the only
role-asymmetric signal found, and specializing (dense canonical + compact elsewhere) would
sacrifice the canonical memory win that carries the B verdict — a bad trade, explicitly rejected.
