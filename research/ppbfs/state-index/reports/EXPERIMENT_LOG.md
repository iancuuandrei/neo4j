# State-index experiment log

## 2026-09-04 — preflight and materiality

- Refreshed `origin` and `upstream`; upstream default remained `2026.07` at `f213380f812b820a1b312e2ea52cb3d8f1931ccc`.
- Repeated public overlap searches; no materially equivalent implementation was found.
- Added observation-only `PPBFSHooks` callbacks around lookup location/probes and dense bucket allocation.
- Focused PPBFS suite: 120 tests, 0 failures, 0 errors, 5 skipped.
- Instrumentation commit: `011f242418427361f2659535fd5ceca27ec4dbdf`.
- Controlled chain depths 4 through 4096 reproduced quadratic probe growth: 8,382,465 probes for 4,097 accepted lookups at depth 4096.
- Baseline JFR attributed 468 of 919 execution samples (50.9%) to `FoundNodes.get`; candidate C1 reduced this to 2 of 817 (0.24%).

## 2026-09-04 — canonical candidate C1

- Candidate commit: `caf33be9f5de2d8d7f4a291abd466cf808aaf801`.
- Focused candidate correctness: 107 tests, 0 failures, 0 errors, 5 skipped.
- Full Community distribution build passed all 129 reactor modules with tests skipped for packaging qualification.
- Plan-verified roadNet-PA query used `StatefulShortestPath(Into, Trail)` and returned two 772-hop paths for both variants.
- Matched PROFILE DB hits were 6,127,334. Baseline plan time was approximately 31.55 s and C1 approximately 5.59 s. Reported plan memory was 1,042,920,640 bytes for baseline and 1,035,462,144 bytes for C1.
- A randomized two-repetition roadNet-PA screen preserved identical result-length sets for all seven pairs. It remains preliminary, not statistically complete.

## 2026-09-04 — formal roadNet-PA paired timing

- Completed five metadata-bound baseline/C1 JVM-fork pairs with seeds
  `20260904` through `20260908`.
- All retained samples bind to source SHA, distribution JAR hash, full imported
  database manifest, dataset/query/config hashes, Java/Maven/PowerShell/storage
  environment, and protocol settings.
- One earlier baseline attempt produced an empty CSV. It remains preserved as
  `roadNet-PA-b0-fork2.csv`; the successful retry used `fork2-retry1` and was not
  silently substituted or overwritten.
- All result-length sets matched between baseline and C1.
- Deep-distance aggregate (250, 500, 772 hops): 4.373x geometric-mean speedup,
  95% paired-log Student-t CI [2.126x, 8.993x].
- Shallow/common-case aggregate (10, 25, 50, 100 hops): 0.941x, 95% CI
  [0.431x, 2.056x]. This is an uncertain 5.9% point regression and therefore
  does not pass the common-case regression gate.
- All-distance aggregate: 1.818x, 95% CI [0.858x, 3.850x].
- Canonical analysis: `state-index/results/2026-09-04/FORMAL_ROADNET_PA.md`.
- Raw immutable inputs and machine-readable analysis remain in the ignored
  shared D: artifact store.

This establishes a real-graph deep-path benefit and retains C1 for further
evaluation. It does not satisfy multi-topology benefit, common-case regression,
memory, broad correctness, or upstream-contribution gates.

## 2026-09-04 — higher-repetition shallow timing

- Added a reusable paired server runner with append-only D: outputs, independent
  JVM restarts, seeded within-pair variant ordering, metadata capture, and
  explicit baseline/candidate labels.
- Updated the analyzer to reduce each distance to its within-fork median before
  treating forks as independent paired units. Three analyzer regression tests pass.
- Preserved interrupted `roadNet-PA-shallow-v1`: B0 fork 1 completed; C1 fork 1
  was interrupted after startup and produced no CSV. No file was overwritten.
- Completed `roadNet-PA-shallow-v2`: 5 paired JVM forks, 10 measured repetitions
  per distance per variant, 2 full-manifest warmups, 400 validated measured
  queries, and seeds `20260930` through `20260934`.
- The first shallow-only analysis failed closed because the predefined deep
  aggregate was empty. The analyzer was corrected, regression-tested, and rerun
  over the unchanged raw inputs.
- Shallow 10–100-hop aggregate: 1.182x speedup, 95% CI [0.870x, 1.606x], paired
  log Cohen's dz 0.678. No distance has a point-estimate regression above 2%.
- Fork-to-fork aggregate speedups ranged from 0.849x to 1.520x, so the interval
  still permits a broad regression greater than 5%; the gate remains uncertain.
- Canonical report: `state-index/results/2026-09-04/SHALLOW_ROADNET_PA.md`.

## 2026-09-04 — canonical lifecycle tests

- Added five direct `FoundNodes` tests for canonical identity, lookup after
  frontier retirement, bidirectional sharing, duplicate-instance rejection,
  illegal buffer transitions, frontier release, and scoped-memory cleanup.
- The first two memory-test attempts failed because they assumed global tracked
  memory increased monotonically across frontier commits. The final assertion
  measures the actual boundary: old frontier plus next buffer before commit,
  then lower memory after the old frontier is retired while canonical lookup
  remains valid.
- Final `FoundNodesTest`: 5 tests, 0 failures/errors/skips, 1:02 Maven runtime.
- Existing `PGPathPropagatingBFSTest` in the preceding combined run: 72 tests,
  0 failures/errors, 5 skipped. The combined run failed only in the new memory
  assertion and is retained as negative test-design evidence.
- Module Spotless apply/check passed after normalizing Java/Scala line endings.

## 2026-09-04 — plan-verified tracked-memory profile

- Extended the HTTP harness to preserve raw `PROFILE` response JSONL before
  validation and to emit a compact per-query operator summary.
- Two parser attempts failed closed and are preserved: v2 expected the bare
  operator name and wrote no raw response; v3 exposed the actual `plan.root`
  wrapper and `@neo4j` operator suffix while preserving the first raw response.
- The corrected v4 capture accepted only nested operators whose observed name
  begins with `StatefulShortestPath`, and completed all seven pairs for B0/C1.
- Result lengths and operator DB hits match exactly at every distance.
- C1 operator tracked-memory deltas range from -2.91% to +1.46% on shallow
  pairs and from -0.84% to +0.34% on deep pairs. Every observed value is within
  the preferred 5% shallow and 10% benefited-deep gates.
- Canonical report: `state-index/results/2026-09-04/MEMORY_PROFILE_ROADNET_PA.md`.

This is one plan-profile pass on one graph. It supports the memory gate for the
observed workload, but does not replace JOL/HeapEstimator validation, occupancy
analysis, near-limit tests, or multi-topology memory evidence.

## 2026-09-04 — fixed transaction-memory boundary

- The initial dynamic-setting harness failed twice because the built Community
  distribution does not expose `dbms.setConfigValue`; both attempts are retained.
- Replaced it with fresh JVMs and isolated `NEO4J_CONF` directories per variant
  and limit. Raw responses are written before validation, the active setting is
  verified through `SHOW SETTINGS`, and only source-confirmed transaction quota
  errors are accepted as expected failures.
- A 1 MiB smoke limit was rejected because it prevents system-database startup;
  the failed run is retained. Both variants pass the shallow controls at 4 MiB.
- At roadNet-PA distance 250 and an identical 92 MiB limit, B0 returned two
  correct 250-hop rows while C1 failed with
  `Neo.TransientError.General.MemoryPoolOutOfMemoryError` at
  `db.memory.transaction.max`.
- The pass/fail split reproduced in three independent fresh-process pairs with
  byte-identical raw query records.
- C1 therefore fails the mandatory near-limit gate. This justifies evaluating a
  lower-overhead canonical representation; C1 cannot be the retained design.
- Canonical report: `state-index/results/2026-09-04/NEAR_LIMIT_ROADNET_PA.md`.

## 2026-09-04 — C2 qualification and C3 trigger

- C2 focused correctness completed 78 tests with zero failures/errors and five
  skips; Spotless apply/check passed.
- The initial C2 server distribution was provenance-invalid because its
  runtime-util JAR hash matched C1. Four affected run directories are retained
  and explicitly excluded; none contributes to the result.
- Rebuilt the actual Community distribution through the 129-module assembler.
  Build passed in 08:16; bytecode and JAR hash bind it to C2.
- Valid C2 fails the depth-250 query at 92 and 93 MiB and passes at 94 MiB.
  B0 passes at 92 MiB and C1 first passes at 93 MiB.
- Valid three-fork C1/C2 screening estimates C2 retention at 89.9% overall,
  93.3% shallow, and 85.6% deep. Intervals remain wide; this is screening.
- Plan-verified C2 tracked-memory deltas versus B0 range from -2.06% to +2.05%,
  but the aggregate profile does not override the fixed-limit failure.
- C2 is rejected as the final design. Both the memory weakness and screening
  performance threshold trigger the conditional C3 experiment.
- Canonical report: `state-index/results/2026-09-04/C2_ROADNET_PA.md`.

## 2026-09-04 — C3 adaptive-bucket stop gate

- Implemented only the predeclared simplest C3 form: node-major buckets with
  one or two inline sparse entries and one-way promotion to a dense state array.
- Added representation-neutral lifecycle coverage. The final focused run
  executed 118 tests with zero failures/errors and five existing skips;
  Spotless passed.
- Built the actual Community distribution through the 129-module assembler in
  06:05. The runtime-util JAR hash is
  `A2858D5591D0B3D9948C6BC9C389F2249392A5B496FBB2ED5AEB1EB65F70051D`.
- The first memory run recorded an abbreviated candidate SHA and is retained as
  exploratory evidence. The canonical `v2` protocol records full baseline and
  candidate SHAs.
- At the identical 92 MiB startup limit, B0 returned `[250,250]`; C3 capacity 2
  failed with 91 MiB tracked and the next 2 MiB allocation rejected.
- The mandatory memory gate therefore rejects C3. Capacity 1, timing,
  occupancy/crossover, allocation/GC, and a middle primitive map were not run
  because the predeclared fail-fast rule made them non-decision-relevant.
- B0 remains the retained design. No direct-state-index contribution branch or
  upstream PR is justified.
- Canonical report: `state-index/results/2026-09-04/C3_ROADNET_PA.md`.

## 2026-09-05 — Pareto continuation and multi-topology replication

- Preserved the strict-gate result and changed only its interpretation: C1 fails
  identical-limit compatibility, but that fact is not itself a production veto.
- Added deterministic manifests and identical indexed B0/C1 stores for
  roadNet-CA, web-Stanford, and undirected materialized as-Skitter. All bulky
  state remains on D: and all included PROFILE plans used
  `StatefulShortestPath(Into, Trail)` with equal results/DB hits.
- Fixed the paired analyzer to identify cases by source/target/distance rather
  than collapsing multiple pairs at one distance; 12 harness tests passed.
- Five paired JVM forks measured: roadNet-CA 2.340x overall and 5.289x d250+;
  web-Stanford 1.079x overall; as-Skitter 1.014x overall. Confidence intervals
  and raw hashes are in `MULTI_TOPOLOGY_RESULTS.md`.
- LDBC SF10 was not prepared and no naturally qualifying manifest existed;
  forcing a synthetic operator shape was rejected as non-decision-relevant.
- Extended fixed-limit probing. PA d100/d500/d772 were equal and d250 shifted
  92→93 MiB. CA shifted 102→104 at d250 and 1288→1296 at d500, while d100/d800
  were equal. Large setting values required a narrow `SHOW SETTINGS` display
  rounding tolerance; interrupted v1/v2 discovery runs remain preserved.
- JFR v1–v5 accidentally attached to Neo4j's Java launcher. Impossible
  near-zero allocation exposed the flaw; these runs are retained and excluded.
  v6 binds to the database JVM and records +5.5–7.0% total C1 allocation, 23 vs
  25 GCs, and 0.966 vs 0.934 seconds total pause.
- Refreshed upstream: `2026.07` remains at
  `f213380f812b820a1b312e2ea52cb3d8f1931ccc`; the history scan remains and no
  obvious open/recent merged overlapping implementation was found.
- Final Pareto result: B0 and C1 are non-dominated. C2/C3 remain non-selected
  comparators. C4 was not implemented.
- Final classification: `GO — APPROACH NEO4J MAINTAINERS WITH MEASURED TRADE-OFF`.
