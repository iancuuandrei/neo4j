# P7 materiality qualification — is WALK post-saturation bookkeeping worth carrying?

Verdict: **STRONG REAL-WORLD CASE** (with one disclosed ms-scale overhead corner).

Question answered: in realistic workloads, `ExpandAll + WALK` produces large
post-saturation tracing work on a majority of sampled queries across social,
web, and internet topologies; P7 removes 93–97% of it where path reconvergence
exists, with material whole-PPBFS and end-to-end speedups, negligible
regressions, and zero delta outside its scope. Hetionet is neutral
(low reconvergence), not negative.

## Workload definitions

Exact queries, source manifests, NFA constructions, and invocation parameters:
[`research/ppbfs/p7/workloads/`](workloads/) (maintainer-inspectable without
reading the full archive).

## SHAs and method

```text
baseline:  736cad02a36bb4a0d32c1064f44768339c814269 (upstream/2026.08)
candidate: 89f2a714f5b39c4f1383a84b26ceacbaaeaf0c9c (contrib/ppbfs-walk-post-saturation-bookkeeping)
```

Two independent vehicles, same conclusion:

1. **Embedded counter driver** (`p7mat/P7Scan`, outside the repo): opens real
   stores read-only, runs unbound SHORTEST-K WALK PPBFS directly with counting
   hooks (pre/post-saturation pushes, targets, schedules, target-signposts,
   prunes), per variant by classpath order. Deterministic counters.
2. **Real servers**: full `mvn package` distributions of both SHAs, LJ import
   (4,847,571 nodes / 68,993,773 rels), Cypher `REPEATABLE ELEMENTS SHORTEST`
   over HTTP. Operator proven by plan: `StatefulShortestPath(All, Walk)`,
   SLOTTED, 2026.08.0, unbound target, inlined LINK.

Driver NFA is single-state walk-all (OUTGOING, all types); Hetionet also ran a
typed 2-state chain NFA (`chain2:G_I_G,G_I_G`). Discovery used maxRows=500 and
per-query time caps, so reported post-saturation work is a *lower bound* on
full queries. Timing: 5 fresh JVM forks, order-balanced variants, 1 warmup +
3 reps, paired-log 95% CIs.

## Applicability: how often does P7 apply? (unbound WALK, SHORTEST 1, K=1)

| Dataset | Queries | Improved | PostSatFraction med | Aggregate post-sat share |
| --- | --- | --- | --- | --- |
| LiveJournal (8 manifest + 60 random sources) | 68 | 56 | 0.579 | 0.987 |
| web-Stanford (1 + 60) | 61 | ~all with work | 0.859 | ~1.000 |
| as-Skitter (1 + 60) | 61 | most | 0.536 | 0.983 |
| Hetionet Genes/Compounds/Diseases | 52 | most (small) | 0.83–0.98 | 0.89–0.97 |

Post-saturation work is the norm, not the exception, on LJ/web/skitter; the
opportunity is broad-based (40/68 LJ queries above 0.5 fraction), not a handful
of pathological sources — though the largest single LJ query holds ~96% of
aggregate post-sat pushes.

## How much work is removed? (EliminationFraction on post-sat pushes)

| Dataset | Baseline post-sat | Candidate post-sat | Eliminated |
| --- | --- | --- | --- |
| LiveJournal | 5,663,375 | 369,131 | **0.935** |
| web-Stanford | 23,583,460,037 | 1,552,444,469 | **0.934** |
| as-Skitter | 4,657,982 | 148,205 | **0.968** |
| Hetionet (3 labels) | — | — | 0.06–0.13 |
| Hetionet typed chain2 | 63,178 | 52,176 | 0.17 |
| LJ K=2 (sub20) | 27,435 | 16,781 | 0.39 |
| LJ K GROUPS K=2 (sub20) | — | — | 0.05 |

Metadata equality (rows/schedules/target-signposts) held on **every
non-truncated run** (200+ query-pairs, 0 mismatches). The 6 web-Stanford
mismatches were all baseline-side 60s truncations, resolved in favor of the
candidate completing.

## End-to-end and whole-PPBFS timing

Paired embedded timing (whole-PPBFS time ≈ query time for these shapes):

| Query | Speedup (base/cand) | 95% CI |
| --- | --- | --- |
| LJ 2022306 | **28.04x** | [20.86, 37.69] |
| web-Stanford 8183 | **6.65x** | [3.36, 13.14] |
| web-Stanford 83240 | **6.16x** | [3.24, 11.72] |
| as-Skitter 1696073 | **7.30x** | [6.28, 8.49] |
| web-Stanford 29888 | 1.48x | [0.76, 2.90] |
| Hetionet 28050 / 17330 | 1.02x / 1.12x | parity–small win |

Feasibility gaps (baseline censored, candidate complete): web-Stanford 48965 —
baseline 34.4B pushes / 452s timeout vs candidate 111K pushes / 0.04s.

Real Cypher end-to-end (`StatefulShortestPath(All, Walk)`, LIMIT queries):

| Query | Baseline | Candidate |
| --- | --- | --- |
| LJ 2022306 LIMIT 75 | >90s (server timeout) | **53 rows, 52ms** |
| LJ 2022306 LIMIT 50 | 73ms | (covered by above; rows identical 50/50) |
| LJ 3705591 LIMIT 500 | 80ms | **61ms**, rows identical 500/500 |
| LJ 1042128 LIMIT 500 (control) | 76ms | 77ms (parity) |

## Hotspot, allocation, overhead

- JFR (LJ 2022306): PPBFS frames in 61/130 execution samples (47%) baseline
  vs 3/27 (11%) candidate — the removed work *was* the hotspot.
- Allocation: JFR allocation samples 1747 → 971 (−44%); no memo blowup
  (memo is small state sets; run dominated by shared startup either way).
- Small-query overhead (disclosed): on <50ms no-opportunity queries the memo
  machinery costs ~1–3ms absolute (e.g. 11.7→15.1ms, 1.0→2.2ms) — visible only
  because the queries themselves are milliseconds. No relative regression on
  larger queries.

## Regression controls (all zero-delta)

- Bound Walk, 48 real LJ pairs: bit-identical across all counter columns.
- TRAIL, LJ sub20: bit-identical including push counts (96,493).
- K GROUPS: identical metadata, 5% elimination (little post-sat work exists).
- Never-saturating / low-multiplicity queries: candidate ops == baseline ops.

## Limitations

- Discovery truncated at 500 rows / 60–180s caps (understates full-query work).
- Driver NFA is single-state walk-all (plus one typed 2-state Hetionet check);
  absolute numbers are NFA-dependent, but the Cypher end-to-end results confirm
  the mechanism on planner-built NFAs too (where it is *stronger*: the QPP NFA
  with juxtaposition structure amplifies trace multiplicity).
- No road-network runs (low-multiplicity control skipped as instructed).
- as-Skitter timing set is small (3 queries); Hetionet end-to-end not run
  (embedded neutral is sufficient signal there).
- Server timing is few-rep, single-machine, order-noted (rep-1 cold effects
  reported, medians used); no multi-machine CI beyond embedded forks.

## Why this clears the maintainer bar

ApplicabilityRate is high (majority of sampled WALK/ExpandAll queries),
TracingFraction is dominant on affected queries (JFR 47%, 6–28x speedups,
feasibility gaps), EliminationFraction is 93–97% where reconvergence exists.
Cost: +207 production lines, WALK-gated, lazily allocated, with measured
zero-delta outside scope and a bounded ms-scale tax on tiny queries. This is
not a synthetic-only effect: it reproduces end-to-end on real Cypher over
LiveJournal and web-Stanford.
