# Deferred PPBFS product-state indexing

## Status

**Proposed; draft fork PR open, not submitted upstream.** A minimal review branch exists at
[`contrib/ppbfs-deferred-state-index`](https://github.com/iancuuandrei/neo4j-contributions/tree/contrib/ppbfs-deferred-state-index),
targeting `2026.07`. Design and evidence are recorded in draft fork PR
[iancuuandrei/neo4j-contributions#3](https://github.com/iancuuandrei/neo4j-contributions/pull/3)
(open, draft; explicitly not an upstream PR). Related upstream discussion:
[neo4j/neo4j#13966](https://github.com/neo4j/neo4j/issues/13966), which is also the
discussion venue for the sibling C1 candidate
([ppbfs-direct-state-index](ppbfs-direct-state-index.md)).

## Problem and design

`FoundNodes.get(nodeId, stateId)` scans retired BFS levels newest-to-oldest, so lookup
cost grows with retained history depth: a controlled depth-4096 chain recorded 8,382,465
history probes for 4,097 lookups. Instead of indexing every accepted product state up
front (C1), this candidate defers indexing until retired history exceeds eight levels.
The first qualifying retiring frontier becomes the retired index; later unique node
buckets are transferred and repeated-node buckets are merged by state slot while
preserving exact `NodeState` identity.

## Results

```
roadNet-PA d250+          B0/C4 4.370x [3.838, 4.976]
roadNet-CA d250+          B0/C4 4.098x [3.231, 5.197]
Hetionet H3 (10 forks)    1.003x [0.973, 1.033], equivalent within +/-5%
bounded two-fork confirm  7.589x at PA d772, 7.267x at CA d800
```

H=4/8/16 sensitivity: identical results in all 45 executions; deep point estimates
within 5.7%.

## Trade-off

C4 retains C1-class deep speed and allocated less than C1 in every qualified JFR
recording, but adds retirement/ownership complexity. It does not uniformly reduce
memory versus baseline: selected d250 transaction-memory boundaries moved by +2 MiB,
and deep-road allocation remained +3.8% to +5.7% versus B0. Maintainer feedback on
the ownership model is explicitly requested before upstream submission.

## Correctness

- Focused PPBFS/C4 tests: 114 executed, 0 failures/errors, 5 existing skips.
- Complete `community/cypher/runtime-util` suite: 529 tests, 0 failures/errors,
  5 existing skips.
- Community build: 129 modules, PASS.

## Upstream posture

No upstream PR has been opened. Upstream discussion
[neo4j/neo4j#13966](https://github.com/neo4j/neo4j/issues/13966) covers the
history-depth-linear lookup problem shared with the C1 candidate.

## Links

- [Draft fork PR #3](https://github.com/iancuuandrei/neo4j-contributions/pull/3)
  (design, evidence, and validation source for this note)
- [Simpler C1 draft PR #2](https://github.com/iancuuandrei/neo4j-contributions/pull/2)
- [Upstream discussion neo4j/neo4j#13966](https://github.com/neo4j/neo4j/issues/13966)
