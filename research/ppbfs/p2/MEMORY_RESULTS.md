# P2 memory results (`MEASURED`; four claims kept separate per contract)

## 1. Tracked structural memory (deterministic, per-run medians, bytes)

| Workload | B0 | V | B0/V | C1 | C1+P2 | C1/C1P2 | C4 | C4+P2 | C4/C4P2 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| chain2000-s255 | 2,728,330 | 1,104,000 | 2.47 | 2,628,608 | 1,004,544 | 2.62 | 2,639,872 | 1,015,808 | 2.60 |
| chain2000-s509 | 8,605,030 | 2,181,360 | 3.94 | 8,408,064 | 1,984,512 | 4.24 | 8,419,328 | 1,995,776 | 4.22 |
| star2000-s33 | 21,087,060 | 14,323,680 | 1.47 | 21,078,016¹ | 14,315,520¹ | 1.47 | 21,087,060 | 14,323,680 | 1.47 |
| grid30-s19 | 515,865 | 468,840 | 1.10 | 466,944¹ | 447,488¹ | 1.04 | 515,865 | 468,840 | 1.10 |
| dense/tiny | parity | parity | 1.00–1.01 | parity | parity | – | parity | parity | – |

¹ Screening-grade single-JVM medians (interaction matrix, `analyze_interaction.py`; KB×1024);
B0/V columns are 10-fork medians. Star C1/C4 rows equal B0/V rows to <0.1% (same lifecycle phase
measured). Pattern is uniform: P2 removes the `O(S)`-per-bucket structure everywhere; C1/C4
interaction preserves each design's lifecycle (C1 retires frontiers, C4 merges retired buckets)
while shrinking every bucket.

## 2. Lowest passing configured query-memory limit (`P2MemoryLimitProbe`, `MEASURED`)

Binary-searched `LocalMemoryTracker` limit for full query completion (configured-limit claim only):

| Workload | B0 limit (B) | V limit (B) | B0/V |
| --- | ---: | ---: | --- |
| chain2000-s255 | 273,408 | 110,592 | 2.47 |
| star2000-s33 | 1,074,176 | 736,256 | 1.46 |

Ratios reproduce the tracked-memory ratios exactly (same accounting). Practical reading: on sparse
regimes the query survives ≈2.5× tighter `dbms.memory.transaction` style budgets. This is the
operational form of the win (deep/large searches that today trip the limiter), not a
resident-memory claim.

## 3. Allocation behavior (`MEASURED` via JFR; single recordings — descriptive, no CI)

Thread-total allocated bytes (`jdk.ThreadAllocationStatistics`, last snapshot per thread;
extraction: `scripts/gc_alloc.py`):

| Recording (identical work) | B0 | V | Δ |
| --- | ---: | ---: | --- |
| unit chain2000-s255 ×150 searches | 1,231.5 MB | 998.9 MB | −18.9% |
| server H3 full manifest ×10 reps | 3,908.5 MB | 3,700.9 MB | −5.3% (diluted: engine/HTTP/pagecache dominate server allocation) |

Allocation-sample classes (B0 chain255): `Object[]` #1 (283 — includes per-bucket `Object[S]`
arrays), `HeapTrackingArrayList` (42 — one per bucket), then `NodeState`/`Lengths`. V replaces
each `Object[S]` with `Object[cap(k)]` and reports fewer allocation samples on the identical
server recording (3,132 vs 3,327). The unit-timing deltas beyond the Amdahl scan bound
(chain255 +17% vs ≈6% scan-only) are consistent with this allocation-rate relief.

## 4. GC behavior (single recordings — observed, not promoted to a claim)

| Recording | Pauses (B0 → V) | Total pause ms (B0 → V) | Max pause ms (B0 → V) |
| --- | --- | --- | --- |
| unit chain255 ×150 | 30 → 30 | 190.5 → 172.0 | 19.2 → 16.4 |
| server H3 full | 5 → 3 | 43.5 → 22.5 | 11.3 → 10.1 |

Directionally favors V; with one recording per variant no significance is claimed. No GC-time
win is part of the verdict — the latency effect is attributed to scan + allocation-rate relief.

## C1/C4 resource answer (for `P1_INTERACTION.md`)

C1's long-lived canonical store and C4's retired index keep one dense `Object[S]` per node today;
P2 shrinks each to `Object[cap(k)]` with zero lifecycle change (C1 `closeLevel` and C4
transfer/merge/close semantics preserved and covered by their own test suites). Measured
C1→C1+P2: 2.6× (S=255), 4.2× (S=507), 1.47× (star). C4→C4+P2: identical ratios. This is the
quantified "P2 recovers P1's allocation cost" result. Replacement-array accounting on
growth/merge/promotion paths is covered by exact-tracker unit tests (`StateBucketTest`:
growth releases exactly once; C4 transfer/close covered by `FoundNodesDeferredIndexTest`).
