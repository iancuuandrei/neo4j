# C2 roadNet-PA qualification

## Verdict

`MEASURED`: C2 is rejected as the final design and retained as a comparator.
It passes focused correctness tests, but worsens the depth-250 transaction-memory
boundary and loses material performance versus C1 in the valid screening run.
Both predeclared conditions therefore trigger C3.

## Provenance correction

The first extracted `c2` distribution was invalid: its runtime-util JAR hash
was identical to C1 because the initial Maven target compiled dependencies but
did not execute the Community distribution assembly. These immutable runs are
`EXCLUDED` from C2 evidence:

- `roadNet-PA-c2-near-limit-smoke-v1`
- `roadNet-PA-c2-boundary-93m-v1`
- `roadNet-PA-c1-vs-c2-screen-v1`
- `roadNet-PA-c2-profile-v1`

The invalid distribution carries an external `INVALID.md` marker. No result
from it is reused below.

The valid C2 distribution was built with:

```text
mvn -Dmaven.repo.local=D:\Caches\Maven\repository -pl packaging/standalone/standalone-community -am -DskipTests package
result: PASS, 129/129 reactor modules, 08:16
C2 commit: ddb621582297ed21a6c46c03889a1914a4115caa
C2 runtime-util JAR SHA-256: 38F5FEFF1E1AB1532654FDE5FFB1535CCA28928BBE1B4618C171234EBF49FB8C
C1 runtime-util JAR SHA-256: 6B95EA3B02CBB97719E0FCEFE915D4FC30A394ECBEB06A9742108AAA48AA6C47
JDK: Temurin 21.0.12.1+1 LTS
```

## Correctness

The focused C2 run completed 78 tests with zero failures or errors and five
skips. It includes direct canonical identity, node/state key separation,
bidirectional sharing, duplicate rejection, frontier retirement, scoped-memory
cleanup, and existing PPBFS behavior coverage. Spotless apply/check passed.

## Fixed transaction-memory boundary

The same roadNet-PA depth-250 query and fixed startup setting were run in fresh
processes. Each passing result contains two 250-hop rows.

| Variant | 92 MiB | 93 MiB | 94 MiB | Minimum observed pass |
| --- | --- | --- | --- | ---: |
| B0 | PASS | PASS | PASS | 92 MiB |
| C1 | FAIL | PASS | not needed | 93 MiB |
| C2 | FAIL | FAIL | PASS | 94 MiB |

C2's 93 MiB failure reports 91 MiB currently tracked and rejects the next
2 MiB allocation. This is a hard failure of the identical-limit gate.

```text
raw roots:
D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-c2-valid-near-limit-v2
D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-c2-valid-boundary-93m-v2
D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-c2-valid-boundary-94m-v2
B0 92 MiB JSONL SHA-256: 670689DB5A872CB5FCB2FF569576609A27CB51553C30985B62FA2AC8AFE453A2
C2 92 MiB JSONL SHA-256: C10CF8733352C02CE2C26707C925803191617EE7778AD59519B7FBA825E5440B
C2 93 MiB JSONL SHA-256: 2F115979AB6CDF6FF67E3F12A0C5BDD503771FFBE4D0FF92152EE4CA3D18E904
C2 94 MiB JSONL SHA-256: F70E8D46D38CAA7DECB1482296629C9CB90FBC8412BC031B346BDA303947D2E5
```

## C1 versus C2 screening timing

Protocol: three paired, independently restarted JVM forks; one complete-manifest
warmup; three measured repetitions per pair; seeded within-pair variant order;
within-fork medians and paired log speedups. All result-length sets matched.

| Aggregate | C1 / C2 geomean | 95% CI | C2 retention |
| --- | ---: | ---: | ---: |
| all distances | 0.899x | [0.480x, 1.684x] | 89.9% |
| shallow 10-100 | 0.933x | [0.454x, 1.915x] | 93.3% |
| deep 250-772 | 0.856x | [0.516x, 1.421x] | 85.6% |

The screen is noisy and is not a publication-quality performance claim. Its
point estimates nevertheless cross both provisional C3 triggers, and the
separate memory failure already suffices to trigger C3.

```text
raw root: D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-c1-vs-c2-valid-screen-v2
analysis JSON SHA-256: 2B3F108DC8CE9BB3825D4848720B571888B3E001BC46321B0A5870C489FC8A90
```

## Tracked operator memory

One plan-verified profile pass reports C2 deltas versus B0 between -2.06% and
+2.05% across the seven pairs. Versus C1, deltas range from -1.12% to +0.95%.
DB hits, rows, and result lengths match. This aggregate profile is supporting
evidence only; it does not override the fixed-limit failure.

```text
raw root: D:/dev/neo4j-research/artifacts/ppbfs/profiles/roadNet-PA-c2-valid-profile-v2
summary SHA-256: 86977D7902475492CC1C40C443CD1006108F1D5E860A150219E03789546C6B51
raw JSONL SHA-256: F91391269B51A2AD54303C182BEC3D105C08D12B098234EC0556073AF9238587
```

## Decision

C2 is not Pareto-best. C3 is authorized as the simplest adaptive node-major
`tiny sparse -> dense` experiment. A middle primitive map remains prohibited
unless measured occupancy/crossover data demonstrates a real middle regime.
