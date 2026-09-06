# C4-DCRI complete benchmark report

## Variants and protocol

`MEASURED`: the complete synchronized matrix used B0 `1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381`, C1 `caf33be9f5de2d8d7f4a291abd466cf808aaf801`, and C4 `3a8d1f59a35770c5c1cfb380aa0efb2bff7684e2`, all derived from upstream `f213380f812b820a1b312e2ea52cb3d8f1931ccc`. Every formal cohort used fresh JVMs, seeded three-way ordering, identical stores/config/query manifests, one complete warmup, per-fork medians, paired log ratios, and two-sided 95% Student-t intervals. All result-length sets matched.

The complete C4 matrix contained an always-incremented research lookup counter even when diagnostics were disabled. The depth-1/2 gMark control detected its cost. Commit `0f8678e159b0837b448093be8607fcc6ebeaddf1` gates that counter behind the diagnostic hook; the full runtime-util suite and targeted synchronized gMark/controlled/H3 reruns qualify the correction. No algorithm, activation rule, collection, or ownership behavior changed.

Speedup is left-hand variant elapsed time divided by right-hand variant elapsed time.

| Workload | B0 / C1 | B0 / C4 | C1 / C4 | C4 classification |
| --- | ---: | ---: | ---: | --- |
| roadNet-PA all | 1.539 [0.980, 2.417] | 1.848 [1.426, 2.396] | 1.201 [0.653, 2.209] | positive vs B0 |
| roadNet-CA all | 1.830 [1.549, 2.161] | 1.801 [1.428, 2.273] | 0.984 [0.748, 1.296] | positive vs B0 |
| web-Stanford | 1.153 [0.967, 1.375] | 1.196 [0.994, 1.440] | 1.038 [0.959, 1.123] | practically non-inferior; positive point estimate |
| as-Skitter | 1.032 [0.934, 1.141] | 1.059 [0.978, 1.146] | 1.026 [0.913, 1.153] | practically non-inferior vs B0 |
| Hetionet H1 | 1.011 [0.897, 1.140] | 1.045 [0.981, 1.112] | 1.033 [0.966, 1.105] | practically non-inferior |
| Hetionet H2 | 1.014 [0.951, 1.080] | 0.972 [0.843, 1.121] | 0.959 [0.845, 1.088] | inconclusive |
| Hetionet H3 screen | 0.971 [0.799, 1.181] | 0.968 [0.814, 1.150] | 0.997 [0.757, 1.312] | superseded by expanded exact-C4 rerun |
| Hetionet H4 | 0.793 [0.239, 2.635] | 0.945 [0.383, 2.333] | 1.192 [0.514, 2.767] | inconclusive |
| Hetionet H5 | 0.917 [0.769, 1.093] | 0.979 [0.930, 1.031] | 1.069 [0.903, 1.264] | inconclusive; retained as supporting evidence |
| cit-Patents d1-12 | 1.046 [0.574, 1.907] | 1.289 [0.989, 1.678] | 1.232 [0.834, 1.818] | practically non-inferior vs B0 |
| LiveJournal valid `SHORTEST 2`, 10 forks | 0.920 [0.808, 1.048] | 1.094 [0.902, 1.328] | 1.189 [1.004, 1.408] | C4 positive vs C1; B0 comparison inconclusive |

## Deep-road retention

| Aggregate | B0 / C1 | B0 / C4 | C1 / C4 | raw C1-speed retention | log-speed retention |
| --- | ---: | ---: | ---: | ---: | ---: |
| roadNet-PA d250+ | 3.976 [2.581, 6.123] | 4.370 [3.838, 4.976] | 1.099 [0.682, 1.772] | 109.9% | 106.8% |
| roadNet-CA d250+ | 4.342 [3.631, 5.193] | 4.098 [3.231, 5.197] | 0.944 [0.718, 1.240] | 94.4% | 96.0% |

`DERIVED`: C4 clears the predeclared 85-90% deep-retention target on both independent road graphs. Its C1 comparisons are statistically consistent with parity.

## Controlled complexity

The fresh depth-4,096 run records 8,382,465 B0 probes, zero C1 historical probes, and 32,724 C4 frozen-prefix probes for the same 4,097 lookup attempts. C4 averages 7.987 probes/lookup and freezes exactly eight levels. This is the measured mathematical-vs-observed result.

The required 10-fork exact-C4 Hetionet H3 extension established equivalence within +/-5% for every comparison: B0/C1 1.012 `[0.981,1.044]`, B0/C4 1.003 `[0.973,1.033]`, and C1/C4 0.991 `[0.967,1.016]`; corresponding 90% intervals are wholly inside `[0.95,1.05]`.

## gMark falsification and correction

The original frozen gMark instance remains only depth 1-2 and cannot answer the intended deep-RPQ question. Ten combined forks of the first prototype measured B0/C4 0.811 [0.688, 0.956], exposing disabled-diagnostics overhead; activation diagnostics recorded zero activations. After gating the research counter, a new synchronized five-fork run measured B0/C4 0.996 [0.840, 1.180] and C1/C4 1.027 [0.898, 1.174]. The corrected control is inconclusive but centered on parity. The adverse original run remains append-only provenance.

## Operator and workload qualification

`MEASURED`: a separate PROFILE pass on roadNet-PA d10/d250/d772 selected `StatefulShortestPath(Into, Trail)`, returned identical result lengths, and recorded operator memory of 25,980 B, 96,265,872 B, and 1,035,036,160 B. Headline latency uses unprofiled runs only.

`MEASURED`: refreshed FinBench TCR1/TCR2/TCR5/TCR8 plans use variable-length expansion and TCR3 uses `ShortestPath`; TCR11/TCR12 remain excluded for their existing source/dependency reasons. FinBench is again `NON-QUALIFYING FOR DIRECT PPBFS EVIDENCE`; no timing was manufactured.

## Artifact authority

Raw append-only timing, analyses, profiles, memory probes, activation JSONL, JFR, and failure provenance are under `D:/dev/neo4j-research/artifacts/ppbfs/runs/c4/` and `.../runs/c4v2/`. Failed setup runs and the pre-correction gMark result are retained and excluded only for the stated protocol or instrumentation reason.

Key SHA-256 bindings:

```text
81AFD39EA44206B215E846457631FFC082DD703ECFB555C12EBE441D6D27A952  roadNet-PA B0/C4 analysis
E2FA3A2F566E1969D599B12126292CD31D6F69F19B9AD96FD832ED41F2662809  roadNet-CA B0/C4 analysis
40100858C99FAC1C5B916E7B3C16378A213F8AEBA3299A8752C55204309CCE74  LiveJournal B0/C4 analysis
1908C4C378834CD180DA63B6D504C3462CCD9B201AC71E86701BE5FA34392EE0  corrected gMark B0/C4 analysis
71C2CBE14436AD42FD141C26B25ACAB5BEF4C8F9971279B20AF6318C3EF430F6  corrected H3 B0/C4 analysis
AD5E8101A25E7AD54030F500D1C05053E46BCECA827706DBF1473723A2733104  roadNet-PA C4 JFR
2F933F72FB6CE4A921D7418EAB5175AFA620DF93FCD5DD9874F7F24652A3FD4A  roadNet-CA C4 JFR
C6830F7A1982152F9175A5A5BC2D8B8B14A5BDE3A190E237981828EF7BB3BAB6  Hetionet H3 C4 JFR
4E144F16D0ED339FB8845618426DA062ACA064F308D5C02E46B2620A49794EB6  roadNet-PA activation JSONL
2CA1F6ACECD058D9026E0100D3549AB21F62E8863EBDFB690D6E6E3A067F1A5A  corrected C4 controlled metrics
```
