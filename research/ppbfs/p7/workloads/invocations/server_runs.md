# Real server runs (end-to-end Cypher)

## Distributions

Full `mvn package -DskipTests` of each SHA (primed Maven cache; baseline took
~95s, candidate ~8min on the study machine):

- baseline: upstream/2026.08 `736cad02a36bb4a0d32c1064f44768339c814269`
- candidate: `89f2a714f5b` (`contrib/ppbfs-walk-post-saturation-bookkeeping`)

Windows zips unpacked side by side; LiveJournal imported once with
`neo4j-admin database import full` (4,847,571 nodes / 68,993,773 rels) and the
`data` directory copied to the candidate server (identical store).
`conf/neo4j.conf`: benchmark config (auth disabled, 4g heap, 8g pagecache)
plus `dbms.transaction.timeout=90s` so explosive queries return instead of
hanging the harness.

## Index

```cypher
CREATE INDEX snap_id_index IF NOT EXISTS FOR (n:SnapNode) ON (n.snapId)
```

waited to ONLINE on each server before timing.

## Queries

`../cypher/walk_unbound_template.cypher` with `$source` / `$limit` bound per
call over HTTP `tx/commit`; exact timed instances in
`../cypher/timed_instances.md`. One server at a time on `:7474`; stray
transactions from timed-out calls were terminated between runs
(`TERMINATE TRANSACTION`; the tight PPBFS loop ignores termination, so hung
servers were hard-restarted when necessary).
