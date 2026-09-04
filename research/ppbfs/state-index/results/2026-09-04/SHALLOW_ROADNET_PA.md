# Higher-repetition shallow roadNet-PA timing

## Verdict

`MEASURED`: C1 has no per-distance point-estimate regression above 2% and the
10–100-hop aggregate point estimate improves by 18.2%. However, five paired
forks still produce a 95% interval that permits a broad regression greater than
5%. The common-case gate remains `NOT PROVEN`, not failed or passed.

## Protocol

```text
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
baseline timing variant: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
candidate C1: caf33be9f5de2d8d7f4a291abd466cf808aaf801
dataset: SNAP roadNet-PA
dataset SHA-256: 450B8733635D887466A2B96B26411F6E62CAF7006F8A264F59CF9B5D75CDF549
manifest: research/ppbfs/common/manifests/roadNet-PA-shallow-pairs.csv
paired JVM forks: 5
warmups: 2 complete manifest passes per fork
measured repetitions: 10 per distance per variant per fork
measured queries: 400
paired seeds: 20260930, 20260931, 20260932, 20260933, 20260934
analysis unit: within-fork median for each distance
confidence method: two-sided 95% Student-t interval over paired log speedups
raw store: D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-shallow-v2
analysis JSON SHA-256: D5753A110F013E390950C6A74F3606DD411BC471CAB3A0656AFF139CAD087AEA
```

Every warmup and measured query required exactly two results and the expected
minimum path length. Metadata binds code, runtime JAR, dataset, manifest,
configuration, environment, database-file inventory, seed, and protocol.

## Results

Speedup is B0 divided by C1. Medians are across the five within-fork medians.

| Distance | B0 median ms | C1 median ms | Geomean speedup | 95% CI | Paired log dz |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 15.904 | 15.985 | 1.021x | [0.732x, 1.424x] | 0.08 |
| 25 | 15.104 | 14.400 | 0.997x | [0.740x, 1.343x] | -0.01 |
| 50 | 19.049 | 15.065 | 1.185x | [0.839x, 1.674x] | 0.61 |
| 100 | 145.592 | 72.690 | 1.621x | [1.107x, 2.375x] | 1.57 |
| 10–100 aggregate | — | — | 1.182x | [0.870x, 1.606x] | 0.68 |

Aggregate fork speedups were 1.158x, 1.520x, 1.037x, 0.849x, and 1.491x.

## Preserved failures

- `roadNet-PA-shallow-v1` was interrupted after a complete B0 fork and partial
  C1 startup. It remains immutable and excluded because it has no paired C1 CSV.
- The first analyzer attempt on v2 rejected an empty predefined deep aggregate.
  The analyzer was fixed with a regression test and rerun over unchanged inputs.

## Gate interpretation

- Preferred geometric-mean regression limit: point estimate passes.
- Repeatable individual regression above 5%: none established by distance.
- Statistical gate: raw data, pairing, forks, confidence interval, and
  reproducibility are present, but fork variance remains high.
- Conservative conclusion: additional forks or improved noise control are
  required before declaring the common-case gate passed.
