# P2 cost model (v1: fitted screening constants + telemetry operation mix)

Status: draft for experiment; thresholds are hypotheses to be pinned end-to-end, not defaults.
Evidence labels inline. `S` = NFA states, `k` = active states, `R` = id span, `g` = exact lookups,
`i` = full iterations, `w` = first-writes per bucket.

## Measured memory constants (single-JVM probe, `MEASURED`)

`HeapEstimator` (compressed oops): `HeapTrackingArrayList` SHALLOW = 40 B;
`HeapTrackingIntObjectHashMap` SHALLOW = 48 B; `A(n) = align(16 + 4n)`:
A(1–2)=24, A(4)=32, A(8)=48, A(16)=80, A(18)=88, A(32)=144, A(64)=272,
A(128)=528, A(255–256)=1040, A(512)=2064. Artifact: `runs/p2-microbench-v1/memory-constants.txt`.

- `M_dense(S) = 40 + A(S)`: M(4)=72, M(8)=88, M(32)=184, M(128)=568, M(255)=1080, M(512)=2104 B.
- `M_vector(k) = H_V + A(cap(k))`, cap геометрически from 4: k≤4 → ≤64 B + H_V(~40) ≈ ≤104 B;
  k=16 → ≈120 B + H_V. Wins over dense for all `S ≥ 8` at `k ≤ 4` (`DERIVED`).
- `M_DA = M_dense(S) + active list` — strictly more than dense; causal control only.
- `M_hash ≥ 48 + 2 × A(16)` ≈ 300+ B minimum (16-cap fixed overhead, `DERIVED` from source) —
  worse than dense itself for `S ≤ 64` at any k. H rejected on memory alone for the tiny-k regime.

## Fitted CPU constants (in-harness medians, `MEASURED`, ±JIT noise at 3 ns scale)

- Dense scan `c_slot ≈ 0.45 ns/slot` (D iter S=512 ≈ 224–236 ns; S=128 ≈ 51–64 ns).
- Vector iteration `c_active ≈ 2 ns/active` (V iter k=2 ≈ 3.8 ns).
- Direct lookup `c_direct ≈ 3 ns`; vector linear hit ≈ 3 ns for k ≤ 8, ≈ 5 ns at k=16;
  vector miss ≈ `2.8 + 1.1 × k` ns (linear scan to end — `MEASURED` slope).
- Construction: D ≈ `40 + 1.0 × S` ns (zeroing/allocation); V-append ≈ `15 + 3 × k` ns.
  Prototype V sorted-insert is O(k²) scan (no append fast-path) — production uses
  `id > lastId → append`, O(1), justified by 100% monotonic forward inserts (`MEASURED`).
- Marginal insert (monotonic): D ≈ V ≈ 3 ns. Backward (decreasing) inserts shift ≤ k elements;
  at k ≤ 4 this is ≤ ~10 ns (`DERIVED`).
- Merge: D ≈ `0.6 × S` ns (tight null-check scan); V two-pointer `DERIVED` ≈ `2 × (k1+k2)` ns.
  Prototype V re-put merge (O(k²)) is pessimistic and not production-representative; irrelevant to
  P2-only anyway (B0 never merges — history is append-only, buffer promotion is a reference move).

## Per-bucket total (frontier role)

Telemetry (`MEASURED`): each bucket is iterated ≈ once (`i ≈ 1`: 127/127, 95/95, 65/65),
`g` = 0.5–16 exact lookups (all hits), `w = k` first-writes, zero duplicates.

```text
C_dense = (40 + 1.0·S) + 1·(0.45·S) + g·3.0 + k·3.0          [ns, CPU; memory 40+A(S)]
C_vector = (15 + 3·k) + 1·(2·k) + g·hitvec(k) + k·3.0        [ns, CPU; memory H_V+A(cap)]
hitvec(k) ≈ 3 (k ≤ 8), ≈ 5 (k = 16)
```

CPU break-even at `i = 1`: `0.45·S + 3g ≈ 2k + g·hitvec(k)` → dominated by iteration:
V wins iff `k/S ≲ 0.2` (`DERIVED`). Memory break-even is laxer (V wins to much higher k).
Lookup-heavy buckets (`g/i ≥ 8`) with `k ≥ 8` shift the crossover toward dense — the only
regime where fixed-V is at risk (observed: dense89 depth-1 buckets k=16/g=16; star center k=1/g=64
is safe — linear hit at k=1 ≈ direct).

## Hypotheses for end-to-end falsification

- H1 (fixed V): V beats D on all sparse workloads (k ≤ 4), ties tiny-NFA (S ≤ 8), loses dense89 k=16.
- H2 (adaptive): one-way compact→dense promotion at `k ≈ 8–16` recovers the dense loss with no
  sparse cost. Threshold pinned end-to-end, not here.
- H3 (DA control): DA ≈ V on CPU everywhere (isolates null-scan benefit), costs more memory.
- H4 (R deferred): span ≈ k observed ⇒ R ≈ V in memory/iteration with direct lookup; R earns an
  implementation only if V shows lookup-heavy regressions R would fix.
- H5 (H rejected): hash never competitive at k ≤ 4 (memory ≥ dense, iteration 2–8× V).
