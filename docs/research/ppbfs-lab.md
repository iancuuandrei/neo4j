# StatefulShortestPath / PPBFS research lab

## Status

**Active shared lab; direct-state-index experiment concluded.** Experimental
code and local evidence exist; no PPBFS change from this lab has been proposed
or accepted upstream.

## Scope

The lab consolidates work involving:

- `StatefulShortestPath` runtime entry points;
- `PGPathPropagatingBFS`, `FoundNodes`, and `BFSExpander`;
- `NodeState`, `PathTracer`, and `TwoWaySignpost` invariants;
- `ProductGraphTraversalCursor` and NFA/product-state traversal;
- unidirectional and bidirectional execution;
- shared instrumentation, datasets, plan verification, and statistics.

## Experiment relationships

```text
PPBFS research
├── direct historical state lookup
│   └── possible contrib/ppbfs-direct-state-index
├── frontier work selection
│   └── possible independent contribution if validated
└── NFA transition dispatch
    └── possible independent contribution if validated
```

The research branch is shared because the experiments need the same architecture
model, datasets, queries, instrumentation, and regression framework. Upstream
contribution branches remain independent unless one change genuinely cannot
function or be reviewed without another.

## Shared infrastructure

[`research/ppbfs-lab`](https://github.com/iancuuandrei/neo4j/tree/research/ppbfs-lab)
contains:

- source audit and mathematical model;
- observation hooks and controlled materiality tests;
- deterministic SNAP conversion and source-target selection;
- plan-verified StatefulShortestPath queries;
- safe per-JVM-fork timing runner;
- hardware/JVM/input-hash metadata capture;
- machine-readable experimental results and reports.

Large datasets, stores, distributions, profiles, and bulky logs are reproducible
but intentionally excluded from Git.

## Current experiments

| Experiment | Status | Contribution path |
| --- | --- | --- |
| Direct product-state lookup | Rejected at memory gate | [Case study](../contributions/ppbfs-direct-state-index.md) |
| Frontier work selection | Planned | [Case study](../contributions/ppbfs-frontier-work-selection.md) |
| Transition dispatch | Planned | [Case study](../contributions/ppbfs-transition-dispatch.md) |

## Evidence policy

- **Source-confirmed:** verified directly in the audited Neo4j source.
- **Research-supported:** supported by cited literature or another engine, not proof for Neo4j.
- **Experimental:** observed locally in incomplete or narrow runs.
- **Validated:** reproduced under the documented multi-fork, multi-workload protocol.
- **Upstream-confirmed:** acknowledged by Neo4j maintainers.
- **Merged:** accepted into the official upstream repository.

Negative and superseded candidates remain documented when they provide useful
engineering information.
