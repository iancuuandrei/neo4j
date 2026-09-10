# P7 materiality workloads

Exact workload definitions behind `../MATERIALITY_REPORT.md`. Everything here
is inspectable without reading the full P7 archive: query shapes, source
manifests, NFA constructions, and invocation parameters.

## Planner distinction (read first)

The real Cypher workloads **bind the source but leave the end node unbound**:

```cypher
MATCH REPEATABLE ELEMENTS p = SHORTEST 1
  (s:SnapNode {snapId: $source})((a)-[:LINK]->(b)){1,12}(t)
RETURN p LIMIT $limit
```

Verified by plan: `StatefulShortestPath(All, Walk)` (SLOTTED, 2026.08.0,
unbound target, inlined LINK). Therefore the simpler `SHORTEST 1`
implementation that requires **both endpoints bound** does not apply — the
search must consider every reachable target, which is exactly where
post-saturation tracing accumulates.

The lower-level scan invokes the equivalent unbound WALK / SHORTEST-K PPBFS
shape directly (`PGPathPropagatingBFS.create` with `intoTarget ==
NO_SUCH_ENTITY`, which implies `Unidirectional`) so tracing counters can be
collected without unrelated Cypher-runtime cost (planning, HTTP, result
serialization). Absolute numbers are NFA-dependent — the scan NFA is
single-state walk-all (plus one typed Hetionet case); the planner-built QPP
NFA amplifies multiplicity further, which is why the server results are, if
anything, stronger.

## Discovery caps

The scan stops each query at `--max-rows 500` yielded rows or a per-query time
cap (60–180s; 300s for the web-Stanford feasibility case). Large
post-saturation counts are therefore **truncated rather than artificially
inflated** by full exhaustive runs; the `timed_out` flag marks truncated rows.

## Layout

- `cypher/` — server query template, timed instances, plan proof.
- `manifests/` — exact source lists (plus provenance notes); bound-pair
  controls reuse the existing
  `research/ppbfs/common/manifests/` pair files (referenced, not duplicated).
- `nfa/` — the `walk1` single-state all-outgoing WALK NFA and the typed
  two-state `chain2` Hetionet NFA, as constructed in the scan driver.
- `invocations/` — scan parameters per dataset and server-run setup.

## Workload → result map

| Workload | Report result |
| --- | --- |
| LJ discovery (68 sources, K=1) | 56/68 improved; 93.5% post-sat removed |
| web-Stanford discovery (61) | ~93.4% removed; timeouts 6 → 1 |
| as-Skitter discovery (61) | 96.8% removed |
| Hetionet labels + typed chain2 | 6–17% elimination, neutral |
| LJ K=2 / GROUPS / TRAIL / bound subsets | consistent / zero-delta controls |
| Frozen timing sets (5 forks, CIs) | 28.04x LJ; 6–7x web; 7.3x skitter; ~1x hetionet |
| Server LIMIT queries (LJ) | >90s → 52ms; 80 → 61ms; parity control |
| JFR pair (LJ 2022306) | PPBFS 47% → 11% of samples |

No datasets, stores, JFR binaries, or raw CSV/log outputs are committed here;
raw evidence lives outside Git per lab convention.
