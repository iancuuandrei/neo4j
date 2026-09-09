# P7 final report — decouple path enumeration from tracing bookkeeping (WALK-only)

Verdict: **YES — workload-specific but worthwhile (WALK-only).**

Question: after a target saturates (no more result paths needed), PPBFS `PathTracer`
keeps enumerating every represented path combination purely to establish bookkeeping
side effects. Can that residual work be computed directly from the compact
signpost/length representation instead?

## Bases

```text
qualification base (2026.08 PPBFS implementation):
  736cad02a36bb4a0d32c1064f44768339c814269 (upstream/2026.08, verified 2026-09-09)
research branch: research/ppbfs-lab (this report; implementation uncommitted at writing)
clean contribution branch: contrib/ppbfs-walk-post-saturation-bookkeeping (from the SHA above)
local causal baseline for P1/P2: f213380f812b820a1b312e2ea52cb3d8f1931ccc (2026.07, unchanged)
```

2026.07 → 2026.08 changes exactly one ppbfs file (`Propagator`: test/hook getters);
`PathTracer`, `SignpostStack`, `SignpostTracking`, `TwoWaySignpost`, `NodeState`,
`Lengths` are identical, so the P7 analysis holds for both trees.

## Where post-saturation tracing actually occurs (verified in source)

- `PathTracer.fetchNextOrNull` keeps looping past `isSaturated()`: `validate()` side
  effects still execute, `setMinTargetDistance` still fires, pruning still applies.
  Saturation only suppresses *yielding* rows.
- `PGPathPropagatingBFS` sets global `targetSaturated` only for bound `intoTarget`
  searches, and then never re-enters the tracer: bound searches abandon tracing
  mid-DFS on saturation. **Bound targets have no residual enumeration.**
- Unbound searches imply `SearchMode.Unidirectional` (constructor precondition), so
  bidirectional search is always bound and likewise unaffected.
- Conclusion: P7 is an **unbound, unidirectional, multi-target optimization**.
  `SHORTEST K` vs `K GROUPS` and inlined vs non-inlined predicates change only
  saturation timing, not the exhaustion mechanics.

## Side-effect decomposition (from 2026.08 source)

- **structural**: `setMinTargetDistance` + target-signpost registration + its
  propagation scheduling. Fires unconditionally on first push, before validity.
- **(signpost/length)-dependent**: per-length validation marks,
  `setValidatedAtLength` + Validation scheduling, `popAndPrune`
  pruning/synchronization, `protectFromPruning`.
- **local-trace-dependent**: `canAbandonTraceBranch` duplicate rel/node early-out.
- **complete-path-history-dependent**: TRAIL/ACYCLIC leaf validity verdicts over
  `usedRelationships` / `usedNodes`; invalid traces skip validating their suffixes.
- **observational only**: `hooks.*`, row materialization.

WALK collapse (verified: `SignpostTracking.NO_TRACKING` is all no-ops;
`Lengths.NonValidatingLengths` has seen ≡ validated, so `popAndPrune` never prunes
and tracing adds no seen lengths): **the residual WALK bookkeeping is purely
structural.** Everything else on the list is absent in WALK.

## Why only WALK is optimized

TRAIL validity depends on `(NodeState, sourceLength, usedRelationships)` and
ACYCLIC on `(NodeState, sourceLength, usedNodes)`. Those histories are
exponential: two traces can reach the same `(NodeState, sourceLength)` with
different histories and opposite validity (demonstrated in the executable model:
a history-blind cache reuses a valid verdict for an invalid arrival). A compact
memo cannot hold full uniqueness history without becoming exponential itself, and
pruning/validation scheduling in those modes is coupled to per-path validity.
TRAIL/ACYCLIC therefore retain exhaustive tracing unchanged. Bound targets
terminate globally and never re-enter the tracer.

## Mathematical state used by the implementation

Compact state `x = (NodeState, sourceLength)` with transitions through eligible
source signposts (`seenAt(lengthFromSource)`). For a push from `(forwardNode, l)`
at target depth `D`, `lengthToTarget = (D − l) + dataGraphLength(s)` is fixed by
the state, so first-discovery `minTargetDistance` values are state-determined.
Two WALK traces reaching the same state have identical bookkeeping suffixes
(no uniqueness tracking, no validation, no pruning, no new seen lengths), so the
suffix is computed once. Zero-length `NodeSignpost` transitions can cycle
(`(A,l) → (B,l) → (A,l)`); the implementation carries an on-path set that also
terminates a case where the baseline loops forever.

Bound actually proven by the design: pushes ≤ eligible `(signpost, sourceLength)`
pairs; distinct states ≤ `#NodeStates · (D+1)`. Every signpost is still pushed
(and first-traced, preserving discovery order) once per eligible source length;
only re-descent into completed states is skipped.

## Implementation (production delta)

- `PostSaturationMemo.java` (new, package-private): `completed` and `onPath`
  maps (`NodeState → int-length` sets via `HeapTracking*` collections, exact
  memory accounting); one-time seeding from the suspended DFS path; closed in
  `PathTracer.reset()` (epoch-scoped: lengths are relative to the target depth).
- `PathTracer.java` (+59): pre-saturation path bit-identical; post-saturation,
  WALK-only, lazily allocates the memo on first push, skips descent into
  completed/on-path states. Non-WALK modes execute zero new work (one boolean
  gate per push).
- `PGPathPropagatingBFS.java` (+5): disable-only gate for bound `intoTarget`
  searches (`setP7Enabled(false)`); unbound keeps the tracer default.

## Mechanism result (real code, `PGPathPropagatingBFSP7Test`)

Walk repeated diamonds, unbound, SHORTEST K=1, `anyDirectedWalk` NFA:

```text
d=6: baseline 948 pushes → P7 200 pushes (4.74x reduction)
d=8: P7 pushes >4x below baseline and within the 60·d linear bound
```

The executable model corroborates the asymptote (d=12: 16380 → 48 trace ops;
PostSatFraction 0.995 at d=10).

## Differential methodology and equality surface

`PGPathPropagatingBFSP7Test` runs each fixture twice (tracer P7 forced off vs on)
and asserts, via counting hooks, equality of: **result rows in order**,
**propagation schedules**, **target-signpost registrations**, **prunes**,
**returned-row count** — i.e. the full internal state exhaustive tracing would
have produced, as far as it is hook-observable. Bound-target and TRAIL fixtures
assert identical rows *and* identical push counts (zero-delta controls proving
non-targeted modes keep baseline behavior exactly).

## Qualification status

- Java 21 `mvn compile` on `neo4j-cypher-runtime-util`: BUILD SUCCESS.
- PPBFS suites: 134 tests, 0 failures (5 skips proven pre-existing via stashed
  baseline rerun).
- Full module suite: 539 tests, 1 error reproduced identically on the pristine
  baseline: `PPBFSMaterialityBenchmarkTest` NPE on the missing
  `ppbfs.metrics.output` sysprop (benchmark-harness-only; unrelated to P7).
- Remaining non-blocking qualification: query-level end-to-end / JFR timing and
  maintainer review. No upstream issue/PR created.

Entry points: `p7/FINAL_QUALIFICATION.md`, clean branch
`contrib/ppbfs-walk-post-saturation-bookkeeping`.
