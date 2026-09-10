# PPBFS transition dispatch

## Status

**Planned.** No implementation branch, benchmark result, issue, or pull request
exists.

## Summary

This potential investigation would study NFA transition-dispatch cost inside
product-graph traversal. It shares PPBFS architecture and benchmarks but would
remain an independent contribution if evidence justified production work.

## Problem and architecture

The possible scope includes `ProductGraphTraversalCursor`, NFA state transitions,
relationship-type/predicate dispatch, and their interaction with PPBFS expansion.
No material bottleneck or selected design has been established.

## Analysis, design, and implementation

Pending source audit and profiling. No production change is proposed. Any future
candidate would start from current upstream and be compared independently and in
research-only combinations where scientifically useful.

## Correctness and benchmarks

Pending. Required evidence would cover transition semantics, predicates,
direction, runtime plans, allocation/dispatch metrics, real topologies, and broad
regression behavior using the shared PPBFS harness.

## Upstream process

```text
Issue: Not submitted upstream
PR: Not submitted upstream
Outcome: Planned research only
```

## Relevant links

- [Shared PPBFS research branch](https://github.com/iancuuandrei/neo4j-contributions/tree/research/ppbfs-lab)
- [PPBFS benchmark methodology](../benchmarks/ppbfs-benchmark-suite.md)
