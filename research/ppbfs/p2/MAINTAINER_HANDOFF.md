# P2 maintainer handoff (final qualification included)

## Recommendation (verdict B — useful mainly with P1)

Ship the fixed sorted-vector `StateBucket` together with the P1 canonical/deferred index work,
not as a standalone latency PR. Standalone: narrow sparse-regime win (+17%/+5%), neutral
elsewhere through k=256 at g/i≈1150 (falsification survived, no adaptive branch).
Combined (C1+P2 / C4+P2): −2.6…−4.2× bucket memory, +10–51% sparse chains, deep-road
preserved, bounded by a disclosed ~3–6% tax on lookup-saturated tiny-NFA canonical traffic
(Hetionet H3 pairs). Evidence: `FINAL_REPORT.md` + `FINAL_QUALIFICATION.md` + append-only runs
on `D:` (telemetry, microbenches, 10-fork qualifications, memory-limit probes, JFR,
H3/LJ/roadNet paired servers, 6-way interaction).

## Clean extraction plan (not executed — conditional on P1 graduation)

From current upstream SHA (re-fetch and record at extraction time), transfer exactly:

1. `community/cypher/runtime-util/.../ppbfs/StateBucket.java` (new; public only for the
   `ppbfs.hooks` debug package — see class javadoc; no interface, no boxing, exact tracking).
2. `FoundNodes.java` delta (~27 lines: bucket types, `put`/`get`, javadoc).
3. `BFSExpander.java` delta (~6 lines: `appendActiveStatesTo`).
4. `StateBucketTest.scala` (9 focused tests; same-package, no new fixtures beyond existing idioms).

Exclude: telemetry, harnesses, scripts, reports, manifests, JFRs, distributions, D: paths.
If maintainers select C1, extract from `contrib/ppbfs-direct-state-index` + the reviewed P2
delta (canonical `closeLevel` adaptation in `research/ppbfs-p2-c1-interaction`).
If they select C4, extract from `contrib/ppbfs-deferred-state-index` + the reviewed P2 delta
(retire/`mergeBuckets` adaptation in `research/ppbfs-p2-c4-interaction`) + any qualified merge
fix (none needed: re-put retained). Do NOT create standalone
`contrib/ppbfs-sparse-state-storage` under verdict B.

## Validation to repeat at extraction

- Focused suites + full `community/cypher/runtime-util` module
  (`mvn -Dmaven.repo.local=<warm> -pl community/cypher/runtime-util -DsequentialTests test`;
  530 tests / 5 pre-existing ignores on 2026-09-09).
- `spotless:check` on touched modules; Community build if the delta widens.
- Re-run the 10-fork unit rig (`research/ppbfs/p2/scripts/run_qualification.py`) — expect
  chain255 ≈1.17, all others within ±5% or CI-overlapping 1.0, oracles matching.
- Downstream suites: none affected beyond runtime-util (public signature change is limited to
  `FoundNodes.frontier()` return type inside `org.neo4j.internal.*`; verify with a full grep
  for external callers at extraction time).

## Known limits and non-goals

- Real-world large-S Cypher queries were not surveyed beyond the P1 corpora (all small-S):
  the standalone latency win may rarely trigger in practice — the memory/P1 case carries.
- gMark/cit-Patents/as-Skitter/web-Stanford server runs: NOT RUN (time-boxed); roadNet-PA,
  Hetionet H3 and LiveJournal server evidence recorded.
- No adaptive threshold (rejected: falsification survived AND the residual H3 tax sits at k=3–4
  where promotion cannot help), no range/hash/chunked variants, no role specialization (rejected:
  would sacrifice the canonical memory win for ≤6% H3).
- C1 fails `grid60-s63` with and without P2 (pre-existing, harness-exacerbated); unrelated.
- First-in-fork cold page cache confounds naive paired-server aggregates: always verify fork
  order balance (see H3 v2→v3) or pre-warm the working set.
- H3-P1 tax (~3–6% on lookup-saturated tiny-NFA canonical traffic, both P1 pairs, 10 forks each,
  order-balanced pooled) is the disclosed boundary. Unfixable within the candidate space
  (dense == status quo); judge the C1/C4 package net of it.
