# Repeated nested COUNT evaluation in optional matches (#13924)

## Status

**Submitted for upstream review as [neo4j/neo4j#13973](https://github.com/neo4j/neo4j/pull/13973)**
against upstream issue [neo4j/neo4j#13924](https://github.com/neo4j/neo4j/issues/13924).
Clean implementation branch:
[`fix/13924-optional-count-hoist`](https://github.com/iancuuandrei/neo4j-contributions/tree/fix/13924-optional-count-hoist),
one commit over upstream `2026.08` (`bbf3b91f46a`).
Prior runtime-experiment harness (kept, untouched):
[`research/13924-2026.08`](https://github.com/iancuuandrei/neo4j-contributions/tree/research/13924-2026.08).

## Problem

An outer-only nested `COUNT { ... }` in an `OPTIONAL MATCH ... WHERE` clause
was solved inside one Cartesian side of the OPTIONAL RHS even though all of
its dependencies were already available from the outer input. Unrelated RHS
pattern expansion therefore multiplied nested execution: for the issue shape,
18 legitimate outer contexts became 18 × 221 = 3,978 nested COUNT evaluations
before the remaining RHS side expanded candidates to 509,184 rows. Each nested
evaluation repeats the full `LOAD CSV` lifecycle (file open, URI parse, ~2 MB
seeker buffer, parser/extractor setup, resource registration, parse, wide
slotted-row copy, metadata write, close), so the placement defect is
catastrophic for `LOAD CSV` while an equivalent in-memory `UNWIND` hides the
same multiplicity (the issue's control experiment).

## Solution

1. In `ApplyOptionalSolver`, partition OPTIONAL selections to predicates whose
   nested subqueries are exclusively outer-only (`dependencies ⊆ lhsSymbols`),
   read-only `CountIRExpression`s.
2. Solve each eligible COUNT on the outer LHS through the existing
   `SubqueryExpressionSolver` (one `Apply` per hoisted predicate, once per
   outer row); accept only outcomes that actually take the Apply path with no
   residual `CountIRExpression`/`NestedPlanExpression`.
3. Propagate the generated count variables into the OPTIONAL RHS through
   `argumentIds`; keep rewritten predicate application inside the RHS
   `Selection`, preserving null-extension semantics.
4. Restore original predicates into write-once `solveds` on a fresh plan id
   (`LogicalPlanProducer.fixupOptionalHoistedSolved`, mirroring
   `fixupTrailRhsPlan`), so `VerifyBestPlan` still sees the input query solved.

## Core invariant

Evaluation of the expensive nested expression moves outward; application of
the OPTIONAL predicate does not. RHS-correlated COUNTs, updating subqueries,
CASE-blocked (per-row nested pipe) contexts, and EXISTS/list mixtures keep
legacy in-RHS evaluation. No CSV content caching; no memoization;
correlation stays once per logical outer row.

## Results

Structural evaluation-count reduction by plan construction (not a measured
wall-clock speedup): 3,978 → 18 nested COUNT evaluations for the issue
cardinalities. Per-evaluation `LOAD CSV` cost is unchanged by design.

## Correctness

- 17 focused planner regression/negative-control cases (uncorrelated,
  outer-correlated, RHS-correlated-stays, mixed split, CASE-legacy,
  EXISTS-mixed, outer writes, dual COUNTs, headers, early termination,
  top-level CSV, UNWIND-only, scaling sweeps, Volcano/Batched agreement);
  the 3 hoisting tests fail on pristine baseline and pass patched.
- Structural assertions prove the COUNT `Apply` moves outside `Optional`
  while the `Selection` on the generated variable stays inside.
- Planner suites green: CardinalityCostModel 52/52, ConnectComponents 60/60,
  OptionalMatch IDP/Greedy 72, CardinalityIntegration 85, PropertyAccess 23,
  WithPlanning 48, SubqueryCall 121, Order IDP/Greedy 310, RemoteBatch 286,
  slotted-runtime 171/171, physical-planning 155/155.
- Baseline-equivalent failures reproduced identically with and without the
  patch (SubqueryExpression 23/185, EagerPlanning 2/101); not regressions.

## Status

Fix committed locally and submitted upstream; awaiting maintainer review.
Deliberately excluded from this patch (judged independent, unneeded for the
multiplicity collapse): a `LOADCSV_COST_PER_ROW` constant (no calibration
basis) and a LoadCSV pipeline-break change (would tax bulk imports).
