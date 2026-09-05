# LDBC FinBench qualification

**Classification: NON-QUALIFYING FOR DIRECT PPBFS EVIDENCE.**

`SOURCE-CONFIRMED`: the 13 official complex-read query sources were inspected at pinned
implementation commit `5a4f9d7b7bc5daf48370fd9a4640684f67712428`. Seven path-oriented candidates were selected before
candidate execution.

`MEASURED`: B0 `EXPLAIN` classified TCR1, TCR2, TCR5, and TCR8 as `VarLengthExpand(All)` and TCR3
as `ShortestPath`. TCR11 was excluded because APOC was unavailable; TCR12 was excluded because the
official source references an undefined variable `p`. No official query planned to
`StatefulShortestPath`.

Under the predeclared anti-manufacturing rule, SF1 was not downloaded/imported merely to invent a
PPBFS-shaped workload. Raw plan evidence is at
`D:/dev/neo4j-research/artifacts/ppbfs/runs/external-validation/finbench-plan-qualification-v1/`
and `plans.jsonl` has SHA-256
`C8A0771CAD25B5CC9B73FA64B98AB386152D49A3FE8BBBEA5FD675973C0EBD96`.
