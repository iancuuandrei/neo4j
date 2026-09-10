# NFA definitions used by the direct-PPBFS scan

The scan driver builds these product-graph NFAs with the 2026.08
`org.neo4j.internal.kernel.api.helpers.traversal.productgraph` classes
(`State`, `RelationshipExpansion`, `RelationshipPredicate`, `SlotOrName`).
`SearchMode` is derived by `PGPathPropagatingBFS.create`: unbound
(`intoTarget == NO_SUCH_ENTITY`) implies `Unidirectional`. Path mode is
`TraversalPathModeFactory.walkMode()` except for the TRAIL control runs.

## walk1 — single-state all-outgoing WALK NFA (main scan)

One state that is both start and final, with a single self-loop expansion
matching every relationship in the OUTGOING direction:

```java
State s = new State(0, SlotOrName.none(), n -> true, true, true);
int nfaStates = 1;
RelationshipExpansion re = new RelationshipExpansion(
        s, RelationshipPredicate.ALWAYS_TRUE, null, Direction.OUTGOING, SlotOrName.none(), s);
s.setRelationshipExpansions(new RelationshipExpansion[] {re});
s.setReverseRelationshipExpansions(new RelationshipExpansion[] {re});
```

`types == null` means no relationship-type filtering; the node predicate is
`n -> true`. This is the NFA behind all LiveJournal, web-Stanford, as-Skitter,
and untyped Hetionet discovery/timing runs.

## chain2 — typed two-state Hetionet NFA

Used once, as `chain2:G_I_G,G_I_G` on Hetionet Gene sources: start state `q0`
takes one `G_I_G` expansion to final state `q1`, which loops on `G_I_G`.
Type names are resolved to ids through `ktx.tokenRead()` in the running
transaction:

```java
State q0 = new State(0, SlotOrName.none(), n -> true, true, false);
State q1 = new State(1, SlotOrName.none(), n -> true, false, true);
RelationshipExpansion e1 = new RelationshipExpansion(
        q0, RelationshipPredicate.ALWAYS_TRUE, new int[] {t1}, Direction.OUTGOING,
        SlotOrName.none(), q1);
RelationshipExpansion e2 = new RelationshipExpansion(
        q1, RelationshipPredicate.ALWAYS_TRUE, new int[] {t2}, Direction.OUTGOING,
        SlotOrName.none(), q1);
q0.setRelationshipExpansions(new RelationshipExpansion[] {e1});
q0.setReverseRelationshipExpansions(new RelationshipExpansion[0]);
q1.setRelationshipExpansions(new RelationshipExpansion[] {e2});
q1.setReverseRelationshipExpansions(new RelationshipExpansion[] {e2});
// startState = q0, finalState = q1, nfaStates = 2
```

Result on the study: 0 metadata mismatches, 13.5% push / 17.4% post-sat
elimination — product-graph multiplicity does not rescue Hetionet's low
data-graph reconvergence.
