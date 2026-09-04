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
