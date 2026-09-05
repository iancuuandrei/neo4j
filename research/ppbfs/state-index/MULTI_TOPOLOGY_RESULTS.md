# Multi-topology B0/C1 results

## Protocol

`MEASURED`: each dataset used five paired fresh-JVM forks. roadNet-CA used one warmup and three repetitions per case; web-Stanford and as-Skitter used two warmups and ten repetitions. The statistical unit is the paired JVM-fork median, not repetitions inside a JVM. Every included query returned equal results and PROFILE-confirmed `StatefulShortestPath(Into, Trail)`.

| Dataset | Role | Distances | Cases | C1 speedup (95% CI) |
| --- | --- | --- | ---: | ---: |
| roadNet-PA | original road replication | 250, 500, 772 | 3 | 4.373x [2.126, 8.993] |
| roadNet-CA | independent road replication | 10–800 | 14 | 2.340x [2.268, 2.414] |
| web-Stanford | directed/reconvergent contrast | 2–140 | 13 | 1.079x [1.060, 1.098] |
| as-Skitter | low-diameter/high-fanout control | 2–30 | 13 | 1.014x [0.989, 1.039] |

## Distance response

roadNet-CA reproduces the predicted depth response: 1.118x at d10, 1.089x at d25, 1.227x at d50, 1.738x at d100, 3.407x at d250, 5.213x at d500, and 8.328x at d800. The d250+ aggregate is 5.289x `[4.808,5.818]`.

web-Stanford is neutral at d2–10, then 1.057x at d25, 1.130x at d50, 1.196x at d100, and 1.269x at d140. as-Skitter remains statistically neutral overall; only d20 shows a small isolated 1.047x gain.

`INFERRED`: benefit follows effective history depth rather than merely graph size. The low-diameter control provides no evidence of a broad latency penalty.

## Selection and limits

Source/target generation is deterministic and recorded in the checked-in manifests. Extreme pairs were not filtered by observed candidate speed. The web graph has only one selected d140 case because the survey found one valid target at that exact deepest bucket; the analyzer keeps cases distinct rather than collapsing same-distance pairs.

`NOT RUN`: LDBC SNB SF10, because no prepared store/natural qualifying workload existed. This prevents a `STRONG GO` classification but does not erase three independent topology results.

Raw analysis roots are under `D:/dev/neo4j-research/artifacts/ppbfs/runs/pareto/` for `roadNet-CA-b0-vs-c1-v1`, `web-Stanford-b0-vs-c1-v1`, and `as-Skitter-b0-vs-c1-v1`.
