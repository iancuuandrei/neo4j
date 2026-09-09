# P2 final report — sparse NFA-state storage (final qualification included)

Question: should Neo4j's per-node dense `NodeState[S]` buckets change, and to what?
Method: per-bucket telemetry → cost model → collection screening → fixed-V implementation →
10-fork paired qualification → memory-limit probes → JFR → external paired servers (with an
order-confound diagnosis and rerun) → P1 combination of the winner only → large-k adversarial
falsification (k≤256, g/i≈1150) → final-grade 10-fork P1 interaction + deep-server preservation
+ C4 merge audit. Full closure in `FINAL_QUALIFICATION.md`.
Baselines: historical `f213380f…` (causal, all comparisons); upstream `736cad02…` (cross-check).

## Answers

1. Level-owned buckets are often extremely sparse: 99.2% null scans at S=255, 97% at S=33,
   88% at S=19; steady-state k=2, max observed k=16.
2. `S` = compiled NFA size (2–1026 measured); `k` = 1–4 in 17/20 telemetry workloads,
   up to 256 in the falsification family (telemetry-qualified).
3. Active ids are one contiguous run (mean runs ≈1.0) with span ≈ k, except source buckets and
   the designed dispersed control — no range-compressible regime beyond what a vector captures.
4. Dense buckets are the top-allocated short-lived structure (JFR `Object[]` #1, 42 bucket
   lists/sample window); tracked cost `40+A(S)` per node.
5. Null-slot scans are up to 99% of iteration work; bucket code is ~5–12% of PPBFS CPU.
6. Frontier exact lookups are 100% hits; per-bucket g/i spans 0.5 (chains) to 64
   (star center) to ≈1150 (hostile-k128 L0 buckets, measured); canonical lookups split
   shallow-buffer vs deep-history by search depth.
7. Sorted compact vector (V) wins the sparse regime: +16.6% [5.4, 28.1] at S=255, +5.1% at S=507.
8. No dense crossover observed through k=256: worst end-to-end point 0.925
   (hostile-k64 n=10, CI covering parity; 0.965 at n=20) and 0.996 [0.967, 1.022] on the
   kill shot (k=128, g/i≈1150); tiny-NFA 1.01 — fixed V stands; adaptive promotion rejected
   (a promotion threshold could not address the only residual tax, which sits at k=3–4).
9. No — adaptation is not needed (see 8); the simplest robust policy is fixed-V.
10. N/A (no adaptation). Had one been needed, the hypothesis band was k≈8–16.
11. Large-S sparse synthetic workloads improve (chains, star-memory).
12. Small-S, dense, tiny, bidirectional, high-fanout workloads remain neutral
    (H3-B0V 1.017 non-inferior; LJ no material regression detected; unit neutrals).
    P1-combined small-NFA lookup-saturated traffic carries a ~3–6% tax (see 13).
13. Strongest regression: a repeatable ~6% tax for C1+P2 (~3% for C4+P2) on
    lookup-saturated tiny-NFA canonical traffic (Hetionet H3 pairs, 10 forks each,
    order-balanced pooled: 0.9389 / 0.9701; all 23 pairs lean the same way in both pairs;
    d6-slice concentration + JFR leaf doubling corroborate the canonical linear-lookup-volume
    mechanism). Unfixable by promotion (k=3–4 buckets never reach any threshold) and therefore
    a disclosed boundary, not a trigger. Worst standalone-V observation: 0.925 point
    (hostile-k64 n=10, CI straddles parity; n=20 → 0.965). The H3-B0V v2 −9% was a diagnosed
    first-in-fork cold-cache order artifact (3:2 V-first over 5 forks), resolved by a balanced
    10-fork rerun (1.017).
14. Yes: tracked −2.47×/−3.94×/−1.47× (chains/star); allocation samples down on identical recording.
15. Yes: lowest passing configured limit −2.47×/−1.46× (configured-limit claim, not resident).
16. Yes, narrowly: +17%/+5% on large-S sparse; neutral elsewhere; large-k hostile ≥0.94
    points with parity-covering CIs (see `FINAL_QUALIFICATION.md`).
17. Yes: C1→C1+P2 memory −2.6×/−4.2×/−1.47× with C1's direct-lookup architecture preserved;
    final-grade unit latency C1+P2 ≤ C1 with chain255 +51% [1.369, 1.624]; roadNet deep
    preserved (0.973 aggregate, 772-pair 1.043); H3 boundary in 13.
18. Yes: C4→C4+P2 identical memory ratios; retired merge occupied-proportional (audit: re-put
    wins everywhere incl. pure-prepend worst case; no v2 branch); final-grade unit latency
    C4+P2 ≤ C4 with chain255 +50%; roadNet-772 pooled-6 ≈0.949 (bounded, no destruction).
19. No — one fixed representation serves all roles; specialization rejected even under the
    H3 tax (dense canonical + compact scheduling would sacrifice the canonical memory win
    that carries the B verdict — an explicitly bad trade).
20. Ship P2 WITH P1 (C1/C4 package), not as a standalone latency optimization — the standalone
    latency win is narrow (large sparse NFAs) while the P1-combined memory win is broad
    (any k<S in long-lived/merged stores), bounded by the ~3–6% H3-P1 canonical-lookup tax
    disclosed in 13. See verdict.

## Verdict

```text
B — Useful mainly with P1
```

Fixed sorted-vector `StateBucket` (no adaptation, no specialization, no hash, range deferred):
standalone it converts the sparse regime (+17% latency, −2.5× memory/limit, neutral elsewhere
with no material regression through k=256 at g/i≈1150); its broadest operational value is
erasing P1's dense canonical/retired-store tax (C1/C4 −2.6…−4.2×) while preserving their lookup
architectures and result equivalence (6-implementation oracle agreement + server pairs matched),
bounded by a disclosed ~3–6% tax on lookup-saturated tiny-NFA canonical traffic. A standalone
upstream latency proposal (A) would overclaim breadth — surveyed real queries compile to small
NFAs where P2 is neutral by design. D is disproportionate (it would discard +50% sparse-chain
P1 gains and 4× memory recovery over a ≤6% corner tax). C overstates the danger (nothing is
unsafe; worst effect −6% in one corner, unfixable by guards since it sits at k=3–4). B2 has no
basis (both pairs show the same tax). B3 is the wrong remedy (promotion cannot help k=3–4
buckets). E is unwarranted: no load-bearing ambiguity remains.

`contrib/ppbfs-sparse-state-storage` was NOT created: graduation is conditional on P1's fate
(B-verdict), so a standalone P2 PR would misrepresent the recommendation. The extraction plan
(3-file delta, tests, commands) is in `MAINTAINER_HANDOFF.md`, ready if C1/C4 graduate.
