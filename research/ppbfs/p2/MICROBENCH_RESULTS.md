# P2 microbench screening results (`MEASURED`, single-JVM medians, non-JMH)

Artifact: `D:/dev/neo4j-research/artifacts/ppbfs/runs/p2-microbench-v1/screening-<stamp>.csv`
(468 cells: S ∈ {4,8,32,128,512} × k ≤ S × patterns {low,high,dispersed} × ops).
Harness: `P2CollectionMicrobench.scala` (research-only). Analysis: `scripts/analyze_microbench.py`.
JDK recorded per file (`memory-constants.txt`).

## Gate 2 verdict: PASS for V; H rejected; DA is a control; R deferred

- **Iteration** (the dense tax): V/D = 0.02 (S=512,k=2: 3.8 vs 231 ns), 0.07 (S=128,k=2),
  0.35 (S=32,k=2), 0.68 (S=8,k=2); parity only at full occupancy (S=4,k=4: 1.05).
  DA == V (same active list). H is 2–8× worse than V at every (S,k) — rejected for the frontier role.
- **Hit lookup**: all candidates ≈ 3 ns for k ≤ 8 (within JIT noise); V ≈ 5 ns at k=16.
  Production relevance is high: telemetry shows frontier exact lookups are 100% hits.
- **Miss lookup**: V scales ≈ `2.8 + 1.1·k` ns (linear to end); D/DA/H flat ≈ 3 ns.
  Only material for canonical bucket-misses (chains: 130 × ~3 ns delta — negligible vs scans).
- **Construction**: D pays O(S) (S=512: ~150–480 ns); V-append O(k) (~13–65 ns). V/D ≥ 1 only at S ≤ 8.
- **Late insert** (marginal, monotonic): D ≈ V ≈ 3 ns; absolute deltas ≤ tens of ns — never decisive.
- **Merge**: prototype V re-put is O(k²) and loses to D's tight O(S) scan for k ≥ 8 at small S
  (S=128,k=64: 9.75×). Production V merge must be two-pointer O(k1+k2) (`DERIVED` ≈ parity or better
  wherever S > 3k). Irrelevant to P2-only (B0 never merges); matters only for C4 interaction.
- **Prototype artifacts** (not production claims): S=4,k=1 V-iteration 2.5× D is Scala-closure
  overhead in `foreachActive` (production uses an indexed loop); V construct at k=64 is O(k²)
  sorted-insert without the append fast-path (production appends on `id > lastId`, justified by
  100% monotonic forward inserts). Both are re-verified end-to-end on the real implementation.

## Pattern sensitivity

V/D hit ≈ 1.0 across low/high/dispersed id layouts for k ≤ 32; iteration V/D is layout-independent
(scans k actives). No layout favors R over V in the observed span≈k regime.
