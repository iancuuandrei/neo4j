# P2 sparsity report (`MEASURED` on f213 baseline, synthetic harness v1)

Source: `D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-telemetry-v1/` (append-only; latest dir per
workload wins; `grid-s63` superseded). Method: research-only per-bucket counters in `FoundNodes`
(alloc/write/canonical by role) and `BFSExpander` (iteration slots vs active, frontier exact lookups);
flush per run; no per-operation I/O. 20/20 harness tests pass; 8 carry a naive-DFS cross-check
(`assertExpected`); all 20 carry a canonical result oracle for later candidate comparison.

## Q1–Q3: how sparse are level-owned buckets, and how are active ids laid out?

- Sparse regime is real and extreme at large `S`: `chain-s255-k1` — 127 buckets, mean occupancy
  0.79%, p95 0.78%, 32,128 null slots scanned to emit 257 active states (null fraction 99.2%).
  `chain-s127-k1`: 98.4% null. `star-lookup-stress` (S=33): 97.0% null, all 65 buckets at k=1.
  `grid-s19`: 88.1% null across 95 buckets.
- Occupancy sweep behaves monotonically: 11.9% → 23.2% → 27.5% → 36.7% → 47.2% (branch family),
  null fraction 88.1% → 76.8% → 72.5% → 63.3% → 52.8%.
- Steady-state wavefront buckets carry k=2 (chains, grid); source/branch buckets carry k=1–16 (max 16).
- Active ids are almost always one contiguous run (mean runs 1.00–1.07; the designed dispersed
  control hits 2.00 with span 5.0 at k=4 vs clustered span 2.0). Mean occupied 8-chunks ≈ 1.0–1.6
  (dense89: 2.0). Chunk/bitmap structures have no natural advantage: runs already ≈ 1.
- Range implication: steady-state span == k (chain {2j,2j+1}); only source buckets (span 31 at k=4)
  and dispersed layouts (span 13 at k=4) show span > k. R candidate starts with no demonstrated regime.

## Q4–Q6: memory/iteration/lookup split

- Allocation is `O(S)` per bucket by construction (`newEmptyArrayList(S)`): chain-s255 tracks
  127 × (SHALLOW + A(255)) ≈ 127 × ~1072 B ≈ 136 KB of bucket structure for 257 live refs.
- Iteration scans `S` per expanded bucket: 32,385 slots for 257 emitted (chain-s255).
- Frontier exact lookups are 100% hits in all 20 workloads (`frontierMisses = 0`); per-bucket g/i
  ranges 0.5–1 (chains, star leaves) through 2.5–6 (grid, branch) to 16 (dense89 depth-1) and
  64 (star center, k=1, single scan of 33 + 64 direct hits).
- Canonical lookups split by depth: shallow searches resolve entirely in buffer role
  (e.g. branch-dense89: 31 hits + 30 bucket-misses, all buffer; 0 history probes); chains/grid
  probe history deeply with ~0% bucket-hit rate (chain-s255: 15,875 level probes, 0 hits;
  grid-s19: 1,100 probes, 1 hit, 147 buffer hits from reconvergence).
- Writes: zero duplicates in all 20 workloads (collection-level idempotence is structural, not
  load-bearing). Insertions are 100% ascending-monotonic in forward/unidirectional runs and
  0% monotonic (strictly decreasing, still adjacent) in backward search — sorted-vector insertion
  is O(1) append forward, O(k)-shift backward at tiny k.

## Methodological confirmation

- The same graph node owns several level-owned buckets (chain nodes 7 and 11 appear twice with
  disjoint state pairs): global per-node aggregation (prior `rho ≈ 0.75` at S=4) merges levels and
  cannot serve as the P2 oracle. Per-bucket measurement stands.
- Bidirectional and unidirectional into-target runs return the identical result hash; backward role
  contributes its own buckets/iterations/lookups.

## Gate 1 verdict: PASS (continue)

Material causal opportunity on qualified regimes: ≥88% null-slot scans wherever `S ≥ 12` with
per-bucket k ≤ 4 (17/20 workloads), plus `O(S)` tracked allocation per bucket. Tiny-NFA controls
(S=2: 100%, S=4: 50%, S=7: 43%) behave as dense-favoring bounds. Proceed to cost model (Phase 4)
and fixed-candidate screening (V vs D, DA as causal control). R stays deferred (no clustered-span
regime observed); H stays a control (fixed overhead unjustified at k ≤ 4 — to be quantified).
