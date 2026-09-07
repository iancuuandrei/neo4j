# P2 candidate designs and entry gates

Status: design catalogue; no candidate authorized yet

Date: 2026-09-07

This document narrows implementation choices without assuming a winner. The
baseline characterization and memory/microbenchmark evidence determine which
branches are built.

## 1. Required semantics

Every candidate must preserve:

```text
one canonical NodeState object per (graph node ID, NFA state ID)
state-ID lookup returning the same object identity
one emission of each occupied state during frontier expansion
existing frontier/map iteration order where observable by hooks/tests
unidirectional and bidirectional search
buffer -> frontier -> history ownership
C1/C4 ownership only in the later interaction phase
scoped query-memory accounting
```

A candidate may not depend on planner invariants stronger than those enforced by
runtime source and focused tests.

## 2. Minimal hot-path API

The first production-shaped experiment should use one final concrete class, not
an interface hierarchy:

```java
final class NodeStateBucket {
    NodeState get(int stateId);
    void put(NodeState state, int nfaStateCount, MemoryTracker tracker);
    int activeSize();
    void appendStatesTo(HeapTrackingArrayList<State> target);
    Iterator<NodeState> iterator(); // compatibility/debug path, not hot path
    void release(MemoryTracker tracker); // only where ownership is retired early
}
```

The exact signature is revisited after characterization. Design intentions:

- `get` and `put` are monomorphic;
- `appendStatesTo` avoids allocating a lambda/iterator in `BFSExpander`;
- the outer `FoundNodes` scope owns the tracker, so a bucket need not retain a
  `MemoryTracker` reference unless measurements show that simpler ownership is
  worth the object-size cost;
- a compatibility iterator supports logging hooks but is not used in the
  performance-critical expansion loop.

Do not expose a general public collection abstraction.

## 3. D — current dense list

Representation:

```text
HeapTrackingArrayList<NodeState> of exact logical size S
```

Role: immutable control in every comparison.

Advantages:

- constant-time lookup/write;
- simple semantics;
- no promotion;
- good dense/cache-local behavior.

Costs:

- full backing array for every bucket;
- full `S` scan for each frontier iteration;
- full `S` merge in C4.

D is retained if no candidate clears common-case and maintainability gates.

## 4. I2 — two-inline-to-dense concrete bucket

Sparse layout:

```text
state0: NodeState
state1: NodeState
size: 0..2
```

Lookup compares requested ID with `state().id()` in up to two references.
Iteration emits only occupied inline references.

On the third distinct state:

```text
allocate raw NodeState[S]
copy state0/state1 into their state-ID slots
insert new state
clear inline references
never demote
```

Dense mode uses the raw array directly. It must **not** allocate a
`HeapTrackingArrayList` behind the bucket because that retains both wrapper and
list objects.

### Entry gate

Build I2 when baseline data show at least one of:

```text
>=60% of concrete buckets have k<=2
>=60% of iteration-weighted buckets have k<=2
state-bucket structural allocation is material and singleton/two-state buckets dominate
```

### Rejection gate

Reject or supersede I2 if:

```text
k=3..8 is a large stable regime and promotion causes material regressions
or
dense-mode shallow overhead materially exceeds D
or
lookup-heavy k=2 workloads regress >5% end to end
```

## 5. I4 — four-inline-to-dense concrete bucket

Same design with four inline references.

Advantages over I2:

- avoids promotion for more sparse/intermediate buckets;
- likely captures common `k=3/4` cases;
- still no backing array in sparse mode.

Costs:

- larger shallow object for every bucket, including `k=1`;
- up to four comparisons per lookup;
- larger dense-mode object than D/I2.

### Entry gate

Build I4 only when:

```text
k=3 or k=4 accounts for >=20% of bucket-, lookup-, or iteration-weighted traffic
and
runtime shallow-size measurement keeps I4 structurally below D for relevant S
```

I2 and I4 may be microbenchmarked together. Only one receives full end-to-end
implementation unless results are genuinely workload-dependent.

## 6. V — compact growable reference vector

Layout:

```text
NodeState[c]
size k
keys recovered from state().id()
```

Capacity sequence should be small and explicit, for example:

```text
2 -> 4 -> 8 -> dense S
```

or exact-size growth if allocation evidence favors it.

### Entry gate

Build V only when:

```text
k=3..16 is material
and
those buckets remain far below dense occupancy
and
promotion allocation in I2/I4 is predicted to dominate
```

### Risks

- extra backing-array object even for small `k`;
- growth copies and transient allocation;
- linear lookup at larger `k`;
- more lifecycle states than I2/I4.

V is rejected when I2/I4 captures at least 90% of the available memory benefit
with lower complexity.

## 7. SV — sorted compact vector

Layout is identical to V, kept sorted by `state().id()`.

Lookup uses binary search; insertion shifts references; merge can be linear.

### Entry gate

Build SV only when all apply:

```text
k=5..32 is material
insertion telemetry is predominantly nondecreasing or late mutation is rare
lookup-to-iteration ratio makes V's linear lookup expensive
C4 merge is a material later objective
```

A parallel `int[]` is not added unless profiling proves repeated
`NodeState.state().id()` dereference is materially expensive. Duplicating keys
weakens the memory argument.

## 8. H — Neo4j int-object hash-map control

Representation:

```text
HeapTrackingIntObjectHashMap<NodeState>
```

H is not the presumed solution. Its capacity-16 primitive-key and reference
arrays create high fixed cost for tiny buckets.

### Purpose

- establish whether expected O(1) sparse lookup matters in lookup-heavy regimes;
- provide a Neo4j-native hash baseline;
- falsify linear-vector optimism.

### Entry gate

Run collection-level H microbenchmarks unconditionally. Build an end-to-end H
branch only if:

```text
lookup-weighted k is beyond the inline regime
and
H beats the compact candidates on both lookup and structural memory at observed S/k
```

### Rejection gate

No end-to-end H branch when its fixed structural size exceeds D at the dominant
S values or exceeds I2/I4 by >25% without a compensating CPU advantage.

## 9. BM — bitmap plus packed references

Layout:

```text
long or long[] occupancy bits
NodeState[k or capacity]
```

Lookup is membership plus rank; iteration is packed; insertion may shift.

### Entry gate

Build only if:

```text
S<=64 or a cheap multiword rank scheme is demonstrated
k=8..S/2 is a material regime
I2/I4 and H leave a measured gap
```

BM is not justified for a workload set dominated by `k<=4` and dense tails.

### Rejection gate

Reject if rank/prefix machinery, shifting, or extra arrays erase the memory
advantage or add >5% common-case latency.

## 10. CH — segmented/chunked dense array

Layout for chunk width `B`:

```text
NodeState[ceil(S/B)][]
allocate one NodeState[B] only when a state in that chunk appears
```

Candidate widths:

```text
8, 16, 32
```

### Entry gate

Build CH only if telemetry shows:

```text
occupied chunks << k for at least one width
or
states are strongly clustered but not small enough for I2/I4
```

Concretely, require a predeclared useful-locality condition such as:

```text
median occupied-16-state chunks <= 2
while median k >= 8
```

### Risks

- two pointer loads per lookup;
- top-level array scales with `S/B`;
- poor scattered-state behavior;
- scan cost includes chunk table and nulls inside allocated chunks.

## 11. T — tagged singleton outer-map value

Layout:

```text
outer map value = NodeState for k=1
                | NodeStateBucket for k>=2
```

This removes even the bucket wrapper for singleton entries.

### Entry gate

T is a late-stage candidate only if:

```text
>=90% of bucket-, lookup-, and iteration-weighted observations are k=1
and
I2 leaves a material measured allocation/query-memory gap
```

### Risks

- type branch and cast on every outer-map hit;
- more invasive generic types and logging code;
- harder C4 transfer/merge ownership;
- poorer reviewability.

T is never the first upstream proposal.

## 12. RS — role-specialized P1 interaction design

Possible layout:

```text
frontier/buffer: compact iteration-oriented bucket
C1 allStates:    lookup-oriented adaptive/dense bucket
C4 retired:      transfer/merge-oriented bucket
```

RS is not an independent-P2 candidate. It becomes eligible only after one
universal P2 bucket has succeeded and P1 interaction proves a material conflict
between frontier and canonical operation mixes.

### Entry gate

```text
universal P2 passes independently
C1+P2 or C4+P2 has a measured role-specific regression/ceiling
specialization recovers >=5% latency or >=10% allocation/query-memory
```

The improvement must justify duplicated representation logic.

## 13. Memory-accounting design

A custom bucket must use Neo4j's `HeapEstimator` formulas and the same scoped
`MemoryTracker` as `FoundNodes`.

### Sparse construction

```text
tracker.allocateHeap(NodeStateBucket.SHALLOW_SIZE)
```

### Dense promotion

The candidate spec must decide explicitly whether to preserve the baseline
`newEmptyArrayList` extra `S` tracked bytes or replace them with raw-array
accounting. This is not hidden inside implementation.

Required evidence:

```text
actual structural allocation
MemoryTracker delta
query-memory boundary
```

If the baseline's additional `S` charge reflects a deliberate accounting
contract, the candidate preserves it. If it is collection-specific overhead
rather than represented heap, any changed accounting is documented and reviewed
separately from P2 speed.

### Early release

Independent B0/P2 retains all level structures to query close, so scoped tracker
close is sufficient. C1/C4 may release transferred/retired structures early.
`release(tracker)` must be idempotent by ownership contract and covered by
focused double-release tests.

## 14. Iteration design

The hot path should be:

```java
statesList.clear();
statesById.appendStatesTo(statesList);
```

Sparse mode emits `k` states. Dense mode scans `S` slots.

This keeps the first P2 experiment scoped to storage plus the unavoidable way
that representation exposes active states. Eliminating `statesList` itself is a
separate follow-up.

Logging/debug hooks may use `Iterable<NodeState>`, but iterator allocation is
not introduced into the main expansion path.

## 15. Put and identity behavior

For requested `stateId` and incoming `NodeState incoming`:

```text
empty slot:
    insert incoming

occupied by same object:
    no-op / return existing

occupied by different object:
    fail fast; canonical identity violation
```

Even though normal `BFSExpander.encounter` should prevent conflicting writes,
the bucket enforces this invariant so representation changes cannot silently
mask an algorithm error.

## 16. Dense promotion pseudocode

```text
put(incoming):
    id = incoming.state().id()

    if dense != null:
        validate-or-set dense[id]
        return

    if matching inline state exists:
        validate identity
        return

    if inline size < threshold:
        append inline
        return

    dense = allocate NodeState[S]
    for each inline state:
        dense[state.id] = state
    clear inline references
    dense[id] = incoming
```

Promotion is one-way. No hysteresis, demotion, or occupancy percentage branch is
introduced.

## 17. Threshold selection

Threshold is selected from a grid, not guessed:

```text
t = 1, 2, 3, 4, 6, 8
```

For each `t`, evaluate:

```text
weighted structural bytes
promotion rate
transient promotion bytes
lookup comparisons
iteration slots
end-to-end screening
```

The final rule should be an absolute small threshold with an optional minimum
`S` guard, for example:

```text
if S <= S_min: use dense immediately
else: inline threshold t
```

A percentage-only threshold is not preferred because `S=8,k=2` and
`S=256,k=8` have materially different constant costs.

## 18. Candidate advancement matrix

| Candidate | Collection microbench | Direct PPBFS | Planner controlled | External | P1 interaction |
| --- | --- | --- | --- | --- | --- |
| D | required | required | required | required | required |
| I2 | after telemetry gate | if admitted | if admitted | if screening passes | winner only |
| I4 | after telemetry gate | if admitted | if admitted | if screening passes | winner only |
| V/SV | only if mid-k gate | if admitted | if admitted | winner only | optional |
| H | required control | only if structural gate | only if direct passes | unlikely | no unless winner |
| BM | only if intermediate gate | if admitted | if admitted | winner only | optional |
| CH | only if locality gate | if admitted | if admitted | winner only | optional |
| T | late singleton gate | late | late | late | optional |
| RS | no independent claim | no | no | no | conditional only |

## 19. Complexity rule

When two candidates are within practical equivalence for latency and within 10%
of one another for allocation/query-memory benefit, select the candidate with:

```text
fewer representation states
fewer arrays/objects
simpler accounting
smaller production diff
simpler C4 ownership
stronger dense worst case
```

A small benchmark win does not justify a substantially more complex bucket.

## 20. Current analytical ranking

This is a pre-measurement ranking, not a result:

```text
1. I2 / I4 concrete inline-to-raw-dense
2. V or SV only if the measured middle is large
3. H as lookup control
4. CH if clustering is real
5. BM if an intermediate regime remains
6. T only for overwhelming singleton dominance
7. RS only after universal P2 succeeds with P1
```

No production candidate branch is created until the baseline telemetry suite is
validated and run.