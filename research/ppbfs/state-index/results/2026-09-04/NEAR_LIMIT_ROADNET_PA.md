# roadNet-PA fixed transaction-memory boundary

## Verdict

`MEASURED`: C1 fails the mandatory near-limit gate. At the same explicit
92 MiB transaction-memory limit, B0 succeeds and C1 fails. The result reproduced
in three independent fresh-process pairs. C1 must not be retained unchanged.

## Protocol

Each observation starts a new Community server process with an isolated copied
configuration directory selected through `NEO4J_CONF`. The harness verifies
`db.memory.transaction.max` through `SHOW SETTINGS`, writes the complete HTTP
response before evaluating it, checks successful result lengths, and accepts a
failure only when its code and message identify the configured transaction
quota.

```text
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
baseline timing variant: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
candidate C1: caf33be9f5de2d8d7f4a291abd466cf808aaf801
dataset: SNAP roadNet-PA
source: 457532
target: 78764
distance: 250
limit: 92.00 MiB (explicit startup value)
raw root: D:/dev/neo4j-research/artifacts/ppbfs/runs
```

The first two dynamic-setting attempts, `roadNet-PA-near-limit-v1` and `v2`,
failed before query execution because `dbms.setConfigValue` is unavailable in
the built Community distribution. `roadNet-PA-near-limit-smoke-v3` showed that a
1 MiB startup limit is invalid evidence because the system database itself
requires a 2 MiB reservation. These negative runs are preserved rather than
silently replaced.

## Reproduced result

| Run | B0 | C1 | Gate |
| --- | --- | --- | --- |
| `roadNet-PA-near-limit-v7` | PASS, `[250,250]` | FAIL, memory quota | FAIL |
| `roadNet-PA-near-limit-repeat-v8` | PASS, `[250,250]` | FAIL, memory quota | FAIL |
| `roadNet-PA-near-limit-repeat-v9` | PASS, `[250,250]` | FAIL, memory quota | FAIL |

All three B0 JSONL files have SHA-256
`670689DB5A872CB5FCB2FF569576609A27CB51553C30985B62FA2AC8AFE453A2`.
All three C1 JSONL files have SHA-256
`DF116123A7F950085F54832025EEE6F335584AF964B206977F80BB3DAF289212`.

The C1 response is:

```text
Neo.TransientError.General.MemoryPoolOutOfMemoryError
allocation: 2.0 MiB
limit: 92.0 MiB
currently used: 90.0 MiB
setting: db.memory.transaction.max
```

## Interpretation

`DERIVED`: the plan profile's +0.34% tracked-memory delta at this pair looked
small in percentage terms, but Neo4j reserves tracked memory in chunks. The
additional canonical structure moves C1 across a discrete 2 MiB allocation
boundary. Percentage-only profiling therefore understated operational risk.

`SOURCE-CONFIRMED`: `db.memory.transaction.max` is a dynamic per-transaction
limit and Neo4j's tracked collections reserve against it. The error is the
expected memory-pool status for a reservation crossing that setting.

`INFERRED`: a canonical representation with less structural overhead may avoid
this boundary and remains justified for isolated evaluation. The current result
does not prove that such a candidate will pass.
