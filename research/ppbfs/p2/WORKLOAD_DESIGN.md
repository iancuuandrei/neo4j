# P2 workload design (synthetic harness v1 + external plan)

Status: synthetic intents preregistered; roles frozen on `MEASURED` telemetry below (see `SPARSITY_REPORT.md`).
Harness: `community/cypher/runtime-util/.../ppbfs/P2SparsityHarness.scala` (research-only, 20 tests).
Manifest: `WORKLOAD_MANIFEST.csv` (latest run per workload; `grid-s63` superseded by `grid-s19`, excluded).
Oracles: per-run `oracle.txt` (row count, total entity count, sorted-multiset SHA-256) for candidate equivalence.

## Synthetic families (all `MEASURED` S/k in manifest)

| Workload | Graph | NFA (S `MEASURED`) | Intent → frozen role |
| --- | --- | --- | --- |
| `chain-s31-k1` | 40-line | rep-chain r14 (31) | strong positive → positive (S too small for headline) |
| `chain-s127-k1` | 140-line | rep-chain r62 (127) | strong positive → **strong positive** |
| `chain-s255-k1` | 280-line | rep-chain r126 (255) | strong positive → **strong positive** |
| `grid-s19` | 10×10 grid | rep-chain r8 (19) | branching positive → **positive** |
| `branch-occ09` | diamond 2×3 | B2U8 (12) | ~10% intermediate → **sparse intermediate (11.9%)** |
| `branch-occ21` | diamond 2×3 | B3U3 (8) | ~25% intermediate → **intermediate (23.2%)** |
| `branch-occ29` | diamond 2×2 | B4U2 (8) | crossover → **crossover (27.5%)** |
| `branch-occ43` | diamond 1×2 | B6U6 (14) | crossover → **crossover (25.0%, k=6)** |
| `branch-big-k8` | diamond 1×3 | B8U24 (34) | big intermediate → **crossover (15.3%, k=8)** |
| `branch-occ67` | diamond 2×2 | B4U0 (6) | dense negative → **dense (36.7%, k=4)** |
| `branch-dense89` | diamond 1×2 | B16U0 (18) | dense negative → **dense (47.2%, k=16)** |
| `tiny-s2/s4/s7` | 10-line | 2/4/7 | tiny negative controls → **negative controls** |
| `star-lookup-stress` | 64-star | fanout-32 (33) | lookup stress → **lookup stress (center g/i=64)** |
| `locality-clustered` | diamond 1×1 | clustered (18) | range-positive intent → **locality control (span 2)** |
| `locality-dispersed` | diamond 1×1 | dispersed (18) | range-negative intent → **locality control (span 5, runs 2)** |
| `bidir-chain-s31` | 15-line | rep-chain r14 (31) | backward role → **bidir role (rows=1, hash == unidir)** |
| `bidir-standard` | 20-line | `(s)((a)-->(b))*(t)` (5) | bidir control → **bidir dense control (occ 80%)** |
| `unidir-into-s31` | 15-line | rep-chain r14 (31) | into-target control → **control (hash == bidir)** |

Branch-NFA construction: `S = 2 + B + U` (one intermediate state per branch, shared start/final);
matching branches use rel type 1 (present), non-matching use type 2 (absent) — real PPBFS
semantics (unreachable NFA states), not collection manipulation. Occupancy targets are approximate
by design; `MEASURED` actuals govern.

Negative-result discipline: `bidir-chain-s31`/`unidir-into-s31` first ran with rows=0 (20-node line
exceeds the 14-rel NFA reach — design error, not a regression); fixed to a 15-node line, both now
return the identical path hash. The empty runs are retained in artifacts, excluded from analysis
by latest-wins selection.

## External families (Phase 3b, pending)

Prepared stores on `D:`: roadNet-PA/CA, web-Stanford, cit-Patents, LiveJournal, as-Skitter,
Hetionet, gMark-30k. Existing qualifying queries are all small-NFA (`+` over ≤4 rel types,
`S ≈ 4–8`, e.g. Hetionet H1–H5 at `S = 4`): they can only supply neutral/regression evidence,
never the sparse regime. Sparse-regime external coverage requires long-pattern queries
(e.g. 20+ hop fixed patterns on roadNet → `S ≈ 40+`); to be added as `*-longchain-v1` manifests
with `EXPLAIN`-verified `StatefulShortestPath` and recorded compiled `S`. P2 telemetry needs
per-FoundNodes auto runs for server execution (single-run design is harness-only) — implement
before any server telemetry run.
