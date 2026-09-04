# PPBFS research lab

All work in this lab follows [`research/AGENTS.md`](../AGENTS.md), the root
[`AGENTS.md`](../../AGENTS.md), and the self-contained standards under
[`docs/standards/`](../../docs/standards/README.md). External source provenance
is kept separately in [`docs/standards/SOURCES.md`](../../docs/standards/SOURCES.md).

This directory is the single reusable research area for Neo4j core work involving
`StatefulShortestPath`, `PGPathPropagatingBFS`, `FoundNodes`, `BFSExpander`,
`ProductGraphTraversalCursor`, `PathTracer`, `TwoWaySignpost`, bidirectional PPBFS,
and NFA/product-state traversal.

The `research/ppbfs-lab` branch may contain instrumentation, experimental
implementations, benchmark tooling, and research reports. It is never submitted
as an upstream pull request. A successful experiment graduates to a clean
`contrib/*` branch created directly from the authoritative upstream branch.

Paired Neo4j-server experiments use
`common/scripts/run_paired_server_experiment.ps1`. It enforces D:-resident large
inputs/outputs, independently restarted JVM forks, seeded within-pair variant
ordering, metadata capture, and append-only output names. Analyze retained runs
with `common/scripts/analyze_paired_runs.py`.

## Layout

- `common/`: shared datasets, manifests, queries, and variant-neutral scripts.
- `state-index/`: direct/adaptive product-state repository experiments.
- `frontier-selection/`: future bidirectional work-selection experiments.
- `transition-dispatch/`: future NFA transition-dispatch experiments.
- `future/`: scoped placeholders only after an investigation is accepted.

Large downloads, imported databases, distributions, profiles, and raw logs live
outside Git. On this machine `.cache/neo4j-ppbfs-research` is a Git-ignored link
to the shared artifact store. Never commit those large artifacts.

## Current baseline

The state-index experiment is based on:

```text
neo4j/neo4j @ f213380f812b820a1b312e2ea52cb3d8f1931ccc
upstream branch: 2026.07
```

The research chain is intentionally linear:

```text
011f2424184  instrumentation
91011b98e55  controlled materiality harness
1529bdd7fdb  warmed timing mode
caf33be9f5d  canonical direct repository prototype
ddb62158229  state-partitioned canonical repository prototype
bb48edd50d1  adaptive node-major bucket prototype
```

Candidate decisions and the conditional C3 definition are maintained in
`state-index/reports/CANDIDATE_MATRIX.md`. C2 triggered C3, but C3 failed the
mandatory 92 MiB gate. B0 remains the retained design and no direct-state-index
contribution is authorized. The consolidated verdict is in
`state-index/BENCHMARK_REPORT.md`.

Reusable local artifacts remain immutable/versioned where practical. Completed
and negative evidence is preserved under `state-index/results/2026-09-04/`.

## Running one real-graph JVM fork

Build or prepare a distribution for the exact variant, then run:

```powershell
research\ppbfs\common\scripts\run_server_fork.ps1 `
  -Distribution <distribution> `
  -Manifest research\ppbfs\common\manifests\roadNet-PA-pairs.csv `
  -OutputCsv <append-only-output.csv> `
  -LogPrefix <append-only-log-prefix> `
  -Warmups 1 -Repetitions 1 -Seed 20260904
```

The distribution is the variant boundary, so the same runner supports baseline,
state-index, frontier-selection, transition-dispatch, and research-only combined
variants. Run metadata must bind the distribution to its Git SHA and JAR hashes.

Capture immutable run metadata before timing:

```powershell
research\ppbfs\common\scripts\capture_run_metadata.ps1 `
  -Experiment <name> -BaselineSha <sha> -VariantSha <sha> `
  -Dataset <dataset-file> -QueryManifest <manifest> `
  -Config research\ppbfs\common\configs\community-benchmark.conf `
  -Output <append-only-run-directory>\metadata.json
```
