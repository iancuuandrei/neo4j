# Direct-PPBFS scan invocations (applicability/counter study)

The scan opens a real store read-only in embedded mode and runs unbound
SHORTEST-K WALK PPBFS per source with counting hooks
(pushes split pre/post-saturation via `stack.target().isSaturated()`,
targets, schedules, target-signpost registrations, prunes, rows). One JVM per
variant; the variant is selected by placing that variant's
`community/cypher/runtime-util/target/classes` FIRST on the classpath:

- baseline: worktree at `736cad02a36bb4a0d32c1064f44768339c814269`
- candidate: worktree at `89f2a714f5b` (P7 branch)

Remaining classpath: `neo4j:2026.08.0` + `neo4j-cypher-runtime-util:2026.08.0`
from Maven Central. JVM: JDK 21, `-Xmx8g`.

## Discovery (single run per variant)

```text
--store <store-home> --label SnapNode --prop snapId
--sources manifests/<list> --out <csv>
--k 1 --max-rows 500 [--mode walk]
```

Per-dataset lists in `../manifests/`, per-query time caps:

| Dataset | Sources file | cap |
| --- | --- | --- |
| LiveJournal | `lj_discovery_sources.txt` (68) | 180s |
| web-Stanford | `web_discovery_sources.txt` (61) | 60s |
| as-Skitter | `skitter_discovery_sources.txt` (61) | 60s |
| Hetionet Gene/Compound/Disease | `hetionet_*_sources.txt`, `--label <Label> --prop hetioId` | 60s |
| Hetionet typed chain | Gene list + `--nfa chain2:G_I_G,G_I_G` | 120s |

Spot checks (LJ, `lj_k2_groups_trail_subset.txt`, 120s cap): `--k 2`;
`--k 2 --groups true`; `--mode trail`; bound pairs via
`--pairs research/ppbfs/common/manifests/LiveJournal-pairs-v1.csv`
(bidirectional search, intoTarget = target node).

## Timing (5 fresh JVM forks, order-balanced variants, 1 warmup + 3 reps per fork)

Direction is OUTGOING throughout; relationship types are unfiltered (`null`
type set) except the `chain2` Hetionet runs, which restrict to the two stated
`G_I_G` expansions. Node predicates accept everything (`n -> true`).

```text
--warmups 1 --reps 3   (plus the fork loop with per-fork seed order flip)
```

Frozen sets: `lj_timing_sources.txt` (5), `web_timing_sources.txt` (5, monster
capped separately at 300s / 1 rep for the feasibility observation),
`skitter_timing_sources.txt` (3), `hetionet_timing_genes.txt` (2).

## Caps and truncation (read this before interpreting counts)

- `--max-rows 500`: iteration stops after 500 yielded rows, so post-saturation
  push counts are **truncated, not inflated** — full queries would show at
  least as much post-saturation work.
- `--time-ms` caps truncate individual explosive queries (flagged
  `timed_out` in the CSV); the 6 web-Stanford baseline truncations are
  precisely the queries where the candidate pulls ahead most.
- Discovery is single-run counters (deterministic given the store); only the
  timing phase uses forks/reps with paired-log 95% CIs.
