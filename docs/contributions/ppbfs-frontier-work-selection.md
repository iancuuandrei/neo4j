# PPBFS frontier work selection

## Status

**Planned.** No implementation branch, benchmark result, issue, or pull request
exists.

## Summary

This potential investigation would evaluate bidirectional PPBFS expansion-side
scheduling. It remains part of the shared
[PPBFS Research Lab](../research/ppbfs-lab.md) until evidence justifies an
independent upstream contribution.

## Problem and architecture

The possible scope includes bidirectional `PGPathPropagatingBFS` frontier choice,
work estimation, termination behavior, and interactions with `FoundNodes` and
signpost propagation. No source-level weakness or representative performance
impact has yet been established for this topic.

## Analysis, design, and implementation

Pending source audit and benchmark validation. A future implementation branch
would be created directly from then-current upstream and would not depend on the
direct-state-index contribution unless a genuine architectural dependency were
demonstrated.

## Correctness and benchmarks

Pending. Required work would include bidirectional differential correctness,
termination/selector semantics, expansion-balance metrics, real graph workloads,
and common-case regression tests using the shared PPBFS harness.

## Upstream process

```text
Issue: Not submitted upstream
PR: Not submitted upstream
Outcome: Planned research only
```

## Relevant links

- [Shared PPBFS research branch](https://github.com/iancuuandrei/neo4j/tree/research/ppbfs-lab)
- [PPBFS benchmark methodology](../benchmarks/ppbfs-benchmark-suite.md)
