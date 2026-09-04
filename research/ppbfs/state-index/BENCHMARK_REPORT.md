# PPBFS direct product-state repository benchmark report

## 1. Executive verdict

`MEASURED`: the baseline history scan is materially expensive on deep PPBFS
workloads, and C1 removes that cost with a 4.373x deep roadNet-PA geometric-mean
speedup. However, every tested direct canonical repository fails the mandatory
fixed-memory condition that B0 passes. C3, the final permitted Pareto-recovery
candidate, failed the first 92 MiB gate. The production result is therefore B0
unchanged and no upstream proposal.

## 2. Exact environment

```text
audit date: 2026-09-04 Europe/Bucharest
repository: neo4j/neo4j
upstream branch: 2026.07
baseline SHA: f213380f812b820a1b312e2ea52cb3d8f1931ccc
OS: Windows 11 Pro 25H2 build 26200.9168 x86-64
CPU: AMD Ryzen 9 9955HX, 16 physical / 32 logical cores
RAM: approximately 31.2 GiB
JDK: Eclipse Temurin 21.0.12.1+1 LTS
Maven: 3.9.11
server heap: 4 GiB initial / 4 GiB maximum
page cache: 8 GiB
large artifacts and Maven cache: D:
```

Distribution, configuration, dataset, manifest, JVM, and hardware metadata are
bound to retained runs. Candidate reports record exact source and runtime-JAR
hashes.

## 3. Hypothesis

`DERIVED`: replacing history-depth-linear lookup with expected-constant-time
query-local canonical lookup should reduce the lookup component from
`Theta(X + sum(h_i))` to expected `Theta(X)`. It is acceptable only if object
identity, results, query-memory behavior, and ordinary latency remain safe.

## 4. Source-confirmed problem

`SOURCE-CONFIRMED`: at B0, `BFSExpander.encounter` calls
`FoundNodes.get(nodeId,stateId)` for accepted product transitions. `get` probes
the buffer, active frontier(s), then historical levels newest-to-oldest. Private
history has no other semantic consumer; identity-bearing path state lives on
the canonical `NodeState` and related signpost structures. See
`reports/SOURCE_AUDIT.md`.

## 5. Mathematical expectation

For a new product state at history depth `H`, lookup is expected
`Theta(1+H)`. A chain with one accepted new state per depth accumulates
`Theta(D^2)` lookup bookkeeping. A primitive-key direct repository makes each
lookup expected `Theta(1)` but adds structural memory and insertion cost. These
claims apply to lookup, not total PPBFS execution. See
`reports/MATHEMATICAL_MODEL.md`.

## 6. Benchmark methodology

- Instrumentation was isolated in commit
  `011f242418427361f2659535fd5ceca27ec4dbdf`.
- Real timing used independently restarted paired JVM forks, seeded within-pair
  ordering, complete warmups, within-fork medians where repetitions existed,
  paired log ratios, two-sided 95% Student-t intervals, and append-only raw data.
- Every included real query returned equal path lengths and executed a nested
  `StatefulShortestPath(Into, Trail)` operator.
- The real graph was SNAP roadNet-PA, SHA-256
  `450B8733635D887466A2B96B26411F6E62CAF7006F8A264F59CF9B5D75CDF549`.
- Fixed-limit tests restarted Neo4j with isolated `NEO4J_CONF`, verified
  `db.memory.transaction.max` using `SHOW SETTINGS`, and saved complete HTTP
  responses before validating outcomes.
- The shared runner and analyzers are under `../common/scripts/`; bulky raw
  evidence is under `D:/dev/neo4j-research/artifacts/ppbfs/`.

## 7. Controlled complexity results

`MEASURED`: controlled chain depths 4 through 4096 reproduced the predicted
growth. At depth 4096, B0 performed 8,382,465 historical probes for 4,097
accepted lookup attempts. This passes the materiality scaling condition.
Diamonds and layered-DAG fixtures remain available as correctness/topology
controls, but no publication claim is based on synthetic timing alone.

## 8. Real graph results

For C1, speedup is B0 elapsed time divided by C1 elapsed time.

| roadNet-PA distance | B0 median ms | C1 median ms | Geomean speedup | 95% CI |
| ---: | ---: | ---: | ---: | ---: |
| 250 | 1,156.741 | 434.743 | 2.233x | [0.840x, 5.933x] |
| 500 | 9,511.730 | 2,067.130 | 4.481x | [1.685x, 11.918x] |
| 772 | 33,316.613 | 3,623.778 | 8.355x | [6.371x, 10.957x] |
| deep aggregate | — | — | 4.373x | [2.126x, 8.993x] |

`MEASURED`: this establishes a significant deep-path benefit on one real graph,
not across three topology families. C2 retained 89.9% of C1 performance overall
and 85.6% on deep pairs in its three-fork screening run. C3 timing was `NOT RUN`
because its first memory gate failed.

## 9. LDBC and regression results

Higher-repetition shallow roadNet-PA timing for C1 produced a 1.182x aggregate
point estimate with 95% CI `[0.870x,1.606x]`; no distance had a point-estimate
regression above 2%, but fork results ranged from 0.849x to 1.520x. The broad
common-case gate is therefore `NOT PROVEN`. LDBC SNB and additional real graph
families were `NOT RUN — candidates rejected at the memory gate before broader
qualification`.

## 10. Internal causal metrics

`MEASURED`: B0 JFR attributed 468 of 919 execution samples (50.9%) to
`FoundNodes.get`; C1 reduced this to 2 of 817 (0.24%). Together with the
controlled probe curve and direct-lookup hooks, this supports causal attribution
of C1's deep improvement to removal of historical probing.

## 11. Memory analysis

Plan-reported C1 operator memory ranged from -2.91% to +1.46% versus B0 across
the seven roadNet-PA pairs. That aggregate view concealed an allocator boundary:

| Variant | 92 MiB | 93 MiB | 94 MiB | Minimum observed pass |
| --- | --- | --- | --- | ---: |
| B0 | PASS | PASS | PASS | 92 MiB |
| C1 dense node-major | FAIL | PASS | not needed | 93 MiB |
| C2 state-major maps | FAIL | FAIL | PASS | 94 MiB |
| C3 adaptive node-major, capacity 2 | FAIL | not run | not run | greater than 92 MiB |

C3's canonical run records 91 MiB in use and rejection of the next 2 MiB
allocation. Capacity 1 uses the same inline bucket size and promotes earlier,
so it cannot repair this low-occupancy failure. Object-layout/JOL and crossover
sweeps were `NOT RUN — no candidate survived the decisive limit gate`.

## 12. Allocation and GC analysis

Detailed allocation/GC comparisons were `NOT RUN — stopped by the fixed-limit
gate`. All candidate structural allocation uses Neo4j's scoped heap tracker;
tests verify exact zero after normal early close and interruption cleanup. No
candidate uses `allocateHeapNoThrow` or evades query accounting.

## 13. Candidate matrix

| Candidate | Canonical representation | Correctness | Memory result | Decision |
| --- | --- | --- | --- | --- |
| B0 | level-partitioned history | PASS | passes 92 MiB | retain |
| C1 | node -> dense state array | PASS | fails 92 MiB | reject |
| C2 | state -> primitive node map | PASS | first passes 94 MiB | reject |
| C3 | node -> inline sparse/dense bucket | PASS | fails 92 MiB | reject |

The optional third C3 tier was not implemented because no measured middle
regime could override the failed low-occupancy boundary.

## 14. Pareto selection

C1 owns the strongest measured deep latency but is dominated by B0 on the
mandatory memory-boundary dimension. C2 worsens that boundary and loses
material screening performance versus C1. C3 does not recover B0's boundary.
Under the ordered gates, B0 is the only admissible design and therefore the
production choice.

## 15. Regressions and negative results

- C1: one-MiB worse observed transaction-memory boundary; shallow uncertainty.
- C2: two-MiB worse boundary than B0 and 10.1% overall / 14.4% deep screening
  loss versus C1. Classified as map structural overhead/cache locality plus
  measurement noise pending deeper profiling.
- C3: failed the same 92 MiB boundary as C2. Classified as bucket/global-map
  structural overhead; deeper attribution was intentionally stopped.
- Invalid or interrupted runs are retained and explicitly excluded. The first
  C2 distribution accidentally contained C1 bytecode; all four affected runs
  are excluded. C3 `v1` used an abbreviated SHA; identical full-SHA `v2` is the
  canonical record.

## 16. Acceptance-gate evaluation

| Gate | Result |
| --- | --- |
| preflight / public overlap | PASS; none found on 2026-09-04 |
| source compatibility | PASS |
| materiality | PASS |
| semantic correctness | PASS for C1/C2/C3 focused evidence |
| physical operator | PASS for included real queries |
| causal attribution | PASS for C1 |
| real benefit | PASS on one graph; multi-topology alternative not proven |
| common-case regression | NOT PROVEN |
| tracked-memory percentage | observed PASS for C1 profiles |
| identical-limit memory | FAIL for C1, C2, and C3 |
| allocation/GC | NOT RUN after decisive failure |
| upstream gate | FAIL; no production candidate |

## 17. Limitations

The evidence is Windows/Java-21 specific and uses one real topology. It does not
establish LDBC behavior, multi-topology generality, JOL layout, p95/p99 latency
from sufficiently large independent samples, or allocation/GC effects for C3.
Those omissions do not weaken the NO-GO: the near-limit contract is mandatory,
and all direct candidates already failed it. The result does not prove that all
possible future algorithms or data structures are impossible.

The requested single all-phases reproduction command was not expanded after
the decisive gate failure. The retained variant-neutral preparation, server,
memory-matrix, metadata, and statistical scripts remain separately runnable;
creating more orchestration for a rejected patch would not change the decision.

## 18. Final recommendation

Keep B0 unchanged. Preserve the lab commits and raw evidence for provenance, do
not transfer C1/C2/C3 production code to the clean contribution branch, and do
not contact upstream about a patch under the current acceptance contract.

NO-GO — MEMORY REGRESSION IS UNACCEPTABLE
