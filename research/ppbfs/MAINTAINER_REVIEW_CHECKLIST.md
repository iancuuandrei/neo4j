# PPBFS deferred-index review checklist

- [ ] one `NodeState` identity remains canonical per `(nodeId, stateId)`
- [ ] active and retired bucket ownership is disjoint
- [ ] transferred buckets are not closed at retirement
- [ ] merged incoming buckets are closed exactly once
- [ ] an occupied merge slot accepts only the identical `NodeState`
- [ ] frozen history never grows after activation
- [ ] empty retirements cannot activate indexing
- [ ] bidirectional retirement shares one canonical lookup domain
- [ ] scoped `MemoryTracker` close releases all structural accounting
- [ ] Trail, Walk, Acyclic, bidirectional, and shortest-path lifecycle tests remain green
- [ ] H=8 is acceptable as a conservative fixed threshold
- [ ] internal Linux performance confirms no important common-case regression

