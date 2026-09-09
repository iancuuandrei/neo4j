# P2 JFR analysis (`MEASURED` profiles; `DERIVED` bounds; `INFERRED` clearly marked)

Recordings: `D:/dev/neo4j-research/artifacts/ppbfs/jfr/p2-b0-chain255.jfr`,
`p2-v-chain255.jfr`, `p2-b0-grid30.jfr`, `p2-b0-diamond.jfr` (unit, ×15 repeats), and
`runs/p2-external-v1/h3-jfr*/b0.jfr + v.jfr` (server, H3). Analysis: `scripts/analyze_jfr.py`
(leaf top-frames + inclusive class shares + allocation classes) and `scripts/gc_alloc.py`
(GC pauses + thread-total allocated bytes). JDK 21, `settings=profile`.

## Unit profiles (B0)

- **chain2000-s255** (strong positive; 164 samples B0 / 161 V): inclusive BFSExpander 88–94%,
  ProductGraphTraversalCursor 78–86%, FoundNodes 5.5–6.8%. B0 `HeapTrackingArrayList` 6.1%
  (leaf `Itr.next`/`elementData`/`get`/`checkIndex` ≈ 4.3%); V shows `StateBucket` 0.0%
  inclusive — the tiny bucket methods are fully inlined into `getFromLevel`/`expand`
  (consistent with zero call-layer overhead; the server H3 pair still records outline
  `StateBucket.get` frames where inlining differs under server JIT load). Allocations on B0
  led by `Object[]` (283), `HeapTrackingArrayList` (42), `NodeState` (32), `Lengths` (30):
  buckets are top-allocated short-lived structure. Amdahl bound for scan removal (`DERIVED`
  from telemetry counts × fitted `c_slot`): ≈6% of harness elapsed — the measured +17%
  therefore rides substantially on allocation-rate relief (thread-total allocated bytes on
  identical work: B0 1,231.5 MB → V 998.9 MB, −18.9%; tracked −2.47×), not scans alone.
  GC pauses on the same pair: 30/190.5 ms → 30/172.0 ms (single recordings, descriptive only).
- **grid30-s19** (neutral; 187 samples): leaf `LongObjectHashMap.probe` 9.6% (canonical/history
  map probes), `Objects.checkIndex` 7.0% (dense bucket + list bounds checks),
  `HeapTrackingArrayList.elementData` 2.7%. V removes the per-slot checkIndex on iteration and
  shrinks (not removes) probe cost — consistent with a small positive/neutral.
- **diamond-dense-s18** (dense control): profiled; bucket code thirds with lookup code; no
  pathology in either representation (see recording; neutral result stands).

## Server profiles (H3, B0 vs V, full 23-pair manifest, ~543 samples each)

- Leaf shapes match: signpost maintenance dominates both
  (B0 `upsertSourceSignpost` 13.6%+`hasTargetSignpost` 12.1%; V 12.3%+7.7%), map probe 6–8%,
  store/pagecache the remainder. Buckets are a single-digit slice of H3 CPU in both variants.
- Exact bucket frames: B0 `FoundNodes.getFromLevel` leaf 6.1%/inclusive 11.2% (includes array
  get); V `getFromLevel` 1.8%/9.6% + `StateBucket.get` 0.9%/3.1% + `State.id` 2.4%/2.4%.
  V's lookup subtree (9.6%) is SMALLER than B0's (11.2%) — bucket code does not explain any
  H3 delta in either direction, corroborating the order-confound diagnosis (see
  `END_TO_END_RESULTS.md`): the v2 “regression” was cold-cache order imbalance, not buckets.
- `BFSExpander.expand` leaf 0.6%→1.7% (B0→V, +6 samples of 543): directionally consistent with
  one extra call layer (`appendActiveStatesTo`), quantitatively negligible.

## Amdahl reading for the verdict

Bucket scan+lookup code is ~5–12% of PPBFS CPU in the measured workloads (harness-denominator
caveat: `InMemoryGraph` O(E) scans inflate the unit denominator, so production low-degree shares
are larger). The observed wins (+17% chain255) exceed the scan-only bound via allocation relief;
the memory-limit wins (2.47×) are the operationally larger effect. No profile shows a V
pathology; no profile justifies adaptive promotion (no k-dependent blowup observed anywhere).
