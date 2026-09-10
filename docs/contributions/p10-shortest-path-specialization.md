# SSP → FSP specialization for simple QPP node groups (P10)

## Status

**Upstream PR [neo4j/neo4j#13970](https://github.com/neo4j/neo4j/pull/13970) open (base `2026.08`) for upstream issue [neo4j/neo4j#13969](https://github.com/neo4j/neo4j/issues/13969).** Clean implementation branch:
[`contrib/ssp-to-fsp-node-groups`](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ssp-to-fsp-node-groups),
independently rooted at upstream `2026.08` (`736cad02a36bb4a0d32c1064f44768339c814269`).
Research branch:
[`research/ssp-to-fsp-p10`](https://github.com/iancuuandrei/neo4j-contributions/tree/research/ssp-to-fsp-p10).
Research complete; implementation qualified; contribution published; upstream issue opened; upstream PR opened 2026-09-09.

## Problem

Neo4j falls back to `StatefulShortestPath` for simple shortest-path queries where
the remaining semantic obligation is deterministic node-group output, and
cumulative solved SPP metadata can also block otherwise valid specialization.

## Solution

1. identify the SPP owned by the current SSP through solved-state delta
   (`current − source == {spp}`, conservative fallback otherwise);
2. allow directed single-rel QPP node groups to be reconstructed natively by
   `FindShortestPaths` as prefix/suffix slices of the returned path.

## Core invariant

Traversal remains ordinary FSP; no NFA/product-state machinery is added.

## Results

```
chain16       1.89x
chain64       1.92x
diamond       1.42x
tree b4/d6    5.49x
A-specific    1.47x  (stacked SSP+FSP → FSP+FSP)
stacked case  6.47x  (SSP+SSP → FSP+FSP flag toggle)
reconstruction tax ~ noise
existing FSP ~ unchanged (1.00x)
```

Paired fresh-JVM measurements, same query/data/params/runtime, SSP via
`gpm_shortest_to_legacy_shortest_enabled=false` vs FSP via `true` (+`INTO_ONLY`).

## Correctness

- Adversarial matrix and randomized differential testing (1500/1500 on final
  guarded code, SSP oracle vs FSP, full rows incl. multiplicity).
- Planner qualification: directed node-group cases → `ShortestPath`;
  stacked eligible → FSP+FSP; multi-rel / min>1 / k>1 / grouped undirected → SSP.
- Directed-only group specialization after undirected same-node counterexample
  (pre-existing FSP/SSP discrepancy isolated; guard prevents enlarging it).
- Suites: `StatefulShortestToFindShortestIntegrationTest` 65/65 (59 existing +
  6 new P10), `SlotAllocationTest` 50/50,
  `LogicalPlanToPlanBuilderStringTest` 217/217,
  `ShortestPathNodeGroupsTest` 4/4.

## Status

Production implementation qualified on Neo4j 2026.08. Upstream:
issue neo4j/neo4j#13969, PR neo4j/neo4j#13970 (open).
