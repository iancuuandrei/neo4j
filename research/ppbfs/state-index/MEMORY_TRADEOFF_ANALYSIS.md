# C1 memory trade-off analysis

## Preserved strict-gate fact

`MEASURED`: B0 passes the roadNet-PA d250 query at 92 MiB while C1 fails; C1 first passes at 93 MiB. C1 therefore fails the predeclared strict identical-memory-limit compatibility gate. The three fresh-process reproductions and original report remain unchanged.

`POLICY / MAINTAINER DECISION`: that fact does not alone decide whether C1 is an unacceptable optimization.

## Fixed-limit curve

| Dataset | Distance | B0 minimum observed pass | C1 minimum observed pass | Absolute | Relative |
| --- | ---: | ---: | ---: | ---: | ---: |
| roadNet-PA | 100 | 14 | 14 | 0 MiB | 0% |
| roadNet-PA | 250 | 92 | 93 | +1 MiB | +1.09% |
| roadNet-PA | 500 | 532 | 532 | 0 MiB | 0% |
| roadNet-PA | 772 | 996 | 996 | 0 MiB | 0% |
| roadNet-CA | 100 | 12 | 12 | 0 MiB | 0% |
| roadNet-CA | 250 | 102 | 104 | +2 MiB | +1.96% |
| roadNet-CA | 500 | 1288 | 1296 | +8 MiB | +0.62% |
| roadNet-CA | 800 | 1732 | 1732 | 0 MiB | 0% |
| cit-Patents | 12 | 8 | 8 | 0 MiB | 0% |
| Hetionet H3 | 6 | 10 | 10 | 0 MiB | 0% |
| LiveJournal | 3 | 2 | 2 | 0 MiB | 0% |
| gMark | 2 | 2 | 2 | 0 MiB | 0% |

These are minimum **observed** points from targeted adjacent/coarse probes, not mathematical minima from exhaustive searches.

For LiveJournal and gMark, 2 MiB is also the lowest server-usable setting in this setup. Below that, the system database fails during startup when its first 2 MiB tracked reservation cannot fit. Those attempts are infrastructure exclusions, not query-level failures, and make no claim below 2 MiB.

## PROFILE high-water curve

`MEASURED`: C1/B0 operator-memory deltas are small on both road networks but larger on contrasting topologies: PA `[-2.91%,+1.46%]`, CA `[-0.10%,+2.18%]`, web-Stanford `[+5.80%,+9.94%]`, and as-Skitter mostly negative through d12 but `[+8.80%,+9.70%]` at d20/d30.

DB hits and returned rows match for every pair. The evidence does not support a constant cost or a monotonic depth-scaled blow-up. It supports workload-dependent additional canonical-map backing storage. Research-only Hetionet instrumentation measured `U`, `N`, and occupancy; high occupancy with shallow history remained near parity, so occupancy alone does not explain latency. See `PRODUCT_STATE_OCCUPANCY_ANALYSIS.md`.

## Why 92 becomes 93

The transaction pool reserves tracked memory in discrete chunks. At PA d250, C1 reports about 90 MiB currently tracked and rejects the next 2 MiB request at a 92 MiB configuration. PROFILE differs by only 330,848 bytes (+0.34%).

`INFERRED`: C1 slightly changes live tracked structure and crosses a reservation boundary. Thus “minimum passing setting shifted by one MiB” is correct; “C1 consumes exactly one extra MiB” is not. CA d250 (102→104) and d500 (1288→1296) demonstrate that the setting shift varies with the workload.

## Candidate implications

- B0 retains the best strict-boundary compatibility.
- C1 pays small road-network and up-to-about-10% observed contrasting-topology operator memory for direct lookup.
- C2 first passes PA d250 at 94 MiB and is not a memory improvement over C1.
- C3 failed the 92 MiB gate and did not establish a better frontier.

`INFERRED`: C1's cost is real and must be disclosed, but the observed curve is not a large/scaling memory blow-up. C4 was not implemented: structural memory remains a maintainer question, and another candidate would have displaced the more decision-relevant cross-topology and allocation work.
