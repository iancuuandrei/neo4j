# Product-state occupancy analysis

## Instrumentation boundary

Occupancy was measured with research-only commit `3b0413e37db` on branch `benchmark/ppbfs-external-observability`. It adds an opt-in hook and is deliberately absent from B0, C1, and the clean contribution branch. Raw JSONL is append-only under `D:/dev/neo4j-research/artifacts/ppbfs/runs/external-validation/hetionet-occupancy-v1/`.

Definitions: `U` is unique `(node,state)` product states, `N` distinct data nodes, `|Q|` NFA states, and `rho = U/(N*|Q|)`.

| Workload | |Q| | median U | median N | median rho | median k | p95 k | depth | history probes | lookups |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| H1 | 4 | 16,442 | 5,477.5 | 0.75 | 3 | 3 | 3 | 23,568 | 16,528 |
| H2 | 4 | 3,969 | 1,311.5 | 0.75 | 3 | 3 | 3 | 11,100.5 | 4,159.5 |
| H3 | 4 | 24,888 | 7,714 | 0.79 | 3 | 4 | 4 | 67,529 | 60,577 |
| H4 | 4 | 3,811 | 1,267.5 | 0.75 | 3 | 3 | 3 | 7,649 | 4,075.5 |
| H5 representative | 4 | 7,329 | 2,414 | 0.76 | 3 | 3 | 3 | 11,302 | 53,164 |

`MEASURED`: these Hetionet queries have high per-node NFA-state occupancy despite shallow history. H3, the largest measured product-state set, is equivalent within +/-5% after ten paired forks.

`INFERRED`: occupancy alone is not a predictor of speedup. The strongest gains occur when lookup attempts also traverse deep retained history; high rho with shallow history can be close to parity. This supports a debt model involving lookup count times history-probe depth rather than `U`, `N`, or rho alone.

`LIMITATION`: occupancy was measured only on the research hook build and Hetionet. The available aggregate points are insufficient for a defensible multivariate regression; no correlation coefficient is promoted as causal evidence. Controlled chain counts and source/JFR attribution remain the causal evidence.

