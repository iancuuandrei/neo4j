# C4-DCRI activation analysis

## Evidence boundary

`SOURCE-CONFIRMED`: C4 activates once, at a non-empty frontier retirement after eight history levels. It transfers the first retiring outer map, transfers unique later buckets, merges repeated-node buckets while preserving `NodeState` identity, never backfills the frozen prefix, and never allocates a canonical bucket. The frozen threshold was selected before formal timing.

`MEASURED`: a separate diagnostic pass ran every frozen C4 manifest with one warmup and one measured execution. Timing evidence comes from diagnostics-disabled runs. Raw JSONL is under `D:/dev/neo4j-research/artifacts/ppbfs/runs/c4/activation-v1/`.

| Workload | executions | activated | activation depth | transfers | merges | canonical allocations | max retired nodes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| roadNet-PA | 14 | 14 | 10 | 3,012,154 | 0 | 0 | 916,865 |
| roadNet-CA | 28 | 28 | 10 | 11,286,354 | 0 | 0 | 1,528,487 |
| web-Stanford | 26 | 18 | 10 | 165,354 | 0 | 0 | 26,778 |
| as-Skitter | 26 | 14 | 10 | 1,024 | 2 | 0 | 258 |
| cit-Patents | 20 | 8 | 10 | 15,376 | 2 | 0 | 3,679 |
| Hetionet H1-H5 | 320 | 0 | n/a | 0 | 0 | 0 | 0 |
| LiveJournal `SHORTEST 2` | 48 | 0 | n/a | 0 | 0 | 0 | 0 |
| gMark shallow | 108 | 0 | n/a | 0 | 0 | 0 | 0 |

`MEASURED`: roadNet-PA performed 12,743,342 post-activation lookups; roadNet-CA 47,689,176; web-Stanford 1,286,536. Frozen-prefix probes per miss remained bounded by eight. Direct retired-index hits were 3,634,526, 13,583,958, and 309,348 respectively.

`DERIVED`: the diagnostics establish the intended asymptotic transition. History never grows beyond eight levels after activation, and each post-activation miss performs no more than eight frozen-prefix probes regardless of final depth.

`INFERRED`: fixed depth eight is conservative on shallow heterogeneous workloads because it never activates there. It activates late in d10 road queries and in some low-diameter controls; those activations are small but explain why C4 is not byte-for-byte B0 for every control.

`LIMITATION`: the map API exposes no stable resize callback. Exact resize counts and largest single resize are therefore not measured. JFR backing-array attribution, transfer totals, and maximum retired-index size are the bounded evidence for outer-map growth.

