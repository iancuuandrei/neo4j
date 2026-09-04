# Benchmark standard

## Freeze the contract before candidate selection

Every performance claim requires a written, versioned contract recording:

- hypothesis and causal mechanism;
- exact baseline and candidate SHAs and complete isolated diff;
- harness version and immutable execution command;
- workload, dataset, query, source-target pair, and configuration manifests,
  including versions and hashes;
- correctness oracle and proof the workload reaches the code/operator under test;
- metrics and units, practical benefit and regression thresholds, statistical
  method, confidence level, sample-size or stopping rule, and exclusions;
- representative workloads, adversarial cases, common-case checks, and controls;
- warmup, cache state, process/JVM forks, repetitions, randomized/interleaved
  order, timeout, retry, and failure policy;
- hardware, OS, storage, JDK/JVM, heap, GC, page cache, and runtime flags;
- raw-data, profile, log, metadata, analysis, and report destinations.

Do not choose thresholds after seeing results. Protocol changes create a new
version and must not silently combine incompatible runs.

## Execute reproducibly

- Compare the same upstream base plus exactly one isolated delta.
- Keep causal instrumentation builds separate from final timing builds.
- Validate result equivalence before retaining timing. Correctness differences
  disqualify a candidate or sample; they are never performance wins.
- For Cypher claims, capture `EXPLAIN` or `PROFILE`; fail closed when the expected
  operator or execution path is absent.
- Warm up explicitly and use multiple independent process/JVM forks.
- Randomize or interleave alternatives when supported; record seed and order.
- Record variance sources: frequency scaling, competing load, affinity, SMT,
  NUMA, cache state, storage, and background services.
- Preserve raw samples and unsuccessful runs, including timeout, cancellation,
  retry, and exclusion reasons.
- Keep units explicit. Report latency and throughput when both matter, plus
  allocation, GC, tracked memory, and near-limit behavior for memory changes.
- Do not aggregate different hardware as one population.
- Controlled graphs establish mechanisms and scaling; they do not replace real
  or standardized workloads.

## Statistical interpretation

- Predeclare the analysis and assumptions; use paired analysis for paired runs.
- Report effect size and confidence interval against the practical threshold;
  a p-value alone is not materiality.
- Report medians and distributions. Use p95/p99 only with adequate samples.
- Do not use a statistical test below its valid sample regime.
- Ambiguity is a valid result. Gather data or report uncertainty; never force a
  positive conclusion.

## Artifact contract

Each retained run resolves to source SHA, runtime artifact hash, environment,
dataset/query/config hashes, seed, fork, repetition, timestamp, command, exit
state, and raw output. Analysis outputs identify all input files.

On this machine, datasets, stores, build caches/products, distributions, JFRs,
logs, and raw results live on `D:` outside Git. Use shared immutable artifacts
instead of per-worktree copies. Git may contain scripts, manifests, checksums,
small canonical summaries, and durable reports.

## Report contract

A report contains exact methods/environment, all samples, per-workload results,
effects and confidence intervals, causal metrics, correctness, memory and GC,
regressions, exclusions, failures, limitations, and gate verdict. Separate
measurements, derivations, and interpretation. Local proof is not hosted or
production proof.
