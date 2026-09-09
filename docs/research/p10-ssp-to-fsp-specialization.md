# P10: SSP → FSP specialization for simple QPP node groups

## Objective
Expand Neo4j's `StatefulShortestPath` → `FindShortestPaths` specialization with maximum real coverage and minimum new machinery, preserving the invariant that traversal remains ordinary shortest-path BFS (`V`, no `V×Q` product state).

## Exact base
- `neo4j/neo4j` branch `2026.08`, SHA `736cad02a36bb4a0d32c1064f44768339c814269`, version `2026.08.0`.

## Eligibility boundary (final)
FSP iff ALL hold: selector `SHORTEST 1` / `ALL SHORTEST` (k=1); target bound; owned SPP (solved-state delta) exactly one with one relationship; single var-length (min 0/1, no groups) OR single-rel QPP (min 0/1) with every node group reconstructible; predicates inlineable; no shard property access; path mode preserved. Kept unsupported: min>1, k>1, unbound target enumeration, multi-rel QPPs, path-global predicates. Grouped undirected QPPs stay SSP (see counterexample).

## Mathematical argument
- SSP searches `V×Q`; FSP searches `V` + shortest-path state. Specialize iff automaton coordinate `q` is redundant.
- Single-rel QPP `(x)-[r]->(y)` repeated k times contributes exactly one left/right/rel binding per edge. For path `p=(v0,e1,v1,…,ek,vk)`: `left=nodes(p)[0..k-1]`, `right=nodes(p)[1..k]`, `rels=relationships(p)`; empty when k=0. No segmentation ambiguity (vs multi-rel QPPs where parity matters). Reconstruction is O(k) output work after discovery.
- Owned SPP: `planStatefulShortest` does `solveds(source).amend(addSelectivePathPattern(spp))`, so `own = current.solvedSPPs − source.solvedSPPs`; rewrite iff `own == {spp}` with one relationship. Pure planner change, `SameId` preserved.

## Candidate A: owned-SPP delta
Planner-only. Previously blocked stacked shape `WITH * MATCH p1=MATCH p2=` (same horizon, same endpoints) gave SSP+FSP; patched gives FSP+FSP (proven by EXPLAIN on patched build). No traversal/runtime change.

## Candidate B: node-group reconstruction
Bounded `FindShortestPaths` output extension: `leftNodeGroup/rightNodeGroup: Option[LogicalVariable]`; slot allocation `CTList(CTNode)`; interpreted (`ShortestPathPipe`) + slotted (`ShortestPathSlottedPipe`) materialize via shared `ShortestPathNodeGroups` helper (prefix/suffix of `path.nodeIds()`). Orientation always query order (left outer → right outer); FSP path is materialized in that order so no `reverseGroupVariableProjections` needed. Verified from planner/runtime code, not intuition.

## Undirected same-node counterexample → directed-only guard
Randomized differential (2000 trials, patched SSP-flag-OFF oracle vs FSP-flag-ON) found 8 failures, all undirected `(c)-[r]-(d)+` src==tgt: SSP returns len-1 self-loop, FSP returns empty (GROUP) or longer path / `EntityNotFound: NODE -1`. Minimized single-node self-loop: undirected QPP `RETURN p` (groups eliminated) already plans FSP on unpatched and returns 0 vs SSP 1 — pre-existing FSP/SSP discrepancy, not reconstruction error (within-SSP `c vs nodes(p)[0..-1]` holds 140/140). Since planner cannot prove runtime source≠target for distinct outer vars, P10 guards `dir==BOTH` with groups → SSP. Directed OUTGOING/INCOMING unaffected (500/500 + 1500/1500 pass after guard).

## Correctness results (patched 2026.08 build, not slice proxies)
- Planner matrix 9/9: directed left/right/both/rel+node/path+groups → `ShortestPath`; multi-rel/min>1/k>1 → SSP; stacked → FSP+FSP.
- Within-SSP reconstruction 140/140 (all dirs/quants/pairs/selectors).
- Randomized differential 1500/1500 (flag OFF vs ON, full rows incl multiplicity; single-pick tie-breaks compared by distance).
- Suites: `StatefulShortestToFindShortestIntegrationTest` 65/65 (59 existing + 6 new P10), `SlotAllocationTest` 50/50, `LogicalPlanToPlanBuilderStringTest` 217/217, `ShortestPathNodeGroupsTest` 4/4. Legacy `FindShortestPaths/ShortestPathPlanningIntegrationTest` nested-predicate cases fail 5+6 identically on pristine 2026.08 in this environment (baseline, anon-naming diffs); 9 additional SSP-planning cases assert pre-P10 SSP behavior for shapes P10 intentionally converts (covered positively by new rewrite tests).

## Benchmark methodology
Same patched build, same query/data/params/runtime/JVM flags; SSP via `gpm_shortest_to_legacy_shortest_enabled=false`, FSP via `true` (+`INTO_ONLY` for point-to-point). Fresh JVM per fork (each `java` invocation), 50 warmup + 100 measured per workload per fork, 10 paired forks; ratio `R=T_SSP/T_FSP` per fork, median/min/max. Plans verified via EXPLAIN per workload.

## Measured results (10 paired forks)
- chain16 1.89x, chain64 1.92x, diamond 1.42x, tree-b4-d6 5.49x, stacked SSP+SSP vs FSP+FSP 6.47x; A-specific unpatched SSP+FSP 0.638ms vs patched FSP+FSP 0.435ms = 1.47x.
- Group tax ~noise (+1–10%, +µs absolute); legacy FSP median 1.00x.
- Structural story: FSP avoids NFA/NodeState/signposts/propagation/product-graph work; groups add O(k) list build only.

## Killed alternatives
Lower-bound>1 (needs `(v,min(d,m))` acceptance state), k-shortest (different algorithm), unbound-target enumeration, multi-rel QPPs (need `q`), path-global predicates (shortest-then-filter unsound), PPBFS-in-FSP, cost model (no crossover observed).

## Verdict: STRONG
Both candidates survive bounded, exact, and measurably useful. Headline: directed single-rel QPP node groups + owned-SPP delta.
