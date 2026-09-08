# Neo4j PPBFS P2 — sparse NFA-state storage research

## Status

`INFERRED` program; `MEASURED` claims appear only with recorded experiments.
Active branch: `research/ppbfs-p2-baseline-instrumentation` (instrumentation only — never a timing baseline).
See `REPO_ARCHAEOLOGY.md` (`SOURCE-CONFIRMED` on the historical baseline) and `ACCESS_PATTERN.md` (roles and operation mix).

## Baselines (`SOURCE-CONFIRMED`)

| Role | Value |
| --- | --- |
| Historical causal baseline (P1/P2 comparison) | `f213380f812b820a1b312e2ea52cb3d8f1931ccc` |
| Current upstream cross-check, branch `upstream/2026.08` | `736cad02a36bb4a0d32c1064f44768339c814269` |
| Upstream fetch date (UTC) | 2026-09-08 |

Do not mix measurements between baselines. The eventual contribution candidate (only if gates pass)
is ported cleanly onto the then-current upstream SHA; the `f213…` baseline exists for causal P1 comparison.

## Frozen reference (do not modify)

- `contrib/ppbfs-direct-state-index` (C1), `contrib/ppbfs-deferred-state-index` (C4)
- `research/ppbfs-c4-deferred-index`, `research/ppbfs-c4-threshold-sensitivity`
- `research/ppbfs-lab` (P1 archive; contains a prior field-based experimental `StateBucket`, canonical-only)
- `benchmark/ppbfs-external-observability` (global occupancy metric — **not** the P2 sparsity oracle)

## Branch progression

`research/ppbfs-p2-baseline-instrumentation` → candidate branches (only after gates) →
`research/ppbfs-p2-c1-interaction` / `research/ppbfs-p2-c4-interaction` (P2 winner only) →
`contrib/ppbfs-sparse-state-storage` (only if graduation is justified; fresh from upstream, reviewed delta only).

P2-only timing candidates are built from the clean historical baseline plus the production delta.
Instrumentation builds are never timing builds.

## Documents

- `REPO_ARCHAEOLOGY.md` — Phase 1 source audit with line-level call map.
- `ACCESS_PATTERN.md` — bucket roles and operation mix.
- `WORKLOAD_DESIGN.md`, `WORKLOAD_MANIFEST.csv` — Phase 3 (pending telemetry freeze).
- `SPARSITY_REPORT.md` — Phase 2 per-bucket distributions (pending).
- `COST_MODEL.md` — Phase 4 (pending fitted constants).
- `CANDIDATE_DESIGNS.md`, `MICROBENCH_RESULTS.md`, `END_TO_END_RESULTS.md`, `MEMORY_RESULTS.md`,
  `JFR_ANALYSIS.md`, `P1_INTERACTION.md`, `FINAL_REPORT.md`, `MAINTAINER_HANDOFF.md` — staged (pending gates).
- `scripts/`, `queries/`, `manifests/`, `plots/` — harness and manifests (pending).

Large datasets, stores, JFRs, builds and raw runs live outside Git under the research artifact policy on `D:`.
