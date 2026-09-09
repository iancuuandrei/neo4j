# P2 maintainer handoff

## Recommendation (verdict B — useful mainly with P1)

Ship the fixed sorted-vector `StateBucket` together with the P1 canonical/deferred index work,
not as a standalone latency PR. Standalone: narrow sparse-regime win, neutral elsewhere.
Combined (C1+P2 / C4+P2): −2.6…−4.2× bucket memory with architectures and result equivalence
preserved. Evidence: `FINAL_REPORT.md` + append-only runs on `D:` (telemetry, microbench,
10-fork qualification, memory-limit probes, JFR, H3/LJ paired servers, 6-way interaction).

## Clean extraction plan (not executed — conditional on P1 graduation)

From current upstream SHA (re-fetch and record at extraction time), transfer exactly:

1. `community/cypher/runtime-util/.../ppbfs/StateBucket.java` (new; public only for the
   `ppbfs.hooks` debug package — see class javadoc; no interface, no boxing, exact tracking).
2. `FoundNodes.java` delta (~27 lines: bucket types, `put`/`get`, javadoc).
3. `BFSExpander.java` delta (~6 lines: `appendActiveStatesTo`).
4. `StateBucketTest.scala` (9 focused tests; same-package, no new fixtures beyond existing idioms).

Exclude: telemetry, harnesses, scripts, reports, manifests, JFRs, distributions, D: paths.
For C1/C4 variants, additionally port the `closeLevel`/retire/`mergeBuckets` adaptations from
`research/ppbfs-p2-c1-interaction` / `research/ppbfs-p2-c4-interaction` (same bucket file).

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
- roadNet/gMark/cit-Patents/as-Skitter/web-Stanford server runs: NOT RUN (time-boxed).
- No adaptive threshold, no range/hash/chunked variants (all rejected with evidence in
  `CANDIDATE_DESIGNS.md` / `MICROBENCH_RESULTS.md`).
- C1 fails `grid60-s63` with and without P2 (pre-existing, harness-exacerbated); unrelated.
- First-in-fork cold page cache confounds naive paired-server aggregates: always verify fork
  order balance (see H3 v2→v3) or pre-warm the working set.
