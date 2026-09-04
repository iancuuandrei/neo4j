# PPBFS state-repository candidate matrix

## Decision policy

Candidates are evaluated as Pareto alternatives, not an implementation queue.
Correctness and the identical-limit memory gate precede timing. A more complex
candidate is implemented only when the preceding candidate leaves a measured
memory/performance gap.

| Candidate | Representation | Status | Reason |
| --- | --- | --- | --- |
| B0 | Level-partitioned node-major history | retained reference | authoritative upstream behavior |
| C1 | Canonical `nodeId -> dense state array` | rejected | `MEASURED`: fails at 92 MiB where B0 passes |
| C2 | Canonical `stateId -> nodeId map` | rejected as final; retained comparator | `MEASURED`: 94 MiB minimum versus B0's 92 MiB and C1's 93 MiB; screening point estimate is 10.1% slower than C1 overall |
| C3 | Adaptive canonical node-major buckets | triggered; implementation pending | C2 leaves both the predefined memory weakness and a material screening-performance gap |

## C2 qualification order

1. Focused correctness and memory-accounting tests.
2. Identical transaction-memory-limit boundary against B0.
3. B0/C1/C2 controlled and real-workload comparisons.
4. Common-case and allocation/GC checks.

Stop at C2 when it:

- preserves at least 90–95% of C1 performance on relevant workloads;
- passes every memory limit at which B0 passes;
- has neutral or better memory than B0; and
- otherwise remains Pareto-best under correctness and implementation complexity.

Trigger C3 only if C2 is more than 10–15% slower than C1 on relevant workloads
or retains another material memory/performance weakness. If C2 equals or beats
C1 while fixing memory, C3 must not be built.

`MEASURED`: C2 triggered both alternatives. Its valid three-fork roadNet-PA
screen retained 89.9% of C1 performance overall and 85.6% on deep pairs. More
decisively, it failed at 92 and 93 MiB and first passed at 94 MiB, while B0
passes at 92 MiB and C1 first passes at 93 MiB. See
`state-index/results/2026-09-04/C2_ROADNET_PA.md`.

## Conditional C3 definition

C3 is a canonical node-major adaptive product-state repository:

```text
nodeId
  -> StateBucket
       -> tiny sparse representation at very low occupancy
       -> optional primitive int-to-NodeState map only if crossover data requires it
       -> dense stateId-indexed array at high occupancy
```

Its purpose is to recover any measured C1-like node-major/cache-local advantage
without retaining C1's sparse `O(N * |Q|)` slot allocation. The first prototype,
if triggered, uses only tiny-sparse to dense. A middle primitive map is added
only after measured crossover evidence.

For each data node `v`, record:

`rho_v = activeStates(v) / |Q|`.

The sparse/dense transition must be selected from measured occupancy, latency,
tracked heap, and allocation data. No arbitrary threshold is allowed.

## Required comparison dimensions

Compare B0, C1, C2, and C3 only if triggered, on:

- end-to-end and direct-lookup latency;
- peak Neo4j tracked heap;
- minimum passing transaction-memory limit;
- allocation and GC;
- common-case regression;
- implementation and review complexity.

The final design is the simplest Pareto-best candidate. Sophistication is not a
selection criterion.
