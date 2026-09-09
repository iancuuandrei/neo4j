# P2 end-to-end results (`MEASURED`)

Method: `P2TimingScreen.scala` (harness `60C2A2…` for the base matrix, `5513DBFD…` for all
final-qualification rigs — each byte-identical in all execution worktrees at measurement time),
warmed (5 runs), 9 timed runs per workload per fork, seeded workload shuffle, oracle
self-consistency per fork (rows + sorted-multiset SHA-256 must match across all
14 runs or the fork FAILS), cross-variant oracle equality checked by the analyst.
Unit rig: 10 fresh-JVM paired forks, randomized variant order, per-fork medians → paired ratios →
geomean + 10k-bootstrap 95% CI. Primary ratio = B0/V (>1 = V faster). Equivalence band ±5%.
Raw: `D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-timing-screen-v1/`.

## Unit qualification (10 forks; B0 = clean f213, V = sorted-vector candidate)

| Workload | S | n | Geomean | 95% CI | Median | CV | Oracle |
| --- | ---: | ---: | ---: | --- | ---: | ---: | --- |
| chain2000-s255 | 255 | 10 | 1.166 | [1.054, 1.281] | 1.228 | 0.15 | match |
| chain2000-s509 | 507 | 10 | 1.051 | [1.038, 1.064] | 1.050 | 0.02 | match |
| chain2000-s31 | 31 | 10 | 1.025 | [0.987, 1.069] | 1.000 | 0.07 | match |
| star2000-s33 | 33 | 10 | 1.022 | [0.955, 1.098] | 1.020 | 0.12 | match |
| grid30-s19 | 19 | 10 | 1.036 | [0.991, 1.104] | 1.011 | 0.10 | match |
| h3analog-bidi2-s5 | 5 | 10 | 0.980 | [0.936, 1.022] | 0.986 | 0.07 | match |
| diamond-dense-s18 | 18 | 10 | 0.982 | [0.903, 1.060] | 0.976 | 0.13 | match |
| tiny-chain500-s4 | 4 | 10 | 1.007 | [0.890, 1.130] | 1.027 | 0.18 | match |

Reading: one strong positive (chain255, lower bound clears +5%); one modest positive (chain509,
tight CI); six neutrals — no workload shows a material regression (worst point 0.98, worst CI
edge 0.90 on the dense control, CV-consistent with noise). Every fork's oracle matched across
variants; any mismatch would have failed the fork.

Caveat (conservative bias): `InMemoryGraph.nodeRels` scans O(E) per expansion, inflating the
harness denominator with work that production store cursors do degree-proportionally. Unit ratios
therefore UNDERSTATE production bucket-CPU effects on low-degree graphs. grid60-s63 was removed
as harness-infeasible in every variant including B0 (documented workload-design correction, not
a product finding).

## External validation (server distributions, jar-swap isolation)

Distributions share one base; lib dirs differ ONLY in `neo4j-cypher-runtime-util` (B0 `25B44F…`
from clean f213 vs V `E6284F…`). All paired result sets matched (lengths multisets equal).

- **Hetionet H3** (S=4, dense k≈3, 23 pairs, SHORTEST 2, bidirectional):
  first attempt (5 forks) read 0.909 [0.826, 0.999] — then DIAGNOSED as a first-in-fork
  cold-cache order confound (V ran first in 3/5 forks; the first variant pays cold page cache;
  per-pair 23/23 uniformity + fork-order correlation + balanced rerun below).
  Rerun (10 forks, measured 5:5 order alternation): **1.017 [0.961, 1.077], PRACTICALLY
  NON-INFERIOR** (±5% TOST). H3 is neutral, not a regression. JFR pair on the subset shows
  signpost/propagation dominance with matching profile shapes (see `JFR_ANALYSIS.md`).
- **LiveJournal shallow** (high fanout, 24 pairs, 6 forks with throwaway cache warmup, measured
  3:3 order balance): **0.963 [0.856, 1.083]: no material regression detected**
  (CI wider than the ±5% band on 3–13 ms queries over HTTP; per-pair ratios scatter 0.85–1.05
  with ±40% fork spreads — the measurement floor, not a bucket effect; unit star2000
  lookup-stress at g/i=64 is neutral at 1.022).
  No high-fanout regression evidenced; worst pair 0.853 with [0.748, 0.978] single-slice spread.

## Correctness ledger

- New `StateBucketTest`: 9/9 (empty/single/order/missing/dup/conflict/growth/memory/close).
- Existing suites on V: full `runtime-util` module 530 tests, 0 failures (5 pre-existing `ignore`s);
  PPBFS differential suites (naive-DFS `assertExpected`) green.
- Cross-implementation oracles: B0/V/C1/C1+P2/C4/C4P2 identical on all unit workloads (incl. all
  large-k/hostile families); server result sets matched on H3 (B0/V/C1/C1P2/C4/C4P2), LJ (B0/V),
  roadNet-PA (C1/C1P2, C4/C4P2).
- roadNet/gMark/cit-Patents/as-Skitter/web-Stanford coverage: roadNet-PA done (above);
  gMark/cit-Patents/as-Skitter/web-Stanford server runs remain `NOT RUN` (time-boxed; surveyed
  queries there compile to the same small-S regime already covered by H3/LJ neutrals).

## Final qualification, Part I — large-k falsification (B0/V, 10–20 forks)

Telemetry-qualified family (S/k/span/g/i measured per bucket): k=32 (S=34/258), k=64 (S=66/514),
k=128 (S=130/1026), k=256 (S=258), hostile F=8 fanout (per-bucket g/i ≈52/576/1150).
S≫k@256 NOT RUN (monotonicity argument recorded in `FINAL_QUALIFICATION.md`).

| Workload | max k | per-bucket g/i | Geomean | 95% CI | Oracle |
| --- | ---: | ---: | --- | --- | --- |
| k32-s34 / k32-s258 | 32 | ~38 | 0.978 / 1.036 | [0.900, 1.053] / [0.944, 1.143] | match |
| k64-s66 / k64-s514 | 64 | ~77 | 0.977 / 0.991 | [0.941, 1.010] / [0.957, 1.025] | match |
| k128-s130 / k128-s1026 | 128 | ~154 | 0.970 / 0.941 | [0.915, 1.033] / [0.856, 1.033] | match |
| k256-s258 | 256 | ~307 | 0.943 | [0.844, 1.052] | match |
| hostile-k32-f8 | 32 | ~52 | 1.001 | [0.923, 1.091] | match |
| hostile-k64-f8 (n=20) | 64 | ~576 | 0.965 | [0.914, 1.021] | match |
| hostile-k128-f8 | 128 | ~1150 | 0.996 | [0.967, 1.022] | match |

No CI excludes parity low-side; worst point 0.925 moved to 0.965 at n=20. Fixed-V survives;
no adaptive branch created.

## Final qualification, Part II — P1 pairs (10 forks each + servers)

Unit C1/C1P2 (ratio >1 favors C1P2): chain255 1.514 [1.369, 1.624], chain509 1.097
[1.082, 1.114], h3analog-bidi2 1.080 [0.996, 1.172], six others neutral (0.965–1.000).
Unit C4/C4P2: chain255 1.500 [1.360, 1.605], chain509 1.118 [1.065, 1.204], rest neutral
(0.980–1.101). All oracles match. Full tables in `FINAL_QUALIFICATION.md`.
Servers: roadNet-PA C1-pair 0.973 [0.913, 1.037] (772-pair 1.043); roadNet-772 C4-pair pooled-6
≈0.949 (bounded, no destruction); H3 C1-pair pooled-10 0.9389, H3 C4-pair pooled-10 0.9701
(order-balanced; the disclosed ~3–6% canonical-lookup tax). All server result sets matched.
