# C4-DCRI final Pareto analysis

## Answer first

**C4 PARTIAL — BETTER RESOURCE PROFILE, BUT TRADE-OFF REQUIRES MAINTAINER CHOICE**

C4 validates the central architecture: it removes final-depth-linear lookup work after a bounded prefix, retains C1-class deep-road speed, avoids canonical bucket duplication, and is materially better than C1 on the valid shallow LiveJournal control. It does not meet the stronger claim that memory becomes uniformly B0-like: both road graphs regress by 2 MiB at d250, roadNet-CA d500 remains in C1's observed boundary interval, and deep-road allocation remains 3.8-5.7% over B0.

## Evidence layers

`SOURCE-CONFIRMED`: activation is one-way at frontier retirement; the first retiring map and later unique buckets transfer ownership; frozen history is never backfilled; active scheduling maps never alias retired storage; collisions require identical `NodeState` instances.

`MEASURED`: focused and complete runtime-util suites pass with zero failures/errors. Controlled chain probes become bounded at eight per post-activation miss. Fresh three-way timing gives deep speedups of 4.370x on roadNet-PA and 4.098x on roadNet-CA versus B0, while C4/C1 deep intervals include parity. The 10-fork valid LiveJournal run gives B0/C4 1.094x [0.902, 1.328] and C1/C4 1.189x [1.004, 1.408]. C4 allocates less than C1 in every qualified JFR recording.

The corrected exact-C4 10-fork Hetionet H3 extension establishes practical equivalence for all three pairs: B0/C4 1.003 `[0.973,1.033]` and C1/C4 0.991 `[0.967,1.016]`, with 90% intervals wholly inside the +/-5% band. The corrected gMark control is centered on parity at B0/C4 0.996 `[0.840,1.180]`; it remains too shallow and statistically unresolved.

`DERIVED`: C4 retains essentially all measured C1 deep benefit. On PA, C1/C4 deep is 1.099x [0.682, 1.772]; on CA it is 0.944x [0.718, 1.240]. Neither supports a material deep loss. Bounded frozen-prefix probing supplies the expected linear-in-lookups asymptote.

`INFERRED`: ownership transfer removes the dominant always-on bucket duplication, while coalesced outer-map growth explains the residual road allocation and boundary variability. The gMark depth-1/2 warning cannot be an activation cost because diagnostics show zero activations; fixed per-lookup research counters and run drift are plausible contributors, but the measured result remains disclosed.

`POLICY / MAINTAINER DECISION`: choosing C4 over C1 depends on whether maintainers prefer C4's improved common-case/resource profile enough to accept its additional lifecycle complexity, and whether the remaining small memory-boundary shifts are acceptable. The experiment does not authorize an upstream PR.

## Why the stronger verdicts are not justified

- `C4 WINS` would overstate the resource result: memory did not uniformly return to B0 and deep-road allocation is still above the preferred target.
- `C1 REMAINS PARETO-BEST` is contradicted by C4's C1-class deep speed, lower allocation, and significant LiveJournal advantage over C1.
- `C4 FAILS` is contradicted by its validated asymptotic change, replicated deep gains, and improved resource profile.

No C4 successor, sharding scheme, singleton representation, or flat pair table is justified by this experiment. The next action is maintainer discussion using the measured trade-off, not another candidate.
