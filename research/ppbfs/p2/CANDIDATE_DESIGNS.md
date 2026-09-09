# P2 candidate designs (v1: screening freeze)

## D — dense array (control)

Unchanged baseline: `NodeState[S]`, O(1) lookup/insert, O(S) iteration, O(S) memory/merge.
Expected to win tiny-NFA (S ≤ 4) and full-occupancy buckets.

## V — sorted compact vector (primary candidate) ✅ screening PASS

Sorted `NodeState` refs by `state().id()`; ascending iteration preserved; linear lookup;
append fast-path on `id > lastId` (monotonic inserts measured); idempotent re-put;
close releases backing array; exact `MemoryTracker` accounting; no boxing/lambdas/streams.
API (package-private, final class — no interface):

```java
final class StateBucket implements AutoCloseable {
    NodeState get(int stateId);
    void put(NodeState nodeState);
    int activeSize();
    void appendActiveStatesTo(HeapTrackingArrayList<State> target);
}
```

Replaces the per-node dense list in buffer/frontier/history maps on B0 (no C1/C4 changes).
End-to-end branch: `research/ppbfs-p2-sorted-vector` from clean `f213…`.

## DA — dense + active list (causal control) ✅ screening PASS as control

Keeps `NodeState[S]` for lookup plus compact active-id list for iteration. Separates null-scan
savings from lookup-representation effects. Costs more memory than D; never a graduation candidate.
Branch only if V-vs-D needs causal decomposition: `research/ppbfs-p2-dense-active-list`.

## A — one-way compact→dense (conditional on V/D crossover)

Start V, promote once to D at measured `k` threshold (hypothesis ≈ 8–16). No demotion; promotion
releases compact backing exactly once. Branch `research/ppbfs-p2-adaptive` only after fixed-V
end-to-end shows a real crossover.

## R — range-compressed array (deferred)

`offset + NodeState[span]`, O(1) lookup after range check, O(span) iteration. Observed span ≈ k
with single runs gives R ≈ V everywhere with no demonstrated win; dispersed/source buckets show
span > k where R wastes. Implement only if V end-to-end shows lookup-heavy regressions.

## H — primitive int→object map (rejected as candidate, retained as documented control)

Fixed 16-cap overhead (≥300 B — worse than dense itself at S ≤ 64), iteration 2–8× V.
No end-to-end branch.
