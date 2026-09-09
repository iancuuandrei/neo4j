# P2 final qualification (Part I–III evidence closure)

Branch: `research/ppbfs-p2-final-qualification` (from `45c8544…`). Frozen production branches
untouched; all new work (scripts, workloads, analysis, reports) lives here. Timing harness
SHA-256 `5513DBFD…` byte-identical in all 7 execution worktrees (verified). Method inherited:
10 fresh-JVM paired forks (seeded variant order), 5 warmups + 9 timed runs, per-fork medians,
paired ratios, geomean + 10k-bootstrap 95% CI, oracle equality. Ratio direction is stated per
table. Raw evidence append-only on `D:`.

## Part I — large-k falsification: fixed-V SURVIVES

Collection danger zone first (`P2LargeKMicrobench`, 205 cells, production-faithful V with early
exit): V/D hit ≈1.0 (first), ≈2–3× (mid, k=32–64), ≈5–10× (last, k=64–128), ≈21× (last, k=256);
miss-below/inside ≈1.0 at ALL k (early exit); iteration <1 whenever k<S. Theoretical danger:
large-k + hit-heavy + high g/i. That corner was then tested end-to-end with semantically real
PPBFS workloads (telemetry-qualified; S/k/span/g/i measured, never assumed):

| Workload | S | max k | g/i (per-bucket max) | B0/V (10 forks) | 95% CI | Oracle |
| --- | ---: | ---: | --- | --- | --- | --- |
| k32-s34 / k32-s258 | 34 / 258 | 32 | ~38 | 0.978 / 1.036 | [0.900, 1.053] / [0.944, 1.143] | match |
| k64-s66 / k64-s514 | 66 / 514 | 64 | ~77 | 0.977 / 0.991 | [0.941, 1.010] / [0.957, 1.025] | match |
| k128-s130 / k128-s1026 | 130 / 1026 | 128 | ~154 | 0.970 / 0.941 | [0.915, 1.033] / [0.856, 1.033] | match |
| k256-s258 | 258 | 256 | ~307 | 0.943 | [0.844, 1.052] | match |
| hostile-k32-f8 | 34 | 32 | ~52 | 1.001 | [0.923, 1.091] | match |
| hostile-k64-f8 | 66 | 64 | ~576 | 0.965 (n=20) | [0.914, 1.021] | match |
| hostile-k128-f8 | 130 | 128 | ~1150 | 0.996 | [0.967, 1.022] | match |

The intentionally hostile kill shot (k=128, ≈1150 exact lookups per single bucket iteration,
10× microbench per-lookup disadvantage) lands at 0.996 with a tight CI. No workload's CI
excludes parity on the low side; worst point 0.925 (hostile-k64 n=10) regressed toward 0.965
at n=20. S≫k@256 explicitly NOT RUN (monotonicity argument: S≈k is V's worst case at fixed k
— minimal iteration saving at full lookup pressure — so survival there bounds it; recorded).
**Decision: no adaptive branch. Fixed-V stands. `research/ppbfs-p2-adaptive` was NOT created.**

## Part II — P1 interaction at final grade (10 forks per pair + servers)

Unit (C1/C1P2 and C4/C4P2, required subset + hostile-k64-f8):

| Workload | C1/C1P2 | 95% CI | C4/C4P2 | 95% CI |
| --- | --- | --- | --- | --- |
| chain2000-s255 | 1.514 | [1.369, 1.624] | 1.500 | [1.360, 1.605] |
| chain2000-s509 | 1.097 | [1.082, 1.114] | 1.118 | [1.065, 1.204] |
| chain2000-s31 | 0.997 | [0.964, 1.034] | 1.051 | [0.985, 1.115] |
| star2000-s33 | 0.969 | [0.906, 1.020] | 1.048 | [0.991, 1.130] |
| grid30-s19 | 0.996 | [0.967, 1.025] | 1.101 | [0.988, 1.221] |
| h3analog-bidi2-s5 | 1.080 | [0.996, 1.172] | 0.984 | [0.933, 1.047] |
| diamond-dense-s18 | 0.965 | [0.912, 1.030] | 1.057 | [0.929, 1.190] |
| tiny-chain500-s4 | 0.967 | [0.855, 1.076] | 1.027 | [0.935, 1.142] |
| hostile-k64-f8 | 0.972 | [0.920, 1.043] | 0.980 | [0.894, 1.074] |

All oracles match. C1+P2 is +51%/+10% on sparse chains (C1 pays the dense tax twice per node:
canonical + scheduling buckets); C4+P2 mirrors it. No unit regression in either pair.
Tracked memory (deterministic): C1→C1+P2 −2.62×/−4.24×/−1.47×; C4→C4P2 −2.60×/−4.22×/−1.47×.

Servers (jar-swap isolation; lib diff = exactly the runtime-util jar; hashes recorded):
- roadNet-PA pairs (depth ≤772), C1/C1P2, 3 forks: 0.973 [0.913, 1.037]; 772-pair 1.043;
  all result sets matched. Deep benefit preserved (no destruction).
- roadNet-PA 772-deep, C4/C4P2, 6 forks pooled: ≈0.949 (v1 0.917 + v2 0.983; one fork 1.115);
  inconclusive-leaning, bounded: no destruction; result sets matched.
- Hetionet H3 neutrals, 10 forks each, order-balanced pooled: C1/C1P2 0.9389, C4/C4P2 0.9701;
  all 23 pairs lean the same way in both pairs; result sets matched. Small repeatable tax
  (~3–6%) on lookup-saturated tiny-NFA canonical traffic — the disclosed boundary (below).
- LiveJournal (prior program): 0.963 inconclusive-at-noise-floor; wording fixed to “no material
  regression detected” (Part IV).

## Part III — C4 merge audit: re-put retained, no v2 branch

Collection audit (72 cells, k=4–128, S=2k/8k, disjoint/half/full × ascending/interleaved/below;
below = pure-prepend worst realistic case): re-put beats dense O(S) scan in ALL cells (typical
5–17×; worst case 1.2× at k=128 pure prepends, ≈11 µs absolute). End-to-end arbiter
grid-deep-merge-s31 (372 buckets, depth 14, C4 active): C4/C4P2 1.066 [0.937, 1.167], oracle
match; B0/V 1.032. Merges are dozens-per-query; even worst-case absolute cost is sub-ms.
**Decision: retain the simple re-put merge. `research/ppbfs-p2-c4-interaction-v2` NOT created.**
Memory accounting on promotion/transfer/close covered by StateBucket/C4 unit tests (exact
release pairing; no double release; transfer case unchanged).

## Hardened invalidations log (scares that did not survive)

- H3-B0V v2 −9% → first-in-fork cold-cache order confound (3:2 imbalance); balanced v3 = 1.017.
- k128 hostile oracle False → analyzer missing-data bug (all 20 oracles byte-identical).
- C1 grid60 stall → pre-existing C1 scale pathology + harness-quadratic graph (B0 also >25 min);
  workload dropped for all variants, recorded for C1 owners.
- TLAB parser missing GB unit → hid real totals; fixed (B0 1.23 GB → V 1.00 GB unit).

## Verdict input

Fixed-V survives falsification through k=256 at g/i≈1150; P1 pairs show no unit regression,
preserved deep behavior, 2.6–4.2× memory recovery, safe merge; the remaining cost is a
repeatable ~3–6% tax on lookup-saturated tiny-NFA canonical traffic (H3 pairs, both
architectures, 10 forks each, order-balanced pooled) — unfixable by promotion (k=3–4 buckets)
and therefore a disclosed boundary, not a trigger. See FINAL_REPORT.md for the verdict.
