# gMark external result

## Generator identity and limitation

The original gMark generator at pinned commit `77be5b620375f2fabf1b14204d1d1f899d8f8425` produced 27,038 materialized nodes, 36,088 edges, and four predicate types for the requested 30k instance and seed 20260905. Predicate identities were preserved as relationship types.

`MEASURED`: its sampled reachable diameter was only 3. The formal all-predicate PPBFS workload therefore contained d1/d2 cases, not the planned deep matrix. Five paired forks produced 1.246x `[0.578,2.685]`, TOST-style 90% interval `[0.691,2.247]`: `INCONCLUSIVE`. The width reflects severe between-fork variability.

## Memory boundary

Both variants passed every 2–6 MiB fixed-limit point. Two MiB is the lowest server-usable point in this setup; lower configurations fail at startup on Neo4j's 2 MiB tracked reservation. This workload does not falsify C1, but it also supplies no deep controlled evidence.
