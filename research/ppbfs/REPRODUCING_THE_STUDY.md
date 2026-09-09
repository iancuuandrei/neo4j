# Reproducing the PPBFS study

## Pinned source states

```text
upstream baseline  f213380f812b820a1b312e2ea52cb3d8f1931ccc
instrumented B0    1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381
clean C1           0a8ef51868e9c8f52dfd49c516d0a14a0295b2c6
research C4        0f8678e159b0837b448093be8607fcc6ebeaddf1
clean C4           7411ac3853c3408466725e54bca7379c5f8f8f0d
P2 fixed V         a007e439a38afb4cd479ae5c0cf4bd516923349f
P2 C1 interaction  7743248849a1af6fa6b365f8e33b88baf46af696
P2 C4 interaction  9c8624c73b051f6cd9bdeff407af184616d861d8
P2 final qual      8797dcb28be870c2544049202b4b9eb11fa9d275
```

Use JDK 21. The recorded experiment ran on Windows 11; Linux has not been
measured. Maven dependencies, distributions, imported databases, datasets,
JFRs, logs, and raw results should live under an external artifact root such as
`$PPBFS_ARTIFACTS`, never in Git. Point Maven's local repository there if disk
placement matters (`-Dmaven.repo.local=<path>` or Maven settings).

## Reproduction map (current)

- P1 reproduction (B0/C1/C4 depth metrics, warmed timing, focused tests):
  `research/ppbfs/maintainer-repro/reproduce.py` (see "Fast controlled reproduction" below).
- C4 reproduction: same script with the clean-C4 worktree; H=4/8/16 sensitivity via
  `research/ppbfs/c4/scripts/run_threshold_sensitivity.ps1`.
- P2 baseline telemetry: `P2SparsityHarness` on `research/ppbfs-p2-baseline-instrumentation-v2`
  (research-only per-bucket counters; append-only CSVs under `$PPBFS_ARTIFACTS/runs/`).
- P2 10-fork qualification: `p2/scripts/run_final_qual.py` (B0/V and C1/C1P2, C4/C4P2 pairs),
  analyzed with `p2/scripts/analyze_qualification.py`.
- P2 final large-k qualification (k≤256, hostile g/i): same rig with the `k*`/`hostile-*`
  workloads; collection danger zone via `P2LargeKMicrobench`; merge audit via
  `P2MergeMicrobench`. Consolidated in `p2/FINAL_QUALIFICATION.md`.
- P1+P2 interaction: `p2/scripts/run_interaction.py` (screening) + final rig pairs above;
  servers via `common/scripts/run_paired_server_experiment.ps1` with jar-swap distributions
  (lib diff must be exactly the runtime-util JAR; see `p2/P1_INTERACTION.md`).

## Fast controlled reproduction

Create detached worktrees at the instrumented B0 and research-C4 commits, plus
a clean-C4 worktree, then run:

```bash
python research/ppbfs/maintainer-repro/reproduce.py \
  --b0-worktree ../neo4j-repro-b0 \
  --c4-research-worktree ../neo4j-repro-c4 \
  --clean-c4-worktree ../neo4j-clean-c4 \
  --output ../ppbfs-repro-output
```

The script validates every SHA, runs the depth-4096 metrics and warmed timing
test for B0/C4, runs the focused tests on clean C4, and writes `summary.json`.
It uses Python, Git, and Maven and is OS-neutral; shell continuation syntax is
the only platform-specific part of the example.

## Full server experiment

1. Verify dataset URLs and hashes in `common/datasets/catalog.csv` and
   `state-index/DATASET_PROVENANCE.md`.
2. Convert/import with the scripts in `common/scripts/`; keep one immutable
   imported store and reference it from each runtime overlay.
3. Build B0/C1/C4 Community distributions at their exact SHAs.
4. Run `common/scripts/run_paired_server_experiment.ps1` or
   `c4/scripts/run_three_variant_experiment.ps1`. On Linux, invoke
   `bin/neo4j console`, wait for `http://127.0.0.1:7474/`, run
   `cypher_http_benchmark.py`, capture metadata, and stop the server for each
   independently restarted fork.
5. Analyze with `common/scripts/analyze_paired_runs.py`.

The protocol uses an identical store/config/JDK/manifest, one complete warmup,
seeded balanced variant order, medians within fork, paired log ratios across
independent forks, and two-sided 95% intervals. `PROFILE` is used only for plan,
result, DB-hit, and tracked-memory evidence; headline latency is unprofiled.

## JFR and memory boundaries

`common/scripts/run_server_fork.ps1` shows the Windows JFR flow. On Linux use
the same `jcmd <pid> JFR.start` / `JFR.dump` commands around unprofiled queries,
then `common/scripts/analyze_jfr.py`. For boundaries, start a fresh server with
the chosen `db.memory.transaction.total.max`, execute the fixed manifest, and
record pass/fail with `common/scripts/run_memory_limit_matrix.ps1` or its direct
command equivalent. A configured boundary is not a byte-accurate heap delta.

Tracked files contain source, manifests, checksums, protocols, analysis code,
and reports. Downloaded graphs, imported stores, built distributions, raw CSV,
logs, profiles, and JFR recordings are local/rebuildable artifacts.

