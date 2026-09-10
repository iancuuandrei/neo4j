# Execution model fix for unsupported runtime fallback (#13937)

## Status

**Qualified; upstream PR [neo4j/neo4j#13972](https://github.com/neo4j/neo4j/pull/13972) open against `2026.08`, fixing issue [neo4j/neo4j#13937](https://github.com/neo4j/neo4j/issues/13937).** Implementation branch:
[`investigate/13937`](https://github.com/iancuuandrei/neo4j-contributions/tree/investigate/13937),
independently rooted at upstream `2026.08` (`736cad02a36bb4a0d32c1064f44768339c814269`).
Root cause confirmed end-to-end; minimal fix implemented; behavioral red/green tests green; full targeted suite matrix green; contribution published; awaiting upstream review.

## Problem

Community Edition accepts an explicit `CYPHER runtime=pipelined` with fallback enabled, but:

```
planner uses BatchedSingleThreaded cost assumptions
actual whole-query executor = CommunitySlottedRuntime / Volcano
```

Cartesian products are therefore costed as roughly `ceil(N / batchSize) * RHS-cost`
while the slotted executor pays `N * RHS-cost`. On the exact #13937 query (small nested
`COUNT` with CSV, `SKIP 0`, and relationship-vector search over disconnected components)
this selects the opposite Cartesian orientation, which is catastrophically expensive
under row-at-a-time execution.

## Solution

One match arm in `TransformingPlanner.doPlan` (`CypherPlanner.scala`): resolve explicit
runtime hints through the already-selected runtime's `correspondingRuntimeOption`
(the same abstraction the `default` branch already trusted) before choosing the
planner execution model:

```scala
case explicit =>
  runtime.correspondingRuntimeOption.getOrElse(explicit)
```

No new API, no executor/planner/cost-model changes. Genuine batched runtimes report
batched corresponding options, so Enterprise pipelined/parallel planning is unaffected;
fallback behavior and notifications are unchanged.

## Core invariant

When the compiler already knows before logical planning that an explicitly requested
runtime will execute through another whole-query runtime, the planner must use physical
execution assumptions compatible with that executable runtime. The fix does not predict
arbitrary later per-plan fallbacks.

## Results

Exact #13937 setup/query on embedded Community 2026.08 (30 s transaction timeout):

```
baseline slotted:             ~0.86 s, returns x = 1
baseline runtime=pipelined:   ~8.4 s,  returns x = 1  (same result, opposite plan)
patched runtime=pipelined:    ~0.32-0.46 s, plan structurally identical to slotted
```

Causal chain, all executed (slotted executor held constant throughout):

- pipelined EXPLAIN orientation != slotted EXPLAIN orientation (both report `Runtime SLOTTED`);
- `batchSizePreset=disabled` (B=1) restores the good orientation; small/medium/large keep the bad one;
- PROFILE: bad plan drives the Expand+CSV branch 68x (AllNodesScan 4624 = 68x68 rows, Expand 8704 rows)
  vs good plan driving the vector-Apply branch 128x (8448 = 128x66 rows);
- post-fix pipelined EXPLAIN is string-identical to slotted; all batch presets identical.

SKIP 0 is plan-shape preservative (not itself slow; `Skip` is not unnestable, verified row-preserving).
Relationship-vector search is an amplifier, not the driver (bad plan issues 68 seeks yet is 10x slower;
good plan issues 8448 seeks yet is fast).

## Correctness

- Behavioral planner regression test through real pre-parse → `parseAndPlan`, asserting the
  effective execution model: fails on baseline with `BatchedSingleThreaded(128,1024)` /
  `BatchedParallel(128,1024,false)` instead of `Volcano` for pipelined/parallel; 4/4 green patched.
- Exact-reproduction integration test: pipelined plan skeleton differs from slotted on baseline
  (red), identical patched (green); both complete with `x = 1`; fallback notification preserved.
- Suites: `CommunityRuntimeExecutionModelTest` 4/4, `CypherPlannerTest` 4/4, `CacheKeyTest` 5/5,
  `PreParserTest` 54/54, `CardinalityCostModelTest` 52/52,
  `CartesianProductPlanningIntegrationTest` 15/15, `RuntimeUnsupportedNotificationTest` 2/2,
  `CommunityPipelinedFallbackPlanningIT` 1/1.

## Status

Production implementation qualified on Neo4j 2026.08. Upstream:
issue neo4j/neo4j#13937, PR neo4j/neo4j#13972 (base `2026.08`, head `investigate/13937`).
