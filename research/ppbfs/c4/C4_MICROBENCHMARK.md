# C4-DCRI structural crossover microbenchmark

## Verdict and binding

`MEASURED`: the repository-consistent opt-in Scala harness passed one test with zero failures/errors and wrote 210 rows to `D:/dev/neo4j-research/artifacts/ppbfs/runs/c4/microbenchmark-v1/structural.csv`. SHA-256: `8205528EE27CE53DFEEB194B45F377BA6750265550D3E080F1CB9C6D42BBF861`.

```text
branch base: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
JDK:         Eclipse Temurin 21.0.12.1+1
samples:     7 medians
lookups:     100,000 per lookup sample
Q:           1,2,4,8,16,32,64
map sizes:   16,256,4096
history:     1,2,4,8,16,32
```

The first invocation was `FAIL` before compilation because PowerShell split a dotted Maven property; no output was created. The corrected argument-array invocation was `PASS` in 44.846 seconds.

## Representative measurements and selection

For `|Q|=4`, map size 256:

| Operation | H=1 | H=2 | H=4 | H=8 | H=16 | H=32 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| history miss, ns/lookup | 0.885 | 1.958 | 11.726 | 31.596 | 71.599 | 147.799 |
| oldest hit, ns/lookup | 1.099 | 1.327 | 2.196 | 3.981 | 7.528 | 14.540 |

At the same `|Q|` and map size, a direct hit measured 1.428 ns, transfer into an existing outer index 10.941 ns per bucket, and repeated-node merge 8.594 ns per bucket. The whole first-map ownership transfer is a reference reassignment and is intentionally not represented as entry-by-entry transfer.

`DERIVED`: by H=8, every miss saves roughly 30 ns relative to direct lookup, enough to amortize about one transferred node bucket per lookup in this synthetic structure. Actual PPBFS lookup/bucket ratios and topology still determine end-to-end benefit.

The formal C4 trigger is frozen at:

```text
activate once when a non-empty frontier retires and frozen history already contains at least 8 levels
```

This produces `H0=8`, never activates for final depth 1–3, uses no timer or online floating-point cost model, and bounds later frozen-history scans independently of final depth.

## Limitations

This is a microbenchmark inside the existing runtime-util test harness, not JMH. Empty-memory tracking isolates collection operations but omits transaction-pool accounting. The JVM can optimize repeated lookup loops, and early `|Q|=1` transfer samples showed warmup sensitivity. The result selects a simple threshold for falsification; it does not prove end-to-end optimality. Formal B0/C1/C4 runs and activation diagnostics remain authoritative.
