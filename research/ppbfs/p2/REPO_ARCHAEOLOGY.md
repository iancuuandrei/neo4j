# P2 repository archaeology (`SOURCE-CONFIRMED`)

Base: historical baseline `f213380f812b820a1b312e2ea52cb3d8f1931ccc` unless noted.
All paths relative to repository root. Line numbers from `git show <base>:<path>`.

## 1. Dense bucket layout

`community/cypher/runtime-util/src/main/java/org/neo4j/internal/kernel/api/helpers/traversal/ppbfs/FoundNodes.java`

- Per-node bucket type is `HeapTrackingArrayList<NodeState>` of logical size `S = nfaStateCount`,
  keyed by graph node id in a `HeapTrackingLongObjectHashMap` per level (`FoundNodes.java:57-70`).
- Allocation (`FoundNodes.java:106-114`): `addToBuffer` looks up `frontierBuffer.get(nodeId)`;
  on miss allocates `HeapTrackingArrayList.newEmptyArrayList(nfaStateCount, memoryTracker)` —
  an `Object[S]` filled with nulls, `size = S`, tracked as
  `SHALLOW_SIZE + shallowSizeOfObjectArray(S)` — then `nodeStates.set(state.id(), nodeState)`.
  Memory is therefore `O(S)` per distinct buffered node even when `k = 1`.
- The class javadoc (`FoundNodes.java:31-40`) explicitly flags this:
  sparse arrays from sequential state-id indexing "may lead to over allocation" and
  "we may want to revise this in the future if benchmarks tell us to". That comment is the P2 problem statement.

## 2. Write path

- `BFSExpander.discover` (`BFSExpander.java:92-95`) calls `foundNodes.addToBuffer(node)` then
  `node.discover(direction)`; node-juxtaposition closure (`BFSExpander.java:99-123`) recursively
  `encounter`s same-node product states, so one discovery can perform several same-bucket `put`s.
- `BFSExpander.encounter` (`BFSExpander.java:128-140`): canonical check `foundNodes.get(nodeId, state.id())`;
  on miss constructs `new NodeState(...)` and `discover`s it (insert). Duplicate writes are idempotent at the
  collection level (`set` overwrites the same slot) but each `encounter` still pays a full canonical `get`.
- Overwrite semantics: `HeapTrackingArrayList.set` replaces the slot; canonical identity is enforced by
  construction (only `encounter` creates `NodeState`, guarded by `get`), not by the list.

## 3. Lookup paths (three categories — do not conflate)

- Canonical lookup `FoundNodes.get(nodeId, stateId)` (`FoundNodes.java:117-141`): probes `frontierBuffer`,
  then `forwardFrontier`, then (bidirectional) `backwardFrontier`, then `history` newest-first.
  Cost is `O(depth)` map probes; each level probe is `getFromLevel` (`FoundNodes.java:144-154`):
  `level.get(nodeId)` then direct `nodeStates.get(stateId)`. History lookup is linear in BFS depth.
- Frontier exact lookup `statesById.get(state.id())` (`BFSExpander.java:193,212`): one direct indexed
  `get` per product-graph relationship expansion, against the currently expanded bucket. No map probe.
- Canonical-vs-frontier distinction matters: `encounter` during expansion hits the frontier bucket first
  (most recently committed level is not the frontier — buffer probe first covers same-level revisits).

## 4. Iteration path (the dense tax)

- `BFSExpander.expand` (`BFSExpander.java:168-227`): for each `(nodeId, statesById)` pair of the active
  frontier, `statesList.clear()` then full scan `for (var nodeState : statesById)` (`BFSExpander.java:177-181`)
  over all `S` slots, null-checking each and copying non-null `State` refs into the reused `statesList`
  (capacity `S`, allocated once per expander at `BFSExpander.java:84`).
- `pgCursor.setNodeAndStates(dbNodeId, statesList, direction)` fans the `k` active states out over the
  node's relationships; per emitted expansion one exact frontier lookup (`BFSExpander.java:193,212`) follows.
- Lookup/iteration ratio `g/i` per bucket is therefore driven by graph fanout: one `O(S)` scan per expanded
  bucket plus `O(expansions)` direct lookups. Low-degree graphs amortize poorly (scan dominates); high-fanout
  graphs multiply exact lookups (lookup representation matters more).

## 5. Frontier lifecycle: promotion, retirement, history retention

- `openBuffer` (`FoundNodes.java:157-162`): allocates a fresh `frontierBuffer` map sized from the previous
  buffer; `bufferState = OPEN`. Buffer collects the next level while the current frontier is iterated.
- `commitBuffer(direction)` (`FoundNodes.java:165-186`): pushes the non-empty outgoing frontier into
  `history` (retained for canonical lookup for the rest of the search), installs `frontierBuffer` as the new
  frontier, closes the buffer. History grows by one level-map per expanded level; nothing is ever removed
  except by `close()`.
- `frontier(direction)` (`FoundNodes.java:187-192`) exposes the live level map for expansion.
- `hasMore` (`FoundNodes.java:212-220`): unidirectional requires non-empty forward frontier; bidirectional
  requires both frontiers non-empty. `getNextExpansionDirection` expands the smaller frontier first.

## 6. Memory ownership and close

- `FoundNodes` owns a scoped tracker (`FoundNodes.java:95-102`); `close` (`FoundNodes.java:223-226`) only
  closes the scoped tracker — inner collections are not individually closed (comment says so explicitly).
- `BFSExpander.close` closes `pgCursor` and the reused `statesList`, not `globalState` (not owned).
- `NodeState` constructor allocates `estimatedHeapUsage()` (shallow + `Lengths`) on the shared tracker;
  `NodeState.close` closes signpost lists. Any replacement bucket must preserve exact allocate/release
  pairing, including on promotion (release sparse backing exactly once) and on C4-style transfer.

## 7. Bidirectional behavior

- Two frontier maps (`forwardFrontier`, `backwardFrontier`), one shared `frontierBuffer` (only one direction
  expands at a time). `encounter` re-`discover`s a known `NodeState` for the opposite direction if not yet seen
  by it (`BFSExpander.java:134-138`); per-direction seen flags live on `NodeState`
  (`discoveredForward/discoveredBackward`). History is shared across directions, so canonical probes serve both.

## 8. State-id provenance and ordering

- State ids are dense sequential: `PGStateBuilder.newState` assigns `states.size` at creation
  (`PGStateBuilder.scala`), so `state.id()` is a valid direct index into `[0, S)`. `S = stateCount`.
- Real `S` derives from `ConvertToNFA` (`community/cypher/cypher-planner/.../ConvertToNFA.scala`) over the
  QPP regex; test-side repetition unrolling (`NfaDsl` `rep(min,max)`) multiplies states linearly
  (each unrolled copy adds fresh states). Requested regex complexity is not `S` — only the compiled
  `stateCount` counts.
- Baseline iteration emits active states in ascending state-id order (array order). Any compact candidate must
  preserve ascending iteration order (sorted vector), otherwise cursor/branch behavior and reproducibility can
  change even when graph semantics are unaffected.

## 9. Adjacent components audited

- `NodeState`: canonical per-product-state record; identity uniqueness per `(nodeId, stateId)` is a relied-upon
  invariant (class javadoc). Holds signposts, lengths, target bookkeeping; `estimatedHeapUsage` tracked.
- `PGPathPropagatingBFS`: owns `FoundNodes`, `BFSExpander`, `GlobalState`, `Propagator`, `TargetTracker`,
  `PathTracer`; level loop alternates `expand` (BFS) with propagation/tracing phases.
- `ProductGraphTraversalCursor.setNodeAndStates`: preprocesses NFA type/direction per node over the active
  state list, then emits `(relationship, expansion)` pairs filtered by direction, rel predicate and end-state
  node predicate. Input is the copied `statesList`, not the bucket — bucket iteration order determines input order.
- `PPBFSHooks` (`hooks/PPBFSHooks.scala`): `expand`, `expandNode(nodeId, states, direction)`, `discover`,
  cursor hooks. Usable for coarse observability; per-bucket counters need `FoundNodes`/`BFSExpander`
  instrumentation (separate build, never timed).
- `HeapTrackingArrayList.newEmptyArrayList(n, tracker)`: allocates `Object[n]`, `size = n`, all nulls;
  tracked `SHALLOW + shallowSizeOfObjectArray(n)`. `get(i)` bounds-checks against `size` (`Objects.checkIndex`).
- `HeapTrackingLongObjectHashMap`: open-addressed long→object map, heap-tracked arrays; per-level map overhead
  is shared across that level's buckets, not per bucket.
- `HeapEstimator` (`community/unsafe/.../HeapEstimator.java`): with compressed oops,
  `shallowSizeOfObjectArray(n) = align(16 + 4n)` to 8 bytes. Reference table: A(4)=32, A(8)=48, A(16)=80,
  A(32)=144, A(64)=272, A(128)=528, A(256)=1040, A(512)=2064 (`DERIVED`, verify at runtime before citing in gates).

## 10. P1 reference designs (frozen — read-only)

- C1 `contrib/ppbfs-direct-state-index` (`SOURCE-CONFIRMED` head `0a8ef51868e`): adds a long-lived canonical
  `allStates` map (nodeId → dense `HeapTrackingArrayList<NodeState>`) so canonical lookup is `O(1)` maps
  instead of `O(depth)` history probes; retired frontiers are released. Canonical buckets are still dense `S`
  arrays — C1 is lookup-heavy and long-lived, hence the P2+C1 question (can P2 recover C1's added allocation
  while keeping its deep-query speedup?).
- C4 `contrib/ppbfs-deferred-state-index`: keeps baseline history probing while shallow; past a depth
  threshold the retiring frontier becomes/merges into a `retiredIndex` (nodeId → dense bucket).
  `mergeBuckets` requires equal sizes and merges slot-wise — `O(S)` per repeated graph node. A sorted compact
  bucket could merge in `O(k1+k2)`; must be measured, including ownership/double-release behavior on transfer.
- Prior lab `StateBucket` (`origin/research/ppbfs-lab`, field-based sparse capacity 1–2, canonical-store only,
  no iteration support): reference only. It does not answer the frontier iteration question and is not the P2
  candidate; P2 candidates start from the clean `f213…` baseline.
- Prior global occupancy (`benchmark/ppbfs-external-observability`, Hetionet `rho ≈ 0.75–0.79` at `S = 4`,
  median `k = 3`): aggregates states globally per graph node across levels. It merges states that lived in
  separate dense arrays and cannot serve as the P2 sparsity oracle. P2 telemetry must measure level-owned buckets.
