# P2 repository archaeology

Date: 2026-09-07

Evidence boundary: source audit only

## Baselines

| Purpose | Ref | SHA | Finding |
| --- | --- | --- | --- |
| P1/P2 causal comparison | Neo4j `2026.07` frozen point | `f213380f812b820a1b312e2ea52cb3d8f1931ccc` | Original dense level buckets and history scan |
| Current maintained cross-check | Neo4j `2026.08` | `736cad02a36bb4a0d32c1064f44768339c814269` | P2-relevant `FoundNodes` and `BFSExpander` layout unchanged |
| Existing baseline observability ancestry | personal fork | `3b0413e37db3fd08bbf32aa4b7b472780c9016a4` | Global node occupancy and P1 lookup-probe instrumentation |
| Existing P1 research lab | personal fork | `d4e1fa4cfee1e2bda1d610121307a77286cb3a58` | C1/C4 evidence and a canonical-index-only two-slot `StateBucket` |

`SOURCE-CONFIRMED`: no upstream P2 implementation supersedes this research at
the audited refs.

## Relevant source graph

```text
Cypher selective path pattern
  -> ConvertToNFA
  -> logical NFA with sequential integer state IDs
  -> runtime State[] / transition arrays
  -> PGPathPropagatingBFS
       -> FoundNodes
       -> BFSExpander
            -> ProductGraphTraversalCursor
```

### `ConvertToNFA`

Bounded variable-length relationships and bounded quantified path patterns are
unrolled by adding states. Unbounded repetition introduces loops. This gives the
research two distinct native controls:

- increase `S` through bounded unrolling;
- alter coexisting state occupancy through branch/reconvergence and
  juxtaposition structure.

### `NFABuilder`

`NFABuilder` starts at state ID zero and increments `nextId` for each added
state. The runtime assumption that state IDs are suitable for array indexing is
therefore grounded in the builder, not an incidental dataset property.

### `PGPathPropagatingBFS`

One `FoundNodes` and one `BFSExpander` are created per PPBFS execution. The
caller supplies `nfaStateCount`. `FoundNodes` is closed before the enclosing
scoped tracker, which is the correct point for query-local telemetry emission.

Normal exhaustion and target saturation call `hooks.finished()`, but an early
consumer close may not. P2 telemetry must therefore bind final emission to
`FoundNodes.close()`, not only to the finished hook.

### `NodeState`

`NodeState` is the canonical product-state object for one `(graph node ID, NFA
state ID)` pair. Its source documentation states that uniqueness is relied upon
throughout PPBFS. A P2 structure may change structural references but cannot
copy or replace canonical `NodeState` identities.

### `FoundNodes`

The baseline owns three kinds of level map:

```text
history[]
forwardFrontier
backwardFrontier (bidirectional only)
frontierBuffer
```

Every map has the same value type:

```text
HeapTrackingArrayList<NodeState>
```

A value is created with exact logical size `nfaStateCount`, then indexed by
`stateId`.

The same bucket object progresses through:

```text
frontierBuffer -> directional frontier -> history
```

No bucket mutation occurs after buffer commit. This is the key observation that
allows one exact commit-time occupancy scan followed by lightweight operation
attribution.

### `BFSExpander`

For every graph node in the selected frontier, `BFSExpander`:

1. iterates all `S` entries in the state bucket;
2. copies only non-null `State` references into a reused compact `statesList`;
3. supplies the compact list to `ProductGraphTraversalCursor`;
4. for each accepted relationship expansion, performs one direct state-ID
   lookup in the original bucket to recover the corresponding `NodeState`.

Thus frontier buckets combine two operation shapes:

```text
one O(S) full scan
many O(1) direct state-ID lookups
```

After frontier retirement, the same bucket becomes lookup-only history.

### `ProductGraphTraversalCursor`

`setNodeAndStates` accepts a `List<State>`, derives directed relationship types,
and then reuses that list while traversing relationships. The current P2 phase
does not change this API or eliminate `statesList`; that must remain a separate
causal experiment after storage selection.

### Heap-tracked collections

`HeapTrackingArrayList.newEmptyArrayList(S, tracker)` allocates a full object
reference array and sets logical size to `S`. Iteration therefore visits every
slot, including nulls.

`HeapTrackingIntObjectHashMap` has non-trivial fixed table cost, including
parallel primitive-key and reference-value arrays with a default initial
capacity. It is not automatically smaller for `k=1..3` and remains only a
candidate/control until measured.

## Exact baseline access map

| Site | Map operation | Bucket operation | Mutability | Role |
| --- | --- | --- | --- | --- |
| `FoundNodes.addToBuffer` | `frontierBuffer.get/put(nodeId)` | `set(stateId, NodeState)` | mutable | write-heavy buffer |
| `FoundNodes.get`, buffer | map lookup | `get(stateId)` if node bucket exists | mutable but lookup does not mutate | recursive encounter lookup |
| `FoundNodes.get`, forward frontier | map lookup | `get(stateId)` | immutable | active lookup |
| `FoundNodes.get`, backward frontier | map lookup | `get(stateId)` | immutable | active lookup |
| `FoundNodes.get`, history | newest-to-oldest level map lookup | `get(stateId)` only on matching node buckets | immutable | retained lookup |
| `BFSExpander.expand` state collection | frontier iteration | iterate all `S` entries | immutable | iteration-heavy |
| `BFSExpander.expand` relationship materialization | none | `get(source/target stateId)` | immutable | repeated direct lookup |
| C4 retirement merge | retired-index map lookup | scan/merge state slots | immutable inputs, merged destination | P1 interaction only |

## Lifecycle findings

### Creation

A bucket is allocated on the first state discovered for a graph node in the
currently open buffer. Full `S` storage is allocated immediately, independently
of eventual `k`.

### Mutation

All writes occur while the bucket belongs to `frontierBuffer`. The same graph
node may receive several state IDs in that buffer through NFA alternatives or
juxtapositions. Bidirectional discovery can create a separate level bucket for
the same canonical `NodeState` in the opposite direction.

### Commit

`commitBuffer(direction)` makes the buffer the selected directional frontier.
The prior frontier of that direction is retained in history. Once committed, a
bucket is immutable.

### Iteration

A committed bucket is normally scanned once when its frontier is selected for
expansion. Bidirectional scheduling can extend the wall/global-depth lifetime of
one directional frontier, but it does not introduce further writes.

### Retirement

The whole frontier map is moved into history. The bucket object remains the same
and remains reachable until `FoundNodes.close()`.

### Destruction

The baseline relies on the enclosing scoped `MemoryTracker` for structural
accounting release. It does not individually close history buckets. Research
telemetry must not change that ownership model.

## Why the prior occupancy result is insufficient

The existing `OccupancyPPBFSHooks` stores:

```text
graphNodeId -> BitSet(stateId)
```

across the whole query. This computes canonical/global node occupancy:

```text
U / (N * S)
```

It does not compute level-bucket occupancy. For example, if one graph node is
seen in state 2 at depth 4 and state 19 at depth 10, the old hook reports a
single bucket with `k=2`; the baseline actually allocated two arrays of size
`S`, each with `k=1`.

The existing Hetionet result (`S=4`, median global `k=3`) remains valid for its
P1 question, but it cannot select or reject a P2 representation.

## Prior `StateBucket` finding

The P1 lab contains a two-inline-slot bucket which promotes to a dense list. It
was designed only for C1's additional canonical `allStates` repository.
Frontier buckets remained dense, and the candidate failed a predeclared 92 MiB
query-memory gate because the canonical repository was additive.

`INFERRED`: that failure does not reject replacing baseline level buckets. The
P2 candidate has a different resource equation because it substitutes for,
rather than adds to, the questioned dense arrays.

## Unresolved questions for instrumentation

1. What is the exact final `k` distribution per level bucket?
2. How often are occupied IDs contiguous, low-biased, or spread across `S`?
3. What is the lookup-to-iteration ratio by final `k`?
4. How much of lookup traffic reaches a bucket rather than missing at the level
   map?
5. Are writes generally increasing/adjacent in state-ID order?
6. How often does bidirectional scheduling retain frontiers for multiple global
   levels?
7. Which graph/query combinations produce a true intermediate-occupancy regime?
8. Does bucket management occupy enough baseline CPU/allocation for an
   end-to-end change to be material?

These questions define the next evidence phase; they are not answered by source
inspection alone.