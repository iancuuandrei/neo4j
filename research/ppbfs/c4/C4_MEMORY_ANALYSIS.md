# C4-DCRI query-memory analysis

## Fixed-limit results

`MEASURED`: every probe used a fresh server, the same store/query/config, and Neo4j's transaction-memory limit. A pass/fail transition is allocator/high-water evidence, not exact resident-byte usage.

| Dataset / distance | B0 minimum observed pass | C1 prior minimum observed pass | C4 minimum observed pass | C4 - B0 |
| --- | ---: | ---: | ---: | ---: |
| roadNet-PA d100 | 14 MiB | 14 MiB | 14 MiB | 0 |
| roadNet-PA d250 | 92 MiB | 93 MiB | 94 MiB | +2 MiB / +2.17% |
| roadNet-PA d500 | 532 MiB | 532 MiB | 532 MiB | 0 |
| roadNet-PA d772 | 996 MiB | 996 MiB | 996 MiB | 0 |
| roadNet-CA d100 | 12 MiB | 12 MiB | 12 MiB | 0 |
| roadNet-CA d250 | 102 MiB | 102 MiB | 104 MiB | +2 MiB / +1.96% |
| roadNet-CA d500 | 1,288 MiB | 1,294 MiB | 1,294 MiB observed pass; 1,288 fail | +0 to +6 MiB interval |
| roadNet-CA d800 | 1,732 MiB | 1,732 MiB | <=1,730 MiB | at least -2 MiB |

The d250 minima are bounded by adjacent tested limits. The d500 CA minimum was not exhaustively searched between 1,288 and 1,294 MiB. C4 at d800 passed 1,730 MiB where B0 failed; both passed 1,732 MiB.

## Interpretation

`SOURCE-CONFIRMED`: C4 reuses retired dense buckets and the first retired outer map. It adds no second canonical dense bucket per reached node. Its extra retained structure is primarily coalesced outer-map capacity and temporary merge/growth high water.

`MEASURED`: C4 does not uniformly recover B0's boundary. It is B0-identical at five PA anchor points including d500/d772, worse by 2 MiB at both d250 replications, and better at CA d800.

`DERIVED`: a constant-overhead model is rejected. Boundary direction depends on frontier topology, coalesced-map resize timing, and allocator reservation granularity. The d250 shifts are small in percentage terms but real and reproducible across two road graphs.

`INFERRED`: the most plausible cause is a discrete high-water crossing during outer-map growth rather than duplicated dense buckets: canonical allocation count is zero, while JFR attributes substantial bytes to `HeapTrackingLongObjectHashMap` backing arrays on deep roads.

`POLICY / MAINTAINER DECISION`: whether a roughly 2 MiB d250 boundary increase is preferable to C1's simpler always-on structure is not settled by these measurements. C4 improves allocation and shallow behavior, but memory is not an unconditional win.

