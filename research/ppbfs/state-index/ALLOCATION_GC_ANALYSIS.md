# B0/C1 allocation and GC analysis

## Qualified recording

The qualifying run is JFR `pareto-v6`, attached to the database JVM running Temurin 21.0.12.1. It records one warmup and three measured repetitions of PA d10, d250, and d772. Both variants use the same store, manifest, heap/page-cache configuration, and query ordering seed.

JFR v1–v5 are append-only negative provenance. They attached to Neo4j's Java launcher process and produced impossible near-zero query allocation. They are excluded from all allocation conclusions.

## Results

| Metric | B0 | C1 | C1/B0 |
| --- | ---: | ---: | ---: |
| thread allocation delta | 7,165,600,488 B | 7,561,791,520 B | 1.055x |
| weighted sampled allocation | 7,266,389,104 B | 7,776,055,232 B | 1.070x |
| recording coverage | 63.616 s | 10.138 s | 0.159x |
| allocation rate (thread delta) | 112.6 MB/s | 745.9 MB/s | 6.62x |
| GC count | 25 | 23 | 0.92x |
| total GC pause | 0.934 s | 0.966 s | 1.034x |

`MEASURED`: two allocation estimators agree that C1 allocates about 5.5–7.0% more total bytes. C1's higher allocation rate is principally a throughput effect: the fixed workload completes far sooner. Absolute GC count does not increase and absolute pause differs by about 32 ms in this single paired profiling run.

## Allocation sites

Both variants are dominated by PPBFS work: `Lengths.ValidatingLengths` arrays, `HeapTrackingArrayList` objects/backing arrays, `NodeState`, and `TwoWaySignpost`. Weighted samples show C1 increases canonical-map backing arrays (`HeapTrackingLongObjectHashMap` long arrays about 126 MB→230 MB and object arrays about 85 MB→125 MB) and also changes list/signpost volumes.

`INFERRED`: the direct repository has visible structural cost, consistent with tracked-memory results. It does not shift allocation to an unrelated subsystem or cause a GC storm.

`LIMITATION`: this is one representative combined manifest, not independent per-case allocation distributions. Weighted sampling is an estimate. It is strong enough to identify direction and major sites, not exact per-object cost.

```text
B0 JFR SHA-256: 239E00711633C1B4D4D590F72F95209DC78AE2A490C93C835A5D91D999710B70
C1 JFR SHA-256: EEA86C206CD285E49E3742388809696CA2BCC313E27B8672D07BAA1C7DE8952A
B0 analysis SHA-256: ADB44A2BE66201FC0AE210B3544851544B63A3771E6D4B2EB0CE89AF483C5468
C1 analysis SHA-256: 345C8C300689229A6F303F16A1AF307B71402D865FAE38F6DD430DC99D147DA8
```
