# PPBFS direct product-state repository benchmark report

## Executive result

`MEASURED`: C1 removes the history-depth-linear `FoundNodes.get` hotspot and produces large, reproducible road-network gains, a smaller positive result on web-Stanford, and neutral behavior on the low-diameter as-Skitter control. It also has a real but workload-dependent tracked-memory/allocation cost.

The original acceptance contract rejected any candidate that failed a fixed memory limit passed by baseline. C1 therefore formally failed that gate. This continuation preserves that result and treats it as a measured compatibility trade-off, rather than equating it automatically with an unacceptable production regression.

**Final classification:** `GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`

This is permission to discuss the evidence and prepare a clean patch, not a claim that maintainers should accept C1.

## Evidence binding

```text
repository: neo4j/neo4j
upstream branch: 2026.07
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
B0 timing variant: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
C1 variant: caf33be9f5de2d8d7f4a291abd466cf808aaf801
runtime: Eclipse Temurin 21.0.12.1+1 LTS
OS: Windows 11 Pro, AMD Ryzen 9 9955HX, about 31.2 GiB RAM
server heap/page cache: 4 GiB / 8 GiB
bulky artifacts: D:/dev/neo4j-research/artifacts/ppbfs
```

The 2026-09-05 upstream refresh left `upstream/2026.07` at the same SHA. `FoundNodes.get` still scans `history` newest-to-oldest. GitHub searches found no open or recent merged overlapping implementation.

## Source and controlled causal evidence

- `SOURCE-CONFIRMED`: `BFSExpander.encounter` calls `FoundNodes.get(nodeId,stateId)`; the latter checks active structures and then scans historical levels.
- `DERIVED`: one accepted new product state per depth makes lookup bookkeeping quadratic in depth; a canonical direct repository makes lookup expected linear in the number of lookup attempts.
- `MEASURED`: at chain depth 4096, B0 performed 8,382,465 historical probes for 4,097 accepted lookup attempts.
- `MEASURED`: B0 JFR placed `FoundNodes.get` in 468/919 execution samples (50.9%); C1 reduced this to 2/817 (0.24%).

## Timing protocol

Every formal topology run used five independent paired JVM forks, deterministic within-pair ordering, complete warmup, medians within each fork, paired log ratios, and two-sided 95% Student-t intervals. PROFILE was used only to verify the physical operator, DB hits/results, and tracked memory. Headline latency is from normal unprofiled execution. All included cases returned identical result lengths and executed `StatefulShortestPath(Into, Trail)`.

## Multi-topology timing

Speedup is B0 time divided by C1 time.

| Dataset / scope | Cases | Geomean speedup | 95% CI |
| --- | ---: | ---: | ---: |
| roadNet-PA, depth 250/500/772 | 3 | 4.373x | [2.126x, 8.993x] |
| roadNet-CA, all depths 10–800 | 14 | 2.340x | [2.268x, 2.414x] |
| roadNet-CA, depth 250–800 | 6 | 5.289x | [4.808x, 5.818x] |
| roadNet-CA, depth 10–100 | 8 | 1.269x | [1.177x, 1.369x] |
| web-Stanford, depth 2–140 | 13 | 1.079x | [1.060x, 1.098x] |
| as-Skitter, depth 2–30 | 13 | 1.014x | [0.989x, 1.039x] |

`MEASURED`: roadNet-CA independently replicates the depth-dependent effect: 1.118x at d10, 1.738x at d100, 3.407x at d250, 5.213x at d500, and 8.328x at d800. web-Stanford grows from neutral at d2–10 to 1.269x at d140. as-Skitter is neutral overall, answering the low-diameter regression question favorably.

`NOT RUN`: LDBC SNB SF10. No prepared SF10 store or naturally qualifying PPBFS manifest existed, and forcing a synthetic StatefulShortestPath shape would not provide the intended realistic-regression evidence.

## Tracked-memory evidence

PROFILE `Memory` is operator high-water accounting, not process RSS or total allocation. C1 deltas were:

- roadNet-PA: -2.91% to +1.46%; deep cases -0.84% to +0.34%;
- roadNet-CA: +0.18% to +2.18% through d500, then -0.10% at d800;
- web-Stanford: +5.80% to +9.94%;
- as-Skitter: mostly -5.08% to +0.38% through d12, then +8.80% to +9.70% at d20/d30.

Representative minimum-observed fixed limits:

| Dataset | Distance | B0 | C1 | Difference | Relative |
| --- | ---: | ---: | ---: | ---: | ---: |
| roadNet-PA | 100 | 14 MiB | 14 MiB | 0 | 0% |
| roadNet-PA | 250 | 92 MiB | 93 MiB | +1 MiB | +1.09% |
| roadNet-PA | 500 | 532 MiB | 532 MiB | 0 | 0% |
| roadNet-PA | 772 | 996 MiB | 996 MiB | 0 | 0% |
| roadNet-CA | 100 | 12 MiB | 12 MiB | 0 | 0% |
| roadNet-CA | 250 | 102 MiB | 104 MiB | +2 MiB | +1.96% |
| roadNet-CA | 500 | 1288 MiB | 1296 MiB | +8 MiB | +0.62% |
| roadNet-CA | 800 | 1732 MiB | 1732 MiB | 0 | 0% |

`MEASURED`: the boundary shift is neither exactly one MiB nor monotonic with depth. Neo4j rejects the next tracked reservation in discrete chunks (the PA d250 error reports 90 MiB live and rejection of the next 2 MiB). A small live structure delta can therefore cross a configured boundary by one or more MiB; the table must not be read as exact resident-memory overhead.

## Allocation and GC

The canonical JFR v6 run attached to the database JVM and exercised the same d10/d250/d772 manifest once for warmup and three measured repetitions.

| Metric | B0 | C1 | Delta / interpretation |
| --- | ---: | ---: | --- |
| thread-allocation delta | 7.166 GB | 7.562 GB | C1 +5.5% |
| weighted sampled allocation | 7.266 GB | 7.776 GB | C1 +7.0% |
| recording coverage | 63.62 s | 10.14 s | C1 finishes much sooner |
| GC collections | 25 | 23 | no increase |
| total GC pause | 0.934 s | 0.966 s | +0.032 s; similar absolute pause |

`MEASURED`: C1 allocates somewhat more total data but does not create a GC-count or absolute-pause explosion in this run. Its allocation rate is higher because roughly similar work is completed much faster; rate alone is not an overhead measure. Dominant sites in both variants are PPBFS `Lengths`, `HeapTrackingArrayList`, `NodeState`, and `TwoWaySignpost`; C1 additionally raises recorded long/object backing-array allocation in the canonical map.

JFR v1–v5 are retained but excluded from allocation conclusions because they attached to the Java launcher rather than the database JVM. This provenance error was detected from impossible near-zero allocation counts and corrected before v6.

## Correctness and maintainability

C1 passed focused canonical identity, historical lookup, bidirectional sharing, duplicate rejection, frontier retirement, interruption cleanup, generated differential tests, and the existing PPBFS suite. Its representation is the simplest candidate: one query-local node-major canonical repository using Neo4j tracked collections. C2 and C3 remain useful negative comparators but are not production candidates.

## Pareto conclusion

`INFERRED`: B0 and C1 are both Pareto-relevant. B0 minimizes tracked structural memory and preserves every tested fixed limit; C1 has the strongest latency and removes the causal hotspot at a small but real memory/allocation cost. C2 is dominated by C1 in current evidence; C3 did not recover the strict boundary.

`POLICY / MAINTAINER DECISION`: whether the observed headroom cost is acceptable for the deep StatefulShortestPath gains, and whether Neo4j has a preferred internal primitive repository/ownership model. The evidence warrants asking; it does not settle that policy question.

## Raw evidence hashes

```text
roadNet-CA analysis   439CF94617436DCAB5E5018C0B1272B2E40B3604D62233076C760F58396B51F9
web-Stanford analysis A78C67AED79CA08895712841AD4391653A5992D05C368E9EA37FA27E69144718
as-Skitter analysis   244E757C6B537BD9A4134C382F06EA7D17AD10992C8525A6E93C6DAF534727B4
B0 JFR v6             239E00711633C1B4D4D590F72F95209DC78AE2A490C93C835A5D91D999710B70
C1 JFR v6             EEA86C206CD285E49E3742388809696CA2BCC313E27B8672D07BAA1C7DE8952A
```

See `MULTI_TOPOLOGY_RESULTS.md`, `MEMORY_TRADEOFF_ANALYSIS.md`, `ALLOCATION_GC_ANALYSIS.md`, and `FINAL_PARETO_ANALYSIS.md` for separated evidence and judgment layers.
