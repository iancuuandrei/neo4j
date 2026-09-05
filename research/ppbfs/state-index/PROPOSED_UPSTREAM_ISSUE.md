# Upstream issue record

## Status

Posted to `neo4j/neo4j` as [issue #13966](https://github.com/neo4j/neo4j/issues/13966) on 2026-09-05 at `18:22:39Z`.

```text
research head at posting: 81ea6c0cefbee54aa2d9610d8faf84e4acf4a509
clean candidate head:      0a8ef51868e9c8f52dfd49c516d0a14a0295b2c6
upstream target:           2026.07@f213380f812b820a1b312e2ea52cb3d8f1931ccc
issue number:              13966
issue URL:                 https://github.com/neo4j/neo4j/issues/13966
```

The body below is the exact maintainer-facing content posted at creation time. Later maintainer discussion belongs on the issue; do not rewrite this historical record.

## Title

StatefulShortestPath: investigate history-depth-linear product-state lookup in FoundNodes

## Body

### Problem

At `neo4j/neo4j@f213380f812b820a1b312e2ea52cb3d8f1931ccc` (`2026.07`), `BFSExpander.encounter(nodeId,state)` resolves canonical product states through `FoundNodes.get(nodeId,stateId)`. That method checks active structures and then scans historical BFS levels newest-to-oldest. Lookup cost therefore grows with retained BFS depth.

### Causal evidence

A controlled depth-4096 chain recorded **8,382,465 historical probes for 4,097 accepted lookups**. In a representative roadNet-PA JFR, `FoundNodes.get` accounted for 468/919 B0 execution samples (50.9%); a query-local canonical node-major prototype reduced this to 2/817 (0.24%).

### Real performance evidence

Five paired fresh-JVM forks, unprofiled timing, B0 elapsed/C1 elapsed:

- roadNet-PA deep: **4.373x**, 95% CI `[2.126x, 8.993x]`
- roadNet-CA deep: **5.289x**, `[4.808x, 5.818x]`; approximately **8.3x near depth 800**
- web-Stanford: **1.079x**, `[1.060x, 1.098x]`
- as-Skitter low-diameter control: **1.014x**, `[0.989x, 1.039x]` (statistically unresolved)

The evidence supports a depth-sensitive PPBFS optimization, not a universal graph-query speedup.

### External falsification

- Hetionet H3: **equivalent within +/-5%** (0.996x; 90% CI `[0.962x, 1.032x]`)
- cit-Patents: **practically non-inferior** (1.220x; 90% CI `[1.029x, 1.448x]`)
- LiveJournal: **inconclusive** (0.948x; 95% CI `[0.782x, 1.149x]`); a possible shallow/high-fanout regression remains disclosed
- FinBench: official path workloads did not naturally execute `StatefulShortestPath`, so they did not qualify as direct PPBFS evidence
- gMark: the generated instance was too shallow to resolve the intended deep-RPQ question

All retained timing comparisons returned identical path lengths and executed the intended `StatefulShortestPath` operator.

### Trade-off

C1 has a small but real memory/allocation cost. Representative road fixed-limit shifts were **0–1.96%**; roadNet-PA d250 moved from **92 to 93 MiB** minimum observed passing configuration. The original representative JFR estimated approximately **+5.5–7.0%** total allocation. Configured-limit boundary shifts are not equivalent to resident-memory overhead.

### Correctness and validation

- full `community/cypher/runtime-util`: **526 tests, 0 failures/errors, 5 existing skips**
- focused clean candidate: **76 tests, 0 failures/errors, 5 existing skips**
- Spotless: **PASS**

### Questions for maintainers

1. Is the measured query-memory/allocation cost acceptable for this level of deep `StatefulShortestPath` improvement?
2. Is a query-local canonical `(nodeId,stateId) -> NodeState` repository consistent with the intended PPBFS ownership model, or is there another internal representation/data structure you would prefer?
3. Is the minimal C1 patch worth reviewing, or would you prefer further investigation first?

### Links

- Full research/evidence archive: https://github.com/iancuuandrei/neo4j/pull/1
- Minimal clean candidate: https://github.com/iancuuandrei/neo4j/pull/2
