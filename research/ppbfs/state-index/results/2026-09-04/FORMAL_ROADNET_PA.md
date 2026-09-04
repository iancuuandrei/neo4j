# Formal roadNet-PA paired timing

## Verdict

`MEASURED`: C1 materially improves deep-path cases on this graph, but the
shallow/common-case result is uncertain and its point estimate exceeds the 5%
regression allowance. The experiment therefore retains C1 for investigation but
does not pass the full candidate gate.

## Provenance

```text
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
baseline timing variant: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
candidate C1: caf33be9f5de2d8d7f4a291abd466cf808aaf801
dataset: SNAP roadNet-PA
dataset SHA-256: 450B8733635D887466A2B96B26411F6E62CAF7006F8A264F59CF9B5D75CDF549
query manifest: research/ppbfs/common/manifests/roadNet-PA-pairs.csv
paired seeds: 20260904, 20260905, 20260906, 20260907, 20260908
warmups: 1 complete manifest pass per JVM fork
measured repetitions: 1 per pair per JVM fork
analysis: paired log speedups with two-sided 95% Student-t intervals
raw store: D:/dev/neo4j-research/artifacts/ppbfs/runs/formal
```

Metadata sidecars bind every retained CSV to its distribution JAR hash, complete
37-file imported-database manifest, environment, configuration, seed, and command.
The analysis script validates those bindings and result-length equality before
including a pair.

## Results

Speedup is baseline elapsed time divided by C1 elapsed time.

| Distance | B0 median ms | C1 median ms | Geomean speedup | 95% CI |
| ---: | ---: | ---: | ---: | ---: |
| 10 | 16.065 | 13.582 | 0.739x | [0.303x, 1.802x] |
| 25 | 11.284 | 10.257 | 0.773x | [0.334x, 1.789x] |
| 50 | 27.701 | 18.616 | 1.176x | [0.824x, 1.678x] |
| 100 | 70.414 | 48.957 | 1.167x | [0.288x, 4.732x] |
| 250 | 1156.741 | 434.743 | 2.233x | [0.840x, 5.933x] |
| 500 | 9511.730 | 2067.130 | 4.481x | [1.685x, 11.918x] |
| 772 | 33316.613 | 3623.778 | 8.355x | [6.371x, 10.957x] |

| Aggregate | Geomean speedup | 95% CI |
| --- | ---: | ---: |
| All distances | 1.818x | [0.858x, 3.850x] |
| Shallow 10–100 | 0.941x | [0.431x, 2.056x] |
| Deep 250–772 | 4.373x | [2.126x, 8.993x] |

All paired result-length sets matched.

## Failure preservation

The first baseline fork-2 attempt produced an empty CSV and remains preserved as
`roadNet-PA-b0-fork2.csv`. Its successful retry is named `fork2-retry1`; it was
not overwritten. The analyzer excludes incomplete inputs by validation rather
than silently treating them as measurements.

## Limitations and next gate

- One measured repetition per independent JVM fork leaves shallow estimates noisy.
- Only roadNet-PA is represented; no multi-topology inference is allowed.
- The timing result does not establish memory-limit safety, allocation benefit,
  cancellation behavior, or full correctness.
- Next: a higher-repetition shallow/common-case protocol, then additional real
  graph families and the dedicated memory/correctness gates.
