# C4-DCRI source audit

## Status and authority

Status: `SOURCE-CONFIRMED`, pre-implementation audit, 2026-09-05. Repository: `neo4j/neo4j`. C4 branch: `research/ppbfs-c4-deferred-index`. Exact base: B0 instrumentation commit `1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381`, itself derived from upstream `2026.07@f213380f812b820a1b312e2ea52cb3d8f1931ccc`.

Frozen references:

```text
B0 instrumentation: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
C1 research:         caf33be9f5de2d8d7f4a291abd466cf808aaf801
C1 clean:            0a8ef51868e9c8f52dfd49c516d0a14a0295b2c6
research archive:    81ea6c0cefbee54aa2d9610d8faf84e4acf4a509
observability:       3b0413e37db3fd08bbf32aa4b7b472780c9016a4
```

The research archive branch subsequently advanced only for issue-posting documentation to `d3bb27eb5fe54800fb357c2673bd264259ba1aac`. None of the frozen implementation references changed.

## B0 ownership and lookup

`FoundNodes` owns a scoped `MemoryTracker`, a level-partitioned `history`, forward/backward active frontiers, and one mutable buffer. Every level maps `nodeId` to a fixed-capacity `HeapTrackingArrayList<NodeState>` indexed by NFA state id. `commitBuffer` moves the retiring directional frontier into `history` without copying, promotes the buffer, and later creates a fresh buffer.

`get` probes buffer, forward frontier, backward frontier in bidirectional mode, then `history` newest-to-oldest. The source comment and loop explicitly establish O(history depth) lookup. The retained level maps avoid growth of one large outer map but make misses and old hits accumulate probes with depth.

## C1 delta

C1 replaces `history` with an always-on `allStates` map. `addToBuffer` creates or updates a canonical dense bucket and independently creates or updates a scheduling bucket in `frontierBuffer`. A newly reached node can therefore have two live dense state arrays containing references to the same `NodeState` objects. C1 closes retired scheduling maps/buckets and uses `allStates` for direct lookup.

The invariant check `existing == null || existing == nodeState` confirms that replacement by a distinct object is illegal. C1 changes structural ownership, not the semantic key.

## Required invariants

- Exactly one `NodeState` object exists per `(nodeId,stateId)` during one PPBFS execution.
- Traversal direction is mutable discovery metadata and is not part of the canonical key.
- Active frontier membership is scheduling state; canonical/history membership is identity and lookup state.
- A mutable active bucket cannot simultaneously serve as a retired canonical bucket because later inserts could contaminate the current expansion set.
- Ownership transfer is allowed only after a frontier retires and is no longer iterated.
- For a repeated node after activation, occupied incoming state slots merge into the retained bucket; an occupied retained slot must contain the identical object.
- A transferred bucket remains owned until `FoundNodes.close`; a merged/discarded incoming bucket must release its structural accounting exactly once.
- Bidirectional frontiers share the canonical identity domain and buffer but remain distinct scheduling maps.
- Cancellation, early close, normal close, and failed transitions must release the scoped tracker without double release.

## Relevant call sequence

`BFSExpander.expand` iterates `foundNodes.frontier(direction)`, calls `encounter`, then calls `foundNodes.commitBuffer(direction)` after expansion. `PGPathPropagatingBFS` seeds forward and optional backward states through buffer/commit operations. Consequently, `commitBuffer` is the safe frontier-retirement boundary and the only intended C4 ownership-transfer point.

## Collection behavior

`HeapTrackingLongObjectHashMap` accounts its object and key/value backing arrays, releases old backing arrays when growing, and releases current structural memory on `close`. Inner `HeapTrackingArrayList` buckets account their own arrays. Reusing the first retiring outer map is structurally possible only if the map is detached from the directional frontier before a new frontier is installed. Subsequent maps can be merged via `keyValuesView`; transferred inner buckets must not be closed, while merged buckets must be closed after slot reconciliation. The retiring outer map is then closed.

## Open implementation questions

- The activation threshold must be calibrated before selection; it cannot depend on wall-clock time.
- First-map reuse is preferred but must be proven for both directions and empty-frontier transitions.
- Existing hooks do not expose all C4 diagnostics; research-only aggregate hook methods are needed without per-lookup logging.
- The underlying map exposes no stable public resize callback. Rehash analysis may require a research-only tracked-capacity hook or JFR backing-array attribution rather than reflection.

