# External-validation dataset provenance

## Authority and local identity

All bulky source archives, prepared CSV, database stores, builds, and raw runs remain under
`D:/dev/neo4j-research`; none are committed. Checked-in manifests and converters are sufficient
to reproduce the selected workloads.

| Dataset | Authority / pinned source | Local content SHA-256 | Role |
| --- | --- | --- | --- |
| LDBC FinBench | `ldbc/ldbc_finbench_transaction_impls` at `5a4f9d7b7bc5daf48370fd9a4640684f67712428` | official query sources hashed in the plan-qualification JSONL | standardized-workload qualification |
| Hetionet v1 | `hetio/hetionet` at `8a6cc0c5604d0e2908786e4de38af67b8e46ee4a` | `A342AB57E9073E6C02BB5E109D1F16917E6F933BE4E5C77EBBAEBFA26B984C19` | heterogeneous semantic graph |
| gMark test, 30k request | original `gbagan/gmark` at `77be5b620375f2fabf1b14204d1d1f899d8f8425`, seed `20260905` | `A1889F7A1BC804868D392A21FD0D02E702A6E46C7A06A3CACD10C481B8D85F38` | generated RPQ control |
| cit-Patents | Stanford SNAP `cit-Patents.txt.gz` | `9D05955AA997FBC84953C9EB7786675AF80336A356404E75DC18AAA90D3553B0` | directed DAG-like citation graph |
| LiveJournal | Stanford SNAP `soc-LiveJournal1.txt.gz` | `D7BCD5A87B88C896C35FDB9611E804C3F4033C39B58C4C9EA3BA53C680D516D8` | shallow high-fanout social control |

The Hetionet conversion preserves all native node labels and 24 relationship types. The gMark
conversion preserves predicate identifiers as `P<n>` relationship types. SNAP imports preserve
direction; no reverse relationships were synthesized for cit-Patents or LiveJournal.

`SOURCE-CONFIRMED`: original gMark generated 27,038 materialized nodes, 36,088 relationships, and
four predicate types for the requested 30k instance. Its sampled reachable diameter was only 3;
therefore it cannot support the predeclared deep sweep and is reported as a generator/topology
limitation, not silently replaced by favorable cases.

## Artifact policy

Large and rebuildable inputs remain on D:. Checked-in URLs, pinned revisions, converters, manifests, and hashes are the reproducibility authority; the source archives and database stores are not Git content.
