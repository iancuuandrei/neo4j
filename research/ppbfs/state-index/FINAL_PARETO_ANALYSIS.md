# Final PPBFS product-state repository Pareto analysis

| Dimension | B0 | C1 | C2 | C3 |
| --- | --- | --- | --- | --- |
| deep latency | reference; depth-linear lookup | PA 4.373x, CA deep 5.289x | slower screening than C1 | not timed after gate |
| shallow/common latency | reference | CA +26.9%, web +6.4%, Skitter neutral | 93.3% C1 retention in screen | not qualified |
| topology generality | current behavior | positive on 2 roads + web; neutral control | PA screen only | not qualified |
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
- `MEASURED`: C1 removes the hotspot, replicates across topology families, remains neutral on the control, and costs modest memory/allocation.
- `DERIVED`: the depth response matches the predicted lookup complexity.
- `INFERRED`: C1 is a favorable enough trade-off to merit maintainer review, but not proven universally production-preferable.
- `POLICY / MAINTAINER DECISION`: whether query-memory headroom and the chosen Neo4j collection/ownership model are acceptable.

## Classification

Not `STRONG GO`: LDBC was not useful/prepared, contrasting-topology tracked memory reaches about +10%, and the fixed-limit regression is real.

Not `MIXED`: the benefit independently replicates, the negative control is neutral, correctness holds, and neither memory nor GC shows a broad blow-up.

`GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`
