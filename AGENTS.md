# Neo4j contribution research rules

## Scope and precedence

This file governs work in this personal Neo4j fork. It exists on research and
portfolio branches for local contribution discipline; do not copy it into an
upstream pull request unless Neo4j maintainers explicitly request it.

Follow, in order:

1. the user's current task and safety constraints;
2. the nearest more-specific `AGENTS.md` for files below it;
3. Neo4j's repository-local `CONTRIBUTING.md`, build metadata, and test conventions;
4. the official [Neo4j contribution workflow](https://neo4j.com/developer/contributing-code/);
5. this file.

If instructions conflict, stop and report the conflict. Do not weaken an
upstream rule. Treat the official upstream branch and its exact SHA as the
engineering source of truth; the personal fork's default branch may be a
documentation-only portfolio branch.

## Repository and branch isolation

- `origin` is the personal fork; `upstream` is the official Neo4j repository.
- Fetch and identify `upstream/HEAD` before starting or graduating work.
- `research/*` may contain instrumentation, prototypes, harnesses, and reports.
- `contrib/*` must start directly from the intended current upstream SHA and
  contain one independently reviewable contribution only.
- `portfolio/*` contains personal documentation only.
- Never merge a research or portfolio branch into a contribution branch.
- Transfer a proven change by clean reimplementation, a reviewed patch, or
  cherry-picking only commits already limited to the upstream-worthy delta.
- Use separate worktrees for baseline, research, candidate, and contribution
  states. Never reset, clean, delete, or repurpose a worktree with unpublished
  work or unique evidence.
- Never push to `upstream`. Never create an upstream issue, discussion, PR, or
  CLA signature without explicit human authorization.

## Evidence language

Use explicit evidence states in specifications, reports, and PR drafts:

- `SOURCE-CONFIRMED`: established from the identified source revision.
- `PAPER-CONFIRMED`: established by a cited paper.
- `MEASURED`: produced by the recorded experiment.
- `DERIVED`: calculated from confirmed source or measurements.
- `INFERRED`: reasoned but not explicitly stated by a source.
- `SPECULATIVE`: unvalidated possibility.
- `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`, and `EXCLUDED`: execution states.

Do not turn absence of evidence into a pass. A timeout, missing tool, failed
sample, empty output, or unrecognized query plan is not zero and not success.
Preserve contradictory and negative results. Never delete, skip, weaken,
narrow, or rewrite tests, workloads, exclusions, or samples to make a gate pass.

## Specifications and design notes

Write a versioned Markdown specification before consequential architecture,
API, storage, protocol, correctness, performance, benchmark-contract, or
workflow changes. Small local fixes may use a detailed issue/task statement.
Do not create an ADR unless the user explicitly asks for one.

A specification must include:

1. status, date, owner, repository, branch, and exact upstream base SHA;
2. problem statement, motivation, users/workloads, and source evidence;
3. goals, non-goals, scope boundaries, and affected components;
4. functional requirements and quantitative non-functional requirements;
5. invariants, preconditions, compatibility, and externally visible behavior;
6. proposed design, data ownership, lifecycle, and failure behavior;
7. scale assumptions, resource limits, dependencies, concurrency, recovery,
   observability, security, and privacy, marking inapplicable sections explicitly;
8. alternatives considered, drawbacks, counterexamples, and rejection reasons;
9. test oracle, benchmark plan, acceptance and rejection gates, and exact
   reproducible validation commands;
10. migration, rollback, documentation impact, unresolved questions, and the
    human/maintainer decision still required.

Requirements must be measurable and testable. Name the metric, threshold,
verification method, constraints, dependencies, and evidence owner. A proposal
status does not imply implementation approval, correctness, upstream ownership,
or merge acceptance. Update the design note when evidence changes the design;
retain rejected alternatives and prior decisions for provenance.

## Benchmark contract

### Define before running

Every performance claim requires a written benchmark contract before candidate
selection. Record:

- hypothesis and causal mechanism;
- exact baseline and candidate SHAs and their complete diffs;
- benchmark/harness version and immutable command;
- workload, dataset, query, and configuration manifests with hashes;
- correctness oracle and operator/entry-point verification;
- primary metrics, units, practical effect threshold, regression threshold,
  statistical method, confidence level, sample-size/stop rule, and exclusions;
- representative workloads, adversarial cases, common-case regressions, and
  negative-control topologies;
- warm-up, cold/warm cache distinction, JVM forks, measured repetitions,
  randomized/interleaved order, timeout, and failure policy;
- hardware, OS, storage, JDK/JVM, heap, GC, page cache, and relevant runtime flags;
- raw-data, profile, log, metadata, and report destinations.

Do not choose thresholds after seeing results. If the protocol changes, version
it, explain why, and do not silently combine incompatible runs.

### Execute reproducibly

- Compare the same upstream base plus exactly one isolated delta.
- Use separate causal/instrumented and timing builds. Per-operation logging or
  wall-clock calls must not contaminate final latency measurements.
- Verify that each workload reaches the code under test. For Cypher performance,
  capture `EXPLAIN` or `PROFILE`; fail closed on an unexpected operator.
- Validate results before retaining timing. Correctness differences disqualify a
  sample or candidate; they are not performance wins.
- Warm up explicitly, use multiple independent process/JVM forks, and collect
  enough repetitions for the declared statistical method. Randomly interleave
  alternatives where the harness supports it.
- Control and report frequency scaling, competing load, affinity, SMT/NUMA, and
  cache state when material. Prefer realistic workload behavior over an
  artificially cache-fitting benchmark, and document the trade-off.
- Record raw samples, not only aggregates. Include unsuccessful runs, server
  logs, cancellation, timeout, and exclusion reasons.
- Keep units explicit. Record latency and throughput where both matter, plus
  allocation, GC, and tracked memory for memory-sensitive changes.
- Never compare performance numbers from different hardware in one aggregate.
  Keep separate tables or stratify explicitly.
- Never infer practical importance from a p-value alone. Report effect size and
  confidence interval against the predefined practical threshold.
- Report median and distributions. Report p95/p99 only when the sample count
  makes them meaningful; otherwise label them insufficiently sampled.
- Use a paired analysis when runs are paired. State assumptions of the selected
  test; do not use a statistical test below its valid sample regime.
- Distinguish microbenchmark, controlled/synthetic, real-graph, standardized,
  hosted, and production evidence. A microbenchmark cannot prove end-to-end or
  production benefit.

### Preserve artifacts

- Raw results are append-only and immutable once cited. Retry with a new name;
  never overwrite a failed, partial, or completed run.
- Every result must resolve to source SHA, runtime artifact hash, environment,
  dataset/query/config hashes, seed, fork, repetition, timestamp, and command.
- Store rebuildable downloads, Maven/Gradle caches, builds, databases, JFRs,
  logs, and raw runs on `D:` on this machine. Do not accumulate them on `C:`.
- Keep large artifacts outside Git in the shared ignored research store. Git may
  contain scripts, manifests, checksums, small canonical summaries, and reports.
- Do not duplicate large immutable datasets or imported stores per worktree.
- Capture teardown and external-cost/credential requirements for cloud runs.
  Never use paid or credentialed infrastructure without explicit authorization.

### Report without reward hacking

Benchmark reports must contain the exact environment and methods, all samples,
per-workload results, confidence intervals, effect sizes, causal metrics,
correctness, memory/GC, regressions, excluded cases, failures, limitations, and
the acceptance-gate verdict. Separate measured facts from derivation and
interpretation. If results are ambiguous, report ambiguity and collect more
evidence; do not force a positive conclusion.

## Documentation rules

- Documentation changes with the behavior or harness in the same research or
  contribution change. Stale commands and claims are defects.
- Use one H1, descriptive H2 sections, concise prose, stable relative links, and
  alt text for meaningful images. Render or validate links and examples.
- A durable project/research README should cover purpose, status, prerequisites,
  architecture or entry points, configuration, exact usage/test commands,
  artifact locations, known limitations, and contribution workflow as applicable.
- Do not duplicate authoritative instructions. Link to the source and state the
  local delta. Preserve design notes as decision history after implementation.
- Label generated material and its generator; edit the source, not generated
  registries or reports, unless the repository says otherwise.
- Never put secrets, tokens, private environment values, machine credentials,
  sensitive paths, or confidential data in text, metadata, comments, examples,
  Git history, or benchmark artifacts.
- Do not claim hosted, production, cross-platform, statistical, memory, or
  correctness proof when it was not run. Use `NOT RUN` or `BLOCKED`.

## Testing and code changes

- Read the nearest module instructions and existing tests before editing.
- Preserve or strengthen existing assertions. If an old assertion no longer
  models the intended invariant, replace it with a more precise assertion and
  explain the semantic reason; never reduce it to a mere nonzero/smoke check.
- Add small, readable tests even for small code contributions when behavior is
  involved. Reuse the strongest existing oracle and cover edge, failure,
  cancellation, cleanup, memory-limit, and concurrency behavior as applicable.
- Run tests with the repository-supported Maven command, not only an IDE.
- Run the narrowest relevant suite after each change, then the broader affected
  module/downstream suites before graduation. Record command, exit code, test
  counts, skips, failures, runtime, and anything not run.
- Use repository formatting/static-analysis/generated-source commands derived
  from current build metadata. Do not guess or bypass a failing check.
- Do not mix formatting, mechanical cleanup, dependency changes, or unrelated
  refactors with a behavior/performance change.

## Upstream Neo4j pull-request gate

Neo4j's official workflow is authoritative: fork the appropriate repository,
create a specifically named branch, write unit tests, write code, add appropriate
Javadocs/manual documentation, make logical commits, and then send a PR. Before
submission, the contributor must sign the Neo4j CLA, avoid merge commits, rebase
onto the current upstream branch, and run all relevant tests.

Before preparing or opening an upstream PR:

1. refresh `upstream/HEAD`, record the exact target SHA, and re-audit source and
   open/recent issues and PRs for overlap;
2. obtain human agreement for any required issue/discussion and never post it
   automatically;
3. create or refresh a clean `contrib/*` branch directly from upstream; rebase,
   do not merge upstream history;
4. ensure the diff contains one coherent outcome, its focused tests, necessary
   Javadocs/manual docs, and only maintainable benchmark additions;
5. remove instrumentation, debug switches, research-only strategies, datasets,
   raw results, profiles, machine paths, credentials, and portfolio files;
6. run formatting, focused tests, affected module tests, downstream tests, and
   the broadest practical build; truthfully list anything not run;
7. inspect every changed line and the surrounding ownership/lifecycle context;
8. verify clean status, no merge commits, intentional author identity, no secret
   or large-artifact additions, and a diff against the exact upstream target;
9. prepare a local PR description and obtain explicit human authorization before
   any GitHub write action or CLA action.

The PR description must explain the problem and intent, exact invariant/public
behavior, issue reference when one exists, source files changed, tests and exact
commands, before/after benchmark and memory tables for performance claims,
regressions and limitations, compatibility, rollback, and unresolved maintainer
questions. Use English, accurate punctuation, and logical commit messages that
convey intent. Do not describe proposed/open work as merged or accepted.

Investigate every CI failure; never assume it is unrelated without evidence.
Address or explicitly disposition every review comment. Avoid force-pushing
after review when it would destroy useful review context unless maintainers ask
for rewritten history. Human contributors remain responsible for understanding,
reviewing, and defending agent-assisted changes; disclose assistance when the
target project's policy or PR template requires it.

## Primary governance references

The source audit and rationale for these rules is maintained in
`research/governance/BENCHMARK_SPEC_PR_PRACTICES_AUDIT.md`. The most important
upstream-specific sources are `CONTRIBUTING.md` and the official
[Neo4j contributing-code guide](https://neo4j.com/developer/contributing-code/).
