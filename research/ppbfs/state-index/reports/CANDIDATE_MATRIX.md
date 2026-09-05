# PPBFS state-repository candidate matrix

The original strict gate is preserved: C1, C2, and C3 fail at least one fixed limit passed by B0. The final engineering analysis treats this as one Pareto dimension rather than an automatic production veto.

| Candidate | Representation | Performance | Memory | Current status |
| --- | --- | --- | --- | --- |
| B0 | level-partitioned node-major history | depth-linear hotspot | best strict boundary | Pareto reference |
| C1 | canonical node→dense state array | strongest; cross-topology benefit | small/workload-dependent cost | primary maintainer-discussion candidate |
| C2 | canonical state→primitive node map | 89.9% overall / 85.6% deep C1 retention in PA screen | PA d250 first pass 94 vs C1 93/B0 92 MiB | dominated in current evidence; retain provenance |
| C3 | adaptive node-major tiny sparse→dense | not timed after strict gate | failed PA 92 MiB | non-selected; retain provenance |

## Pareto interpretation

B0 and C1 are non-dominated. B0 wins strict memory compatibility; C1 wins direct-lookup complexity and latency. C2 loses both latency and the tested memory boundary to C1. C3 did not demonstrate a memory advantage sufficient to justify its extra machinery.

C2/C3 are not absolute failures. They are negative experimental results for the tested representations and workloads.

## Conditional C4

A flat primitive `(nodeId,stateId)→NodeState` table remains a possible future candidate. It was not implemented because C1 evidence—not a fourth implementation—was the decision bottleneck, and Neo4j maintainers may prefer an existing internal collection or ownership model.

## Decision

`GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`
