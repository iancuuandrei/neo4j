# PPBFS C1 external validation

## Answer first

The external suite does not overturn the road-network result, but it narrows what can be claimed. C1 is strongly beneficial when historical lookup debt is large, equivalent within +/-5% on the replicated Hetionet H3 workload, and statistically unresolved on cit-Patents, gMark, and LiveJournal. It is not valid to describe intervals containing 1 as neutral.

**Final classification:** `GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`.

## Frozen comparison

```text
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
B0 timing/instrumentation: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
C1: caf33be9f5de2d8d7f4a291abd466cf808aaf801
research-only occupancy branch: 3b0413e37db
```

No external-validation run changed B0 or C1. Timing used fresh processes, paired seeds, fork medians, paired log ratios, and two-sided 95% Student-t intervals. Practical classifications use a +/-5% margin and a TOST-style 90% interval.

## Results

| Workload | Forks | B0/C1 geomean | 95% CI | 90% CI | Classification |
| --- | ---: | ---: | ---: | ---: | --- |
| cit-Patents d1–12 | 5 | 1.220x | [0.977,1.524] | [1.029,1.448] | PRACTICALLY NON-INFERIOR |
| gMark test-30k d1–2 | 5 | 1.246x | [0.578,2.685] | [0.691,2.247] | INCONCLUSIVE |
| Hetionet H1 | 5 | 1.049x | [0.850,1.295] | [0.893,1.233] | INCONCLUSIVE |
| Hetionet H2 | 5 | 0.992x | [0.874,1.125] | [0.900,1.092] | INCONCLUSIVE |
| Hetionet H3 screening | 5 | 0.977x | [0.895,1.066] | [0.913,1.045] | INCONCLUSIVE |
| Hetionet H3 extension | 10 | 0.996x | [0.954,1.041] | [0.962,1.032] | EQUIVALENT WITHIN +/-5% |
| Hetionet H4 | 5 | 0.964x | [0.841,1.106] | [0.868,1.072] | INCONCLUSIVE |
| LiveJournal extension | 10 | 0.948x | [0.782,1.149] | [0.811,1.108] | INCONCLUSIVE |

`MEASURED`: every included pair returned identical path lengths and qualified for the PPBFS implementation. The LiveJournal point estimate corresponds to about 5.2% slowdown, but its interval is too wide to establish either practical non-inferiority or material regression.

## Fail-closed exclusions

- FinBench official path queries did not execute StatefulShortestPath and are `NON-QUALIFYING FOR DIRECT PPBFS EVIDENCE`.
- Hetionet H5 latency was excluded in full after baseline-only screening projected disproportionate runtime; no candidate timing was observed and no favorable subset was selected.
- gMark produced a sampled reachable diameter of only 3, so it cannot answer the planned deep-RPQ question.
- LiveJournal v3 is excluded because concurrently started servers collided. The clean v4 replacement is canonical.
- Below-2-MiB memory attempts failed during server startup because a 2 MiB reservation could not fit, not because either query variant failed.

## Interpretation layers

- `SOURCE-CONFIRMED`: current `FoundNodes.get` performs a newest-to-oldest history scan after active checks.
- `MEASURED`: large road gains, positive web-Stanford result, H3 equivalence, cit-Patents practical non-inferiority, unresolved gMark/LJ results, and workload-dependent memory cost.
- `DERIVED`: benefit grows with accumulated history-probe debt in the controlled chain and roads.
- `INFERRED`: C1 is a credible depth-sensitive optimization, not a universal speedup.
- `POLICY / MAINTAINER DECISION`: whether its headroom/allocation cost and unresolved LiveJournal risk are acceptable.

## Canonical derived-artifact hashes

```text
cit-Patents-v1.json                        F94A4DDBBAD3025CB16E5FFF34C5F93E80FB3285F4099E133F314D10555BA57F
gmark-test-30k-v2.json                     BC9268DECAD3AADABDCFB74CD3A061BB5B73E22B949681780D7372892BE8E36F
hetionet-H3-v3-noninferiority.json         C205B669C23C939BB50292B106BD6094DC8DE6F05A82897A15C43D6B27D6325A
LiveJournal-shallow-v4-noninferiority.json 5637816AA7BE5FEF97C666422F81FE51633AC054553D37DA799A87A926E51694
hetionet occupancy raw JSONL               BE82CF1DE14A842AC33E07E1E0B6B78FA55B2FC35F5F54432092058ED885604B
```

Derived JSON/Markdown is under `D:/dev/neo4j-research/artifacts/ppbfs/reports/external-validation/`; immutable raw runs are under the sibling `runs/external-validation/` tree.
