# External-validation semantics matrix

## Qualified workloads

| Workload | Native semantics retained | Physical operator | Evidence role | Status |
| --- | --- | --- | --- | --- |
| FinBench official TCRs | official Cypher unchanged | VarLengthExpand / ShortestPath / excluded | anti-cherry-picking qualification | non-PPBFS |
| Hetionet H1 | `G_I_G` | StatefulShortestPath(All, Trail) | simple typed path | timed |
| Hetionet H2 | `G_I_G|G_C_G|G_R_G` | StatefulShortestPath(All, Trail) | typed alternation | timed |
| Hetionet H3 | four gene participation types | StatefulShortestPath(All, Trail) | repeated relation family | timed + occupancy + JFR |
| Hetionet H4 | compound/gene/disease relation family | StatefulShortestPath(All, Trail) | cross-domain path | timed |
| Hetionet H5 | all 24 native relationship types | StatefulShortestPath(All, Trail) | broad heterogeneous stress | plan + bounded occupancy only |
| gMark | four generated predicate types | StatefulShortestPath(Into, Trail) | shallow generated RPQ control | timed; depth sweep unavailable |
| cit-Patents | directed citation edges | StatefulShortestPath(Into, Trail) | branching/reconvergent DAG-like graph | timed + JFR |
| LiveJournal | directed social edges | StatefulShortestPath(All, Trail) | high-fanout shallow control | timed + JFR |

H5 formal latency is excluded in full: baseline-only screening found repeated 10–16 second cases,
making the frozen matrix disproportionate. No C1 timing was observed and no fast subset was selected.
The predetermined first manifest case is used only for structure/occupancy evidence.

Every formal B0/C1 timing row passed exact returned path-length equivalence. `PROFILE` or `EXPLAIN`
operator qualification is separate from unprofiled latency measurement.

## Exclusions and evidence boundary

FinBench cannot support a direct C1 claim because its official queries bypass PPBFS. H5 and the gMark depth shortfall are preserved as limitations; neither is replaced by a post-result favorable workload.
