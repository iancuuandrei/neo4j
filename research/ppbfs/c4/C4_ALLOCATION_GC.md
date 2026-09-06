# C4-DCRI allocation and GC analysis

## Protocol

`MEASURED`: JFR attached to the Neo4j database JVM. The deep-road recordings use one warmup plus three executions; external supporting recordings use one warmup plus ten executions. Exact B0/C1 recordings at the frozen SHAs are reused where their protocol matches; roadNet-CA and as-Skitter were freshly recorded three-way. Thread-allocation deltas are the primary total-byte measure; sampled allocation sites are explanatory estimates.

| Workload | B0 thread allocation | C1 | C4 | C4 vs B0 | C4 vs C1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| roadNet-PA d10/d250/d772 | 7.166 GB | 7.562 GB | 7.438 GB | +3.8% | -1.6% |
| roadNet-CA d500 | 8.519 GB | 9.260 GB | 9.007 GB | +5.7% | -2.7% |
| as-Skitter d30 | 343.6 MB | 357.3 MB | 342.8 MB | -0.2% | -4.1% |
| Hetionet H3 d6 | 346.6 MB | 358.1 MB | 335.5 MB | -3.2% | -6.3% |
| cit-Patents d12 | 325.0 MB | 335.6 MB | 318.3 MB | -2.0% | -5.1% |
| LiveJournal d3 | 216.6 MB | 217.9 MB | 216.0 MB | -0.3% | -0.9% |

`MEASURED`: C4 is below C1 in every comparable recording. It is effectively B0-level on the four shallow/moderate external controls, but remains 3.8-5.7% above B0 on the deep road workloads. It therefore materially reduces C1's structural allocation without reaching the preferred within-about-2% target everywhere.

GC is descriptive because each row is one recording. C4 recorded 26 collections / 1.499 s pause on roadNet-PA, 33 / 4.132 s on roadNet-CA, and two or three collections with 23-35 ms pause on the external cases. No major-GC storm or qualitatively new allocation site appeared.

`MEASURED`: on roadNet-CA, sampled `HeapTrackingLongObjectHashMap` backing arrays account for about 560 MB in C4 versus 580 MB in C1 and 134 MB in B0. On roadNet-PA C4 they account for about 612 MB. This is consistent with coalescing rather than canonical-bucket duplication.

`INFERRED`: ownership transfer succeeds at removing most C1-only bucket duplication, while outer-map growth remains the dominant incremental structure on deep searches. The evidence does not justify sharding or another representation in this experiment.

Raw recordings and analyses are under `D:/dev/neo4j-research/artifacts/ppbfs/runs/c4/jfr-v1/`, `jfr-v2/`, and `jfr-v3/`; the matching B0/C1 PA and external references remain in the prior append-only JFR directories.

