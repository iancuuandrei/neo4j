# P2 end-to-end results (`MEASURED`)

Method: `P2TimingScreen.scala` (committed on the V branch; SHA-256 `60C2A2…` identical in all
six comparison worktrees), warmed (5 runs), 9 timed runs per workload per fork, seeded workload
shuffle, oracle self-consistency per fork (rows + sorted-multiset SHA-256 must match across all
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
  3:3 order balance): **0.963 [0.856, 1.083], INCONCLUSIVE-vs-band but centered near 1.0**.
  Per-pair ratios scatter 0.85–1.05 with ±40% fork spreads on 3–13 ms queries: the HTTP/ms noise
  floor, not a bucket effect (unit star2000 lookup-stress with g/i=64 is neutral at 1.022).
  No high-fanout regression evidenced; worst pair 0.853 with [0.748, 0.978] single-slice spread.

## Correctness ledger

- New `StateBucketTest`: 9/9 (empty/single/order/missing/dup/conflict/growth/memory/close).
- Existing suites on V: full `runtime-util` module 530 tests, 0 failures (5 pre-existing `ignore`s);
  PPBFS differential suites (naive-DFS `assertExpected`) green.
- Cross-implementation oracles: B0/V/C1/C1+P2/C4/C4P2 identical on 9 workloads; server H3+LJ
  result sets matched on 47 pairs × forks.
- `NOT RUN`: roadNet/gMark/cit-Patents/as-Skitter/web-Stanford server runs (time-boxed; surveyed
  external queries there compile to the same small-S regime already covered by H3/LJ neutrals).
