# Final PPBFS product-state repository Pareto analysis

| Dimension | B0 | C1 | C2 | C3 |
| --- | --- | --- | --- | --- |
| deep latency | reference; depth-linear lookup | PA 4.373x, CA deep 5.289x | slower screening than C1 | not timed after gate |
| shallow/common latency | reference | CA +26.9%, web +6.4%; Hetionet H3 equivalent +/-5%; LiveJournal and Skitter unresolved | 93.3% C1 retention in screen | not qualified |
| topology generality | current behavior | positive on 2 roads + web; Hetionet equivalence; cit/gMark/LJ unresolved | PA screen only | not qualified |
| lookup complexity | linear in history depth | expected constant | expected constant | expected constant |
| PROFILE memory | reference | -5% to +10% observed range | near B0 in PA profile | conceptually adaptive |
| fixed-limit compatibility | best | some 0–1.96% shifts; PA d500/d772 and CA d800 equal | PA d250 94 vs 92 MiB | fails PA 92 MiB |
| sampled allocation | reference | +5.5–7.0% total | not qualified | not qualified |
| GC | reference | 23 vs 25 GCs; similar absolute pause | not qualified | not qualified |
| correctness | upstream | passed focused/differential/lifecycle suite | passed focused suite | passed focused suite |
| implementation | existing complex history lookup | simplest direct candidate | nested state-major maps | adaptive bucket machinery |
| maintainability | known | straightforward query-local canonical index | worse locality/overhead | most complex |

## Frontier

`DERIVED`: B0 and C1 form the measured Pareto frontier. B0 wins strict memory compatibility; C1 wins latency/lookup complexity. C2 is dominated by C1 on the observed memory and timing dimensions. C3 did not demonstrate a boundary win and adds complexity.

## Decision layers

- `SOURCE-CONFIRMED`: the history scan remains upstream.
- `MEASURED`: C1 removes the hotspot, replicates across road/web topology families, is equivalent within +/-5% on Hetionet H3, and costs modest memory/allocation. LiveJournal and as-Skitter remain unresolved controls.
- `DERIVED`: the depth response matches the predicted lookup complexity.
- `INFERRED`: C1 is a favorable enough trade-off to merit maintainer review, but not proven universally production-preferable.
- `POLICY / MAINTAINER DECISION`: whether query-memory headroom and the chosen Neo4j collection/ownership model are acceptable.

## Classification

Not `STRONG GO`: LDBC was not useful/prepared, contrasting-topology tracked memory reaches about +10%, and the fixed-limit regression is real.

Not `MIXED`: the depth-dependent benefit independently replicates, one heterogeneous common workload establishes +/-5% equivalence, correctness holds, and neither memory nor GC shows a broad blow-up. The unresolved LiveJournal control is disclosed rather than treated as favorable evidence.

`GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`

## C4-DCRI addendum (2026-09-06)

C4 adds a third Pareto-relevant point. It retains at least 94.4% raw / 96.0% logarithmic C1 deep speed across the two road aggregates, eliminates measured canonical-bucket duplication, reduces allocation relative to C1, and significantly improves the valid LiveJournal control versus C1. It does not uniformly dominate B0 or C1: two d250 memory boundaries move by +2 MiB, deep-road allocation remains above B0, and ownership/merge lifecycle is more complex than C1.

The C4-specific classification is:

`C4 PARTIAL — BETTER RESOURCE PROFILE, BUT TRADE-OFF REQUIRES MAINTAINER CHOICE`

This addendum does not retract the earlier C1 evidence or its maintainer-discussion recommendation. It changes the question from “B0 or C1?” to “is C1's simplicity or C4's improved resource/common-case balance preferable?”
