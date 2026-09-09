# PPBFS WALK post-saturation bookkeeping

## Status

**Candidate; upstream issue [neo4j/neo4j#13968](https://github.com/neo4j/neo4j/issues/13968) open for architecture feedback, no PR submitted.** A minimal review branch exists at
[`contrib/ppbfs-walk-post-saturation-bookkeeping`](https://github.com/iancuuandrei/neo4j/tree/contrib/ppbfs-walk-post-saturation-bookkeeping),
independently rooted at upstream `2026.08` (`736cad02a36bb4a0d32c1064f44768339c814269`).
Real-world qualification is complete (STRONG REAL-WORLD CASE, details below);
maintainer review remains pending.

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

## Real-world qualification (STRONG REAL-WORLD CASE)

P7 remains limited to `StatefulShortestPath(All, Walk)`, but within that mode
post-saturation tracing is a common and sometimes dominant cost across multiple
real graph topologies. Discovery ran unbound SHORTEST-1 WALK over LiveJournal,
web-Stanford, as-Skitter, and Hetionet (embedded counter driver, maxRows=500,
so reported work understates full queries):

| Dataset | Result |
| --- | --- |
| LiveJournal | 56/68 queries improved; aggregate post-saturation share 98.7%; P7 removed 93.5% of post-saturation pushes (5.66M → 369K) |
| web-Stanford | nearly all sampled queries affected; ~93.4% post-saturation work removed; ~6–7x timing gains on affected queries; timeouts 6 → 1 |
| as-Skitter | most sampled queries affected; 98.3% post-saturation share; 96.8% removed; 7.3x [6.3, 8.5] representative timing |
| Hetionet | mostly low-reconvergence cases; small 6–17% elimination and ~1.0–1.1x timing, effectively neutral |

Whole-PPBFS paired timing (5 fresh JVM forks, 95% CIs): LJ 2022306 **28.04x**
[20.86, 37.69] (tied to that specific query, not an aggregate); web-Stanford
8183/83240 ~6–7x; as-Skitter 1696073 7.30x; Hetionet ~1.0–1.1x.

Strongest real Cypher examples (LiveJournal, proven
`StatefulShortestPath(All, Walk)` plans, unbound targets):

- `2022306`, `LIMIT 75`: baseline `>90s` timeout vs candidate `53 rows in 52ms`
  (timeout-vs-completion observation, not a speedup ratio)
- `3705591`, `LIMIT 500`: `80ms → 61ms`, identical `500/500` rows

JFR on the affected LJ workload moved PPBFS from 47% of execution samples to
11%, with allocation samples down 44%.

Correctness/regression evidence: zero metadata mismatches across 200+
baseline/candidate pairs — rows, schedules and target-signpost registrations
matched; bound 48-pair control identical; TRAIL full-column control identical;
K=2 / GROUPS consistent. Disclosed cost: roughly `1–3ms` absolute overhead on
sub-50ms no-opportunity queries; no relative regression on larger queries.

These gains are specific to qualifying `StatefulShortestPath(All, Walk)`
workloads, not universal Neo4j speedups. Full evidence:
`research/ppbfs/p7/MATERIALITY_REPORT.md` on `research/ppbfs-lab`.

## Current limitation

Upstream issue `neo4j/neo4j#13968` is open for architecture feedback; no PR
submitted. Discovery truncation (500 rows / time caps) understates full-query
work; absolute numbers are NFA-dependent.
