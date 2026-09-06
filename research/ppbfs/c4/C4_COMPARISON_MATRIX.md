# C4-DCRI comparison matrix

| Dimension | B0 | C1 | C4-DCRI |
| --- | --- | --- | --- |
| lookup asymptotics | history-depth dependent; controlled chain `Theta(D^2)` probes | expected `O(1)` | expected `O(1)` in final depth after bounded prefix |
| shallow structure | reference | always-on canonical index | B0 layout until one-way activation |
| deep road latency | reference | large gain | statistically C1-class; 4.370x PA and 4.098x CA deep speedup vs B0 |
| canonical bucket duplication | none | one canonical plus scheduling bucket | none measured; ownership transfer |
| history growth | unbounded | removed | frozen at eight levels |
| memory boundaries | reference | small workload-dependent regressions | mixed: B0-equal at most PA anchors, +2 MiB at both d250 replications, better at CA d800 |
| allocation | reference | +5.5-8.7% on representative roads | below C1 everywhere measured; B0-like externally, +3.8-5.7% on deep roads |
| GC | reference | no established storm | no established storm |
| activation | none | none | deterministic once at non-empty retirement after `H=8` |
| implementation complexity | existing | simplest direct design | moderate ownership/lifecycle complexity |
| correctness | reference | qualified | 531 runtime-util tests: 0 failures/errors, 8 skips; focused 80: 0 failures/errors, 7 skips |
| maintainability | existing | simple | understandable but more state transitions and merge ownership |

## Pareto status

`MEASURED`: C4 retains deep speed and improves C1's shallow/high-fanout and allocation behavior. It does not dominate B0 because two d250 memory boundaries regress by 2 MiB. It does not strictly dominate C1 because C1 is simpler and some C4 common-case intervals remain unresolved.

`DERIVED`: B0, C1, and C4 remain Pareto-relevant under different priorities. C4 has the strongest measured balance, but the evidence supports a maintainer choice rather than declaring C1 obsolete.

