# PPBFS product-state lookup source audit

## Audit identity

- Audit date: 2026-09-04 (Europe/Bucharest)
- Evidence status: SOURCE-CONFIRMED unless marked otherwise
- Repository: `neo4j/neo4j`
- Fork: `iancuuandrei/neo4j`
- Research branch: `research/ppbfs-lab`
- Upstream default branch: `2026.07`
- Baseline SHA: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`
- Baseline commit date: `2026-08-05T19:43:34+02:00`

## Environment

- OS: Windows 11 Pro 25H2, build 26200.9168, x86-64
- CPU: AMD Ryzen 9 9955HX, 16 physical / 32 logical cores
- RAM: approximately 31.2 GiB
- JDK: Eclipse Temurin 21.0.12.1+1 LTS
- Maven: Apache Maven 3.9.11
- Primary JVM heap: 4 GiB

## Current source surface

- `FoundNodes.java`: owns history, active frontier(s), frontier buffer, lookup, depth, and a scoped memory tracker.
- `BFSExpander.java`: calls `FoundNodes.get(nodeId,state.id())` for every accepted relationship or node-juxtaposition encounter, creates a `NodeState` only on a miss, and uses the same object for opposite-direction discovery.
- `PGPathPropagatingBFS.java`: owns one `FoundNodes` instance per iterator/execution and closes it through the PPBFS lifecycle.
- `NodeState.java`: documents identity uniqueness for each `(data node,NFA state)` pair and stores mutable direction, distance, signpost, and propagation state.
- `ProductGraphTraversalCursor.java`: evaluates direction, relationship predicate, and target-node predicate before an accepted expansion reaches `BFSExpander.encounter`.
- `GlobalState.java`: supplies execution-local propagation, target tracking, memory tracking, hooks, search mode, and depth.
- `PPBFSHooks.scala`: provides execution observation; production uses its null implementation.
- `StatefulShortestPathSlottedPipe.scala` and `StatefulShortestPathPipe.scala`: construct the shared `PGPathPropagatingBFS` implementation from the slotted and interpreted runtimes.

## Repository and lookup lifecycle

`FoundNodes` stores dense NFA-state arrays beneath primitive-long maps in four
disjoint locations: the next-level buffer, forward frontier, optional backward
frontier, and prior frontier maps in `history`. Baseline `get` probes in exactly
that order and scans history newest-to-oldest. Each historical-level probe
performs an outer node-id lookup followed, when present, by an indexed state-id
lookup. The method is explicitly documented as `O(N)` with respect to history
length.

`BFSExpander.encounter` is the sole production caller of `FoundNodes.get`. A
miss allocates one `NodeState`, then `discover` registers it in the open buffer
before recursively processing node juxtapositions. A hit returns the original
reference; in bidirectional mode a state unseen from the current direction is
rediscovered into that direction's next frontier while retaining the same object.

Baseline `commitBuffer` appends the expired active frontier to `history`,
increments directional depth, promotes the buffer, and closes buffer state.
Whole-repository search found no consumer of private history beyond construction,
append, and lookup inside `FoundNodes`.

## Identity and ownership

- SOURCE-CONFIRMED: key identity is `(nodeId,stateId)`; traversal direction is mutable metadata on the same `NodeState`.
- SOURCE-CONFIRMED: signposts, lengths, target distances, validation, and propagation are stored on `NodeState` and related objects, not encoded by a historical-map position.
- SOURCE-CONFIRMED: `FoundNodes` obtains a scoped memory tracker and closes it as one execution-owned unit.
- INFERRED: current runtime entry points construct a PPBFS iterator per operator execution and do not publish its `FoundNodes`; synchronization is unnecessary if a replacement introduces no static/shared mutable state.
- INFERRED: canonical lookup is structurally plausible, but frontier structural ownership and scoped accounting must remain measured and tested.

## Git archaeology

`FoundNodes.java` was introduced/refactored in commit
`07ba47f3ba030d78cbf4f7e63789ecbc78f328ca` (2024-04-25).
Bidirectional lookup ordering and history probing were updated in
`e2d6956bf88e68dc7a5abae5059dff50cd4669c2` (2024-05-06). Blame assigns the
explicit complexity comment and base lookup to the initial implementation, with
bidirectional/history details to the bidirectional work.

## Public overlap audit

GitHub searches on 2026-09-04 covered open PRs/issues for `FoundNodes`, `PPBFS`,
`StatefulShortestPath`, `product state`, `product-state`, `direct state lookup`,
`visited state`, and `shortest path performance`, plus recently merged PRs. No
direct/adaptive `(nodeId,stateId) -> NodeState` repository implementation was
found. This cannot exclude private or differently named work.

Phase 0 passed. That source conclusion alone was not treated as a performance
claim; instrumentation and real workloads were required before retaining a
candidate.
