# Updated candidate matrix

The canonical matrix is [`reports/CANDIDATE_MATRIX.md`](reports/CANDIDATE_MATRIX.md). This named deliverable exists to make the continuation result explicit without erasing the original dated B0/C1/C2/C3 records.

| Candidate | Deep latency | Strict boundary | Allocation/GC | Pareto status |
| --- | --- | --- | --- | --- |
| B0 | reference | best | reference | frontier |
| C1 | PA 4.373x; CA deep 5.289x | small failures at selected points | +5.5–7.0% allocation; no GC-count increase | frontier; discuss |
| C2 | slower than C1 screen | worse than C1 at PA d250 | not qualified | dominated currently |
| C3 | not qualified | did not recover 92 MiB | not qualified | non-selected |

Final classification: `GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`.
