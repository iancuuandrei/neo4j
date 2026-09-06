# PPBFS deferred product-state index: maintainer handoff

## Problem

`FoundNodes.get(nodeId, stateId)` checks active structures and then scans every
retired BFS level. On deep `StatefulShortestPath` searches, this makes lookup
bookkeeping grow with history depth. A controlled depth-4096 chain measured
4,097 lookups and 8,382,465 historical probes; JFR attributed about 50.9% of B0
execution samples to `FoundNodes.get` on a representative deep road query.

## Existing B0 behavior

```text
active buffer/frontier -> history[level][node][state]
                           ^ scan newest to oldest for every miss
```

## C1

```text
active scheduling buckets
          +
always-on canonical node -> state[] index
```

C1 is the smallest conceptual change and has the strongest original latency
result. It duplicates canonical and scheduling bucket structures from the first
discovery, increasing allocation and shifting selected memory boundaries.

## C4

```text
first H=8 retired levels -> frozen bounded prefix
later retired frontier  -> node -> state[] retired index
                            move unique buckets; merge repeated nodes

lookup: buffer -> active frontier(s) -> retired index -> frozen prefix
```

C4 defers indexing until history is deep enough to matter. The first qualifying
retiring map becomes the index. Later unique buckets are transferred, not
copied; repeated-node buckets merge by state slot and require exact object
identity on collisions. Active storage is never aliased into retired storage.

## Why C4 exists

| | C1 | C4 |
| --- | --- | --- |
| direct lookup | always on | after eight retired levels |
| canonical storage | separate bucket structure | ownership transfer/coalescing |
| implementation | simpler | additional retirement lifecycle |
| measured deep speed | strongest | C1-class |
| measured allocation | above B0 | below C1 in every qualified JFR; still above B0 on deep roads |

## Performance

| Complete synchronized study | Result |
| --- | ---: |
| roadNet-PA d250+, B0/C4 | 4.370x [3.838, 4.976] |
| roadNet-CA d250+, B0/C4 | 4.098x [3.231, 5.197] |
| Hetionet H3, B0/C4, 10 forks | 1.003x [0.973, 1.033], equivalent within +/-5% |
| LiveJournal, C1/C4, 10 forks | 1.189x [1.004, 1.408] |

The exact clean extraction separately reproduced 7.589x at PA d772 and 7.267x
at CA d800 in a bounded two-fork confirmation. Those smaller checks validate
the extraction; they do not replace the full study.

## Memory and allocation

| Evidence | Result |
| --- | --- |
| qualified JFR | C4 allocated less than C1 in every recording |
| external controls | approximately B0-level allocation |
| deep roads | C4 remained +3.8% to +5.7% allocation versus B0 |
| PA d250 minimum observed limit | B0 92, C1 93, C4 94 MiB |
| CA d250 minimum observed limit | B0 102, C4 104 MiB |
| other anchors | many equal; CA d800 C4 observed slightly below B0 |

These are workload-dependent tracked-memory observations. They do not establish
that C4 generally uses less memory.

## Correctness

The clean patch adds focused activation, identity, ownership-transfer, merge,
bidirectional, empty-retirement, and cleanup tests. The exact candidate passed
114 focused tests and the complete 529-test `runtime-util` suite with zero
failures/errors (five existing skips), plus the 129-module Community build.
Existing PPBFS suites exercise Trail, Walk, Acyclic, bidirectional, interruption,
and shortest-path lifecycle behavior.

## Why H=8

Three-fork H=4/8/16 sensitivity checks returned identical results throughout.
Deep-road point estimates differed by at most 5.7%, with intervals generally
including parity; shallow controls were too noisy to prove equivalence. H=8 is
therefore a conservative simple point in a broad measured region, not a tuned
optimum. See [C4_THRESHOLD_SENSITIVITY.md](c4/C4_THRESHOLD_SENSITIVITY.md).

## Neo4j convention check

The patch uses the existing `HeapTrackingLongObjectHashMap`,
`HeapTrackingArrayList`, and scoped-memory lifecycle already used by PPBFS.
`HeapTrackingLongObjectHashMap.close()` releases only the map's own arrays and
does not close values, which makes bucket transfer valid but requires explicit
closing of merged/discarded incoming buckets. No established collection helper
was found that expresses this move-or-slot-merge ownership operation more
directly. No public API or store format changes.

## Open decision

**C1 is the simplest implementation. C4 has the strongest measured Pareto
balance.** The maintainer choice is whether C4's retirement/ownership complexity
is justified by its lower shallow/allocation cost relative to C1. Linux results
have not been measured here; an internal Linux common-case check remains prudent.

