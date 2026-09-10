# Timed server-query instances (LiveJournal)

All use `cypher/walk_unbound_template.cypher` with the stated `$limit`.
Server: full `mvn package` distributions of the two SHAs below, LJ import
(4,847,571 nodes / 68,993,773 rels), `snapId` range index ONLINE,
`dbms.transaction.timeout=90s`, HTTP tx/commit, auth disabled (benchmark conf).

- baseline distribution: upstream/2026.08
  `736cad02a36bb4a0d32c1064f44768339c814269`
- candidate distribution: same SHA plus the P7 patch
  (`contrib/ppbfs-walk-post-saturation-bookkeeping`,
  `89f2a714f5b39c4f1383a84b26ceacbaaeaf0c9c`)

## 2022306 (high-opportunity hub)

- `$limit 75`: baseline >90s (server timeout, incomplete) vs candidate
  53 rows in 52ms. Timeout-vs-completion observation, not a speedup ratio.
- `$limit 50`: baseline 73ms median; rows identical 50/50 vs candidate.

## 3705591 (moderate opportunity)

- `$limit 500`: baseline 80ms median vs candidate 61ms median,
  rows identical 500/500 (target id + path length per row, in order).

## 1042128 (low-opportunity control)

- `$limit 500`: baseline 76ms vs candidate 77ms (parity).

## Plan proof (operator shape)

`EXPLAIN` of the template on the baseline server yields operator
`StatefulShortestPath(All, Walk)` (planner COST, runtime SLOTTED, version
2026.08.0) with the target `(t)` unbound, inlined `LINK` predicate, and the
source via `NodeIndexSeek` on `s:SnapNode(snapId)`. The simpler
both-endpoints-bound `SHORTEST 1` implementation does not apply to this shape.
