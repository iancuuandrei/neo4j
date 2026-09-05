# LiveJournal external result

## Timing and uncertainty

`MEASURED`: the clean ten-paired-fork extension produced an aggregate 0.948x B0/C1 ratio, 95% CI `[0.782,1.149]`, TOST-style 90% CI `[0.811,1.108]`. Classification: `INCONCLUSIVE`.

The 0.948x point estimate suggests about 5.2% C1 overhead, but the uncertainty spans meaningful regression and improvement. It is neither evidence of neutrality nor proof of a material regression. The earlier five-fork result, 0.942x `[0.821,1.079]`, reached the same unresolved conclusion.

## Memory and allocation support

Representative d3 PROFILE memory was 0.18 MiB B0 versus 0.19 MiB C1. Both variants passed every valid 2–6 MiB limit. A lower follow-up is excluded at infrastructure startup because the system database's first 2 MiB reservation cannot fit below 2 MiB.

Supporting short JFR estimated 206.6 versus 207.8 MiB thread allocation (+0.6%) and 2 versus 3 GC events; this single run is not a regression test. LiveJournal remains the principal unresolved common-case risk.
