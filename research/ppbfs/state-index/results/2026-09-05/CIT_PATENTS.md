# cit-Patents external result

## Timing and practical classification

`MEASURED`: five paired JVM forks across deterministic directed pairs at d1/d2/d4/d8/d12 produced an aggregate 1.220x B0/C1 speedup, 95% CI `[0.977,1.524]`, TOST-style 90% CI `[1.029,1.448]`. Classification: `PRACTICALLY NON-INFERIOR`. The two-sided interval does not establish a conventional positive effect, while the practical interval clears the -5% regression margin.

The d12 estimate is 1.278x `[1.022,1.597]`. Exact result lengths matched and PROFILE selected `StatefulShortestPath`. Representative d12 operator memory was 7.29 MiB B0 versus 7.78 MiB C1. Both first passed the tested fixed-limit curve at 8 MiB.

## Supporting profile evidence

Supporting JFR for one short representative run estimated 309.9 MiB versus 320.0 MiB thread allocation (+3.3%); GC counts were 3 versus 2. This is directional support only, not a fork-level allocation estimate.
