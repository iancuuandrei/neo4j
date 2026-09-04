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

The formal multi-fork run was paused when workspace consolidation superseded the
research goal. One completed baseline fork is retained only as partial evidence;
no upstream performance claim is made from it.
