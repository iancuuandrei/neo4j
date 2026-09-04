# State-index screening evidence — 2026-09-04

Status: the two checked-in CSVs remain preliminary screening evidence. The
completed metadata-bound five-fork analysis is summarized in
[`FORMAL_ROADNET_PA.md`](FORMAL_ROADNET_PA.md); bulky raw runs remain external.

```text
baseline upstream SHA: f213380f812b820a1b312e2ea52cb3d8f1931ccc
instrumented/timing ancestor: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
candidate C1 SHA: caf33be9f5de2d8d7f4a291abd466cf808aaf801
dataset: SNAP roadNet-PA
dataset SHA-256: 450B8733635D887466A2B96B26411F6E62CAF7006F8A264F59CF9B5D75CDF549
query manifest: ../../../common/manifests/roadNet-PA-pairs.csv
warmups: 1 per pair
measured repetitions: 2 per pair
seed: 20260904
```

All paired result-length sets matched. These CSVs justify retaining the research
candidate, but are not sufficient for an upstream performance claim. Complete
JFR, controlled-run, plan, server-log, and imported-store artifacts remain in the
shared ignored artifact store.

Additional canonical reports:

- `MEMORY_PROFILE_ROADNET_PA.md` records plan-verified tracked-memory evidence.
- `NEAR_LIMIT_ROADNET_PA.md` records the fixed-limit allocator-boundary regression.
- `C2_ROADNET_PA.md` records C2 qualification, invalid-run exclusion, and the
  evidence-gated decision to trigger C3.
- `C3_ROADNET_PA.md` records C3 correctness, exact build provenance, and its
  rejection at the first 92 MiB memory gate.
