# C3 adaptive-bucket qualification

## Verdict

`MEASURED`: C3 is rejected at the mandatory first memory gate. The simplest
node-major adaptive repository with two inline sparse entries per node fails
the same roadNet-PA depth-250 query at 92 MiB that B0 passes. Per the
predeclared C3 rules, no timing sweep, occupancy sweep, capacity-1 run, or
middle primitive-map representation is justified.

## Candidate and build provenance

C3 maps each data-node id to a `StateBucket`. A bucket keeps up to two
`(stateId, NodeState)` pairs in fields and promotes once to a heap-tracked dense
state-id array on its third distinct state. The implementation preserves one
canonical object per product-state key and leaves frontier storage unchanged.

```text
baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
C3 commit: bb48edd50d1991b3c7fa9f255b7122d4ffd0146a
sparse capacity: 2 (default)
build command: mvn -Dmaven.repo.local=D:\Caches\Maven\repository -pl packaging/standalone/standalone-community -am -DskipTests package
build result: PASS, 129/129 reactor modules, 06:05
JDK: Temurin 21.0.12.1+1 LTS
runtime-util JAR SHA-256: A2858D5591D0B3D9948C6BC9C389F2249392A5B496FBB2ED5AEB1EB65F70051D
Windows distribution SHA-256: 86F71F89D857B7D1FAB53F1C22C7B4EB5FA2BABA9BDCC9A2667FFD4F5D0DEC76
```

The packaged runtime JAR was inspected and contains both `FoundNodes.class`
and `StateBucket.class`. The isolated distribution contains the same
194,180,693-byte roadNet-PA store as the established comparators.

## Correctness gate

The final focused run executed 118 tests: 113 passed, zero failed or errored,
and five existing tests were skipped. Coverage includes sparse-to-dense
promotion for capacities 1 and 2, canonical identity, duplicate rejection,
node/state isolation, shared bidirectional ownership, isolated frontier
retirement, exact memory release after early close and interruption, and the
generated differential suite. Module Spotless apply/check passed.

## Fixed 92 MiB gate

The canonical `v2` run used fresh Neo4j processes, isolated configuration
directories, the same source/target pair, and the same explicit startup
setting for both variants.

| Variant | Result | Returned lengths | Failure |
| --- | --- | --- | --- |
| B0 | PASS | `[250, 250]` | none |
| C3 capacity 2 | FAIL | `[]` | transaction memory quota |

C3 reported 91 MiB currently tracked and rejected the next 2 MiB allocation
against `db.memory.transaction.max=92m`. The runner intentionally exited
non-zero because the candidate violated the required PASS expectation.

```text
canonical raw root: D:/dev/neo4j-research/artifacts/ppbfs/runs/roadNet-PA-c3-near-limit-v2
protocol SHA-256: F9530D3FB21E6E4A88821F3463BCE7449D497478C2EDAD399E371A157BBFD4FD
B0 JSONL SHA-256: 670689DB5A872CB5FCB2FF569576609A27CB51553C30985B62FA2AC8AFE453A2
C3 JSONL SHA-256: C10CF8733352C02CE2C26707C925803191617EE7778AD59519B7FBA825E5440B
```

The preceding `roadNet-PA-c3-near-limit-v1` run produced the same outcome but
stored an abbreviated candidate SHA. It is retained as exploratory evidence;
`v2` is canonical because its protocol records the full commit SHA.

## Stop decision

The memory gate precedes performance and crossover work. Capacity 1 promotes
strictly earlier than capacity 2 and therefore cannot repair this
low-occupancy boundary failure. A middle map would add complexity without
evidence that a middle-occupancy regime caused the failure. Consequently:

- C3 performance and occupancy sweeps: `NOT RUN — stopped by memory gate`;
- C3 capacity-1 server run: `NOT RUN — dominated for this failure mode`;
- middle primitive-map representation: `NOT IMPLEMENTED`;
- direct product-state repository recommendation: `NONE`;
- retained production baseline: B0.

This result rejects the tested direct-repository family under the current
evidence gates. It does not claim that every possible representation is
impossible, and it does not authorize an upstream contribution.
