# roadNet-PA StatefulShortestPath memory profile

## Verdict

`MEASURED`: all seven observed C1 tracked-memory values satisfy the preferred
memory limits. The largest shallow increase is 1.46%; the largest deep increase
is 0.34%. This is supporting evidence, not the final memory or near-limit gate.

## Protocol and evidence boundary

Both distributions executed the same seven-pair manifest after one untimed
warmup pass and a one-repetition timing prelude. Each subsequent query used
`PROFILE`; the harness persisted its complete HTTP response as JSONL, verified
two expected path lengths, and rejected any plan without a nested operator whose
observed name begins with `StatefulShortestPath`.

```text
upstream baseline: f213380f812b820a1b312e2ea52cb3d8f1931ccc
baseline timing variant: 1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
candidate C1: caf33be9f5de2d8d7f4a291abd466cf808aaf801
operator: StatefulShortestPath(Into, Trail)@neo4j
raw store: D:/dev/neo4j-research/artifacts/ppbfs/profiles/roadNet-PA-profile-v4
B0 raw JSONL SHA-256: C2B2BE987AF42354478082680CD85DC3F50458E29A4A0A65D4C8C4D3505BBDD0
C1 raw JSONL SHA-256: E861547DA605C3FEFD51CCFA7D6323EC3B9CF26D22C20B9C07E37380D2AD3342
B0 summary SHA-256: F2F119E001D60951B0298A5D9F6BC26AF9CCC825D798E88A63D21405399D1529
C1 summary SHA-256: 3E714AE3980DBC2B224755D313553B288F82B895FA0DF261A9AB3D3D0471FAF5
```

`Memory` below is the per-operator value emitted by Neo4j's profile tree. It is
not process RSS or cumulative allocation.

## Results

| Distance | B0 bytes | C1 bytes | Delta bytes | Delta | DB hits equal | Results equal |
| ---: | ---: | ---: | ---: | ---: | :---: | :---: |
| 10 | 26,252 | 25,488 | -764 | -2.91% | yes | yes |
| 25 | 94,604 | 95,636 | +1,032 | +1.09% | yes | yes |
| 50 | 846,756 | 859,144 | +12,388 | +1.46% | yes | yes |
| 100 | 13,513,340 | 13,552,772 | +39,432 | +0.29% | yes | yes |
| 250 | 96,059,424 | 96,390,272 | +330,848 | +0.34% | yes | yes |
| 500 | 557,576,820 | 552,907,692 | -4,669,128 | -0.84% | yes | yes |
| 772 | 1,042,867,760 | 1,035,492,848 | -7,374,912 | -0.71% | yes | yes |

## Gate interpretation

- Shallow preferred limit, at most 5% increase: passes on observed pairs.
- Benefited-deep preferred limit, at most 10% increase: passes on observed pairs.
- Exact result length, row count, and operator DB-hit equivalence: passes.
- Cross-topology memory: `NOT RUN`.
- Object-layout/JOL and structural-estimate validation: `NOT RUN`.
- Identical query-memory-limit boundary behavior: `NOT RUN`.
- Temporary migration/backfill peak: not applicable to always-canonical C1, but
  construction-time peak still requires dedicated structural analysis.

The memory gate remains incomplete until the unrun items are resolved.
