# PPBFS WALK post-saturation bookkeeping

## Status

**Candidate; not submitted upstream.** A minimal review branch exists at
[`contrib/ppbfs-walk-post-saturation-bookkeeping`](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-walk-post-saturation-bookkeeping),
independently rooted at upstream `2026.08` (`736cad02a36bb4a0d32c1064f44768339c814269`).
End-to-end query-level timing and maintainer review remain pending.

## The PPBFS problem

Neo4j's `StatefulShortestPath` evaluates shortest-K path queries with
`PGPathPropagatingBFS` (PPBFS): a breadth-first search over the product of the
data graph and the path-pattern automaton. To keep the BFS compact, each
discovered product node stores *signposts* — references to predecessor
traversals annotated with source-length sets — rather than full paths. Actual
paths are reconstructed on demand by `PathTracer`, which runs a depth-first
search from each target back through the signposts toward the source.

A compact signpost structure can therefore represent combinatorially many
paths: a repeated-diamond graph with `d` levels needs only `O(d)` signposts
but encodes `O(2^d)` distinct source–target paths.

## Why bookkeeping was coupled to `PathTracer`

Tracing does two jobs at once: it enumerates the result paths the query needs,
and, as a side effect of that enumeration, it establishes PPBFS metadata —
`minTargetDistance` per signpost, target-signpost registration, per-length
validation marks, pruning decisions, and the propagation schedules derived from
all of these. Because the metadata is produced *during* enumeration, the tracer
must exhaust every represented path combination for a target even after the
target has saturated (no more result paths needed). When `K << |T|`, that
post-saturation enumeration dominates tracing work.

## Why the safe optimization is WALK-only

The residual bookkeeping decomposes by path mode. In WALK mode there is no
relationship/node uniqueness tracking, every seen length counts as validated
(so no pruning ever fires), and tracing adds no new seen lengths: the remaining
work after saturation is purely structural. In TRAIL and ACYCLIC modes,
validity verdicts, validated-length sets, and prune sets depend on the full
path history (`usedRelationships` / `usedNodes`), which is exponential and
cannot be memoized by compact state — two traces can reach the same compact
state with different histories and opposite validity. Bound-target searches
terminate globally on saturation and never re-enter the tracer, so they are
untouched by construction.

## Structural-state reasoning used by the implementation

The implementation treats the compact state as `(NodeState, sourceLength)` with
transitions through eligible source signposts. For a push from `(forwardNode,
l)` at target depth `D`, the distance to target is fixed by the state, so
first-discovery `minTargetDistance` values are state-determined; and because
WALK traces reaching the same state have identical bookkeeping suffixes, each
suffix is computed once and re-descent into completed states is skipped. Every
signpost is still pushed and first-traced exactly once per eligible source
length, preserving discovery order — only redundant subtree walks are skipped.
An on-path set additionally terminates zero-length `NodeSignpost` cycle
re-entry, a case where exhaustive tracing loops forever. The memo is allocated
lazily on the first post-saturation push and released per target epoch, so
unaffected modes pay no allocation and one boolean gate per push.

## Implementation

Four files, additions only, on the clean branch:

- `PostSaturationMemo.java` (new): completed/on-path `(NodeState →
  source-length)` sets with exact memory-tracker accounting.
- `PathTracer.java` (+59): suspended-DFS continuation with post-saturation
  skip logic, gated on unbound WALK + saturation.
- `PGPathPropagatingBFS.java` (+5): P7 force-disabled for bound `intoTarget`
  searches.
- `PGPathPropagatingBFSP7Test.scala` (new): differential tests (below).

## Mechanism result

Walk repeated diamonds, unbound SHORTEST K=1, measured in the real codebase via
tracer push counts:

```text
d=6: 948 baseline pushes → 200 P7 pushes (4.74x reduction)
d=8: P7 pushes more than 4x below baseline, inside a 60·d linear bound
```

## Differential correctness testing

Each fixture runs twice — tracer P7 forced off vs on — asserting equality of
result rows in order, propagation schedules, target-signpost registrations,
prunes, and returned-row counts: the full hook-observable internal state
exhaustive tracing would have produced. Existing PPBFS suites (110 tests on the
2026.08 base, including the generated exhaustive suite) pass unchanged.

## Regression controls

Bound-target and TRAIL fixtures assert identical rows *and* identical push
counts with P7 on vs off, proving non-targeted modes keep baseline behavior
exactly. Unique-path, K-exhausts-all, one-signpost, and shallow cases engage
the memo rarely or never by construction.

## Current limitation

Mechanism-level push reduction is proven, but query-level end-to-end timing
(JFR) on a real high-multiplicity workload remains pending — a large reduction
in trace operations is not sufficient if tracing is insignificant end-to-end.
No upstream issue or PR has been created yet.
