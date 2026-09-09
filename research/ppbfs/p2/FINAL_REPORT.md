# P2 final report — sparse NFA-state storage

Question: should Neo4j's per-node dense `NodeState[S]` buckets change, and to what?
Method: per-bucket telemetry → cost model → collection screening → fixed-V implementation →
10-fork paired qualification → memory-limit probes → JFR → external paired servers (with an
order-confound diagnosis and rerun) → P1 combination of the winner only.
Baselines: historical `f213380f…` (causal, all comparisons); upstream `736cad02…` (cross-check).

## Answers

1. Level-owned buckets are often extremely sparse: 99.2% null scans at S=255, 97% at S=33,
   88% at S=19; steady-state k=2, max observed k=16.
2. `S` = compiled NFA size (2–507 measured); `k` = 1–4 in 17/20 telemetry workloads.
3. Active ids are one contiguous run (mean runs ≈1.0) with span ≈ k, except source buckets and
   the designed dispersed control — no range-compressible regime beyond what a vector captures.
4. Dense buckets are the top-allocated short-lived structure (JFR `Object[]` #1, 42 bucket
   lists/sample window); tracked cost `40+A(S)` per node.
5. Null-slot scans are up to 99% of iteration work; bucket code is ~5–12% of PPBFS CPU.
6. Frontier exact lookups are 100% hits; per-bucket g/i spans 0.5 (chains) to 64 (star center);
   canonical lookups split shallow-buffer vs deep-history by search depth.
7. Sorted compact vector (V) wins the sparse regime: +16.6% [5.4, 28.1] at S=255, +5.1% at S=507.
8. No dense crossover observed: worst rigorous point 0.98 (dense89), tiny-NFA 1.01 — fixed V
   stands; adaptive promotion rejected per Gate 4 (no evidence it would buy anything).
9. No — adaptation is not needed (see 8); the simplest robust policy is fixed-V.
10. N/A (no adaptation). Had one been needed, the hypothesis band was k≈8–16.
11. Large-S sparse synthetic workloads improve (chains, star-memory).
12. Small-S, dense, tiny, bidirectional, high-fanout external workloads remain neutral
    (H3 1.017 non-inferior; LJ 0.963 inconclusive-at-noise-floor; six unit neutrals).
13. Strongest regression: none material. Worst rigorous observation 0.98; the H3 v2 −9% was a
    diagnosed first-in-fork cold-cache order artifact (3:2 V-first over 5 forks), resolved by a
    balanced 10-fork rerun (1.017) with measured 5:5 alternation.
14. Yes: tracked −2.47×/−3.94×/−1.47× (chains/star); allocation samples down on identical recording.
15. Yes: lowest passing configured limit −2.47×/−1.46× (configured-limit claim, not resident).
16. Yes, narrowly: +17%/+5% on large-S sparse; neutral elsewhere (see table in
    `END_TO_END_RESULTS.md`).
17. Yes: C1→C1+P2 memory −2.6×/−4.2×/−1.47× with C1's direct-lookup architecture preserved and
    screening latency ≤ C1 everywhere.
18. Yes: C4→C4+P2 identical ratios; retired merge becomes occupied-proportional; transfer/close
    semantics preserved and covered by C4's own tests.
19. No — one fixed representation serves all roles; specialization rejected for lack of benefit.
20. Conditionally: ship P2 WITH P1 (C1/C4 package), not as a standalone latency optimization —
    the standalone latency win is narrow (large sparse NFAs) while the P1-combined memory win is
    broad (any k<S in long-lived/merged stores) with zero regressions. See verdict.

## Verdict

```text
B — Useful mainly with P1
```

Fixed sorted-vector `StateBucket` (no adaptation, no specialization, no hash, range deferred):
standalone it converts the sparse regime (+17% latency, −2.5× memory/limit, neutral elsewhere
with no material regression); its broadest operational value is erasing P1's dense canonical/
retired-store tax (C1/C4 −2.6…−4.2×) while preserving their lookup architectures and result
equivalence (6-implementation oracle agreement + 47 server pairs matched). A standalone
upstream latency proposal (A) would overclaim breadth — surveyed real queries compile to small
NFAs where P2 is neutral by design. D is contradicted by the sparse-regime and P1-combined
evidence. E is unwarranted: no load-bearing ambiguity remains (the two scares — H3 v2 and the
C1 grid60 stall — were diagnosed to an order confound and a pre-existing C1/harness limit).

`contrib/ppbfs-sparse-state-storage` was NOT created: graduation is conditional on P1's fate
(B-verdict), so a standalone P2 PR would misrepresent the recommendation. The extraction plan
(3-file delta, tests, commands) is in `MAINTAINER_HANDOFF.md`, ready if C1/C4 graduate.
