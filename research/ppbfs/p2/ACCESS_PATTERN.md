# P2 access pattern (`SOURCE-CONFIRMED` structure; counts pending Phase 2 telemetry)

Base: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`.

## Bucket roles

| Role | Backing | Write | Lookup | Iteration | Lifetime |
| --- | --- | --- | --- | --- | --- |
| `frontierBuffer` | fresh dense `Object[S]` per new node | heavy (every discovery) | canonical probe position 1 (same-level revisits) | none | one level |
| `frontier` (forward/backward) | dense `Object[S]` per node | none after commit | exact `get(stateId)` per product expansion + canonical probe position 2 | full `O(S)` scan per expanded node (`BFSExpander.java:177-181`) | one level |
| `history[i]` | retained dense maps | none | canonical probe positions 3.. (`O(depth)` levels) | none | rest of search |
| C1 `allStates` (frozen ref) | long-lived dense per node | once per product state | all canonical lookups | none | whole search |
| C4 `retiredIndex` (frozen ref) | dense per retired node | merge on retire past threshold | retired canonical lookups | none | rest of search |

## Operation mix per expanded frontier bucket

For a bucket with `S` slots, `k` occupied, fanout `f` (relationships × matching expansions):

- 1 full iteration scanning `S` slots, emitting `k` states.
- `g ≈ f` exact direct lookups (`statesById.get`), hits when the expansion's source/target state is active.
- `w` writes happened one level earlier (buffer phase), typically `k` first-writes plus duplicate `encounter`s.
- Canonical `FoundNodes.get` calls interleave: each `encounter` probes buffer → frontier(s) → history.

Decisive ratio is `g/i` per bucket: one scan is amortized over `g` lookups. Low-degree chain/grid
(`f ≈ 1–2`) → scan dominates → compact iteration wins if lookup stays cheap. High-fanout
(`f ≫ 1`, e.g. LiveJournal/as-Skitter style) → `g` direct lookups can dominate → dense lookup advantage grows.

## Why a universal bucket is only a hypothesis

- Frontier buckets pay iteration + exact lookup; history/C1 buckets pay lookup only; buffer buckets pay
  insertion. Crossovers differ per role. Role specialization is permitted only with measured net gain
  over the simplest fixed/adaptive winner.
- Canonical lookup depth-dependence (baseline `O(depth)` probes vs C1/C4 direct maps) changes `g`
  per role; P2 must be evaluated on B0 first, then combined once with the P2 winner only.

## Telemetry requirements (Phase 2)

Per level-owned bucket (never the global aggregate): `S`, `k` at first iteration and at retirement,
`min/max` state id, span `R`, contiguous runs, occupied 8/16/32-chunks, writes/duplicates,
insertion monotonicity/adjacency, exact lookups by category (canonical / frontier / C1 / C4 / merge),
full iterations with slots scanned vs active emitted, lifetime in epochs, direction, search mode.
Aggregate by bucket count, lookup, iteration and allocation weighting. No per-operation disk I/O;
flush aggregates after the query.
