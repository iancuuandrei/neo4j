# P7 final qualification

Scope: graduate the WALK post-saturation bookkeeping optimization from research
to a clean contribution candidate. Normative result: **YES — workload-specific
but worthwhile (WALK-only)**. What follows is what was actually run, with exact
provenance, and what remains open.

## 1. Base provenance

```text
upstream tip verified 2026-09-09 (fetch): upstream/2026.08 =
  736cad02a36bb4a0d32c1064f44768339c814269
qualification base: same SHA (P7 analyzed + implemented against the 2026.08 PPBFS files)
research branch: research/ppbfs-lab (P1/P2 prototype present; P7 developed uncommitted on top)
contribution branch: contrib/ppbfs-walk-post-saturation-bookkeeping (from the SHA above)
```

2026.07 → 2026.08 ppbfs delta: only `Propagator.java` (+12: test/hook getters).
P1/P2 lab delta touches only `FoundNodes.java`, `StateBucket.java` (new),
`PPBFSHooks.scala`, `PGPathPropagatingBFS.java` (one constructor argument).
`PathTracer`/`SignpostStack`/`SignpostTracking`/`TwoWaySignpost`/`NodeState`/
`Lengths` are identical across all three trees, so the P7 patch applies cleanly
and the contrib branch is independent of P1/P2 by construction (verified by diff
of the contrib branch against its upstream base: 4 files, no P1/P2 content).

## 2. Implementation behavior (locked)

- Pre-saturation: `PathTracer` executes the baseline code path unchanged.
- Post-saturation, unbound WALK only: each pushed signpost is still first-traced
  (`setMinTargetDistance` + scheduling preserved in discovery order); descent
  into `(NodeState, sourceLength)` states already marked completed is skipped;
  re-entry to on-path states (zero-length cycles) is skipped.
- Memo allocated lazily on first post-saturation push; closed in `reset()`
  (per-target-epoch scope). All structures memory-tracked (`HeapTracking*`).
- Bound `intoTarget`: P7 force-disabled in `PGPathPropagatingBFS` (these searches
  never re-enter the tracer after saturation anyway — verified control flow).
- TRAIL/ACYCLIC: P7 never engages (`walkMode` gate); exhaustive tracing retained.

## 3. Evidence actually obtained

### 3.1 Real-code differential tests (`PGPathPropagatingBFSP7Test`, 4 tests, all passing)

| Test | Result |
| --- | --- |
| Walk diamonds d=8 K=1 on/off | rows, schedules, target-signposts, prunes, returned all equal; pushes >4x below baseline, within 60·d |
| Walk diamonds d=5 K=2 on/off | rows, schedules, target-signposts equal; pushes strictly below baseline |
| Bound target d=4 K=1 on/off | rows, pushes, schedules identical (zero delta) |
| Trail d=4 K=1 on/off | rows, pushes, schedules, target-signposts identical (zero delta) |

Headline mechanism number: Walk d=6 K=1 — **948 baseline pushes → 200 P7 pushes
(4.74x)**.

### 3.2 Existing suites (with patch)

- PPBFS-focused: `PGPathPropagatingBFSTest` (72, 5 skips), `Generated...` (35),
  `FoundNodesTest`, `LengthsTest`, `NodeStateTest`, `TwoWaySignpostTest`,
  `PGPathPropagatingBFSP7Test` (4) → **134 run, 0 failures**.
- The 5 skips reproduce identically with the patch stashed (baseline rerun:
  107 run, same 5 skips) → pre-existing, unrelated.
- Full `neo4j-cypher-runtime-util` suite: **539 run, 1 error**, reproduced
  identically on the pristine baseline: `PPBFSMaterialityBenchmarkTest`
  `record controlled chain history-probe scaling` fails with NPE at
  `Path.of(sys.props("ppbfs.metrics.output"))` — the benchmark harness system
  property is unset under plain `mvn test`. Environmental, unrelated to P7.

### 3.3 Executable-model corroboration (not production evidence)

Python mirror of the WALK DFS + memo candidate (`p7_model.py`, `p7_proptest.py`,
kept outside the repo): 120 random + 150 dense-layered differential trials with
0 mismatches (event-order-exact `minTargetDistance` equality); repeated diamonds
d=12: 16380 → 48 trace ops; PostSatFraction 0.995 at d=10; zero-length-cycle
termination where the baseline loops; history-blind TRAIL memo demonstrated
unsound (hybrid justification). Model only — the normative evidence is §3.1–3.2.

## 4. Regression and tax position

- Non-targeted modes (bound, bidirectional, TRAIL, ACYCLIC, never-saturating
  targets): no new allocations (memo created only post-saturation in WALK);
  per-push cost is boolean gates. Zero-delta proven for bound and TRAIL by test.
- Unique-path / K-exhausts-all / one-signpost / shallow cases: memo engages
  rarely or never; model-level runs show candidate ops == baseline ops.

## 5. Open (non-blocking) qualification

1. **Query-level end-to-end / JFR timing**: mechanism-level push reduction is
   proven, but the spec's own bar requires showing tracing share matters
   end-to-end on a real workload. Pending; needs a graph with high shortest-path
   multiplicity under unbound/multi-target WALK PPBFS.
2. **Maintainer review**: no upstream issue/PR created yet (deliberate).
3. **K GROUPS + non-inlined-predicate saturation paths**: mechanics identical by
   code inspection; no dedicated differential test (saturation flag is shared).

## 6. Reviewer checklist for the contrib branch

- [ ] `git diff upstream/2026.08..contrib/ppbfs-walk-post-saturation-bookkeeping`
      shows only the 4 P7 files, no P1/P2 or research material.
- [ ] `PathTracer` pre-saturation path is line-identical to baseline.
- [ ] Bound/TRAIL/ACYCLIC behavior unchanged (tests assert push-identical).
- [ ] Memo lifecycle: lazy alloc, `reset()` close, epoch-scoped keys.
- [ ] Memory accounting via `HeapTracking*` collections only.
