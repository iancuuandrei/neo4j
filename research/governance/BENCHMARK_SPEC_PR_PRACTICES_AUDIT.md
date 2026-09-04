# Benchmark, specification, documentation, and PR practices audit

## Status and method

- Audit date: 2026-09-04
- Scope: practices applicable to a personal Neo4j research fork and later
  upstream contributions
- Method: four independent Luna subagent audits, divided across Microsoft,
  Google, Meta/AWS, and Neo4j, followed by repository-local synthesis
- Evidence boundary: official documentation and first-party repositories only
- Output: generalizable rules adopted in the repository root `AGENTS.md`

This is a practice audit, not evidence that every cited organization applies one
uniform process to every project. Project-specific numeric thresholds, tools,
review roles, and CLA systems were not copied as universal rules.

## Neo4j: governing upstream requirements

| First-party source | Confirmed practice | Adopted rule |
| --- | --- | --- |
| [Neo4j contribution workflow](https://neo4j.com/developer/contributing-code/) | Fork; create a feature/fix branch; write unit tests; write code; add appropriate Javadocs/manual entries; make commits; send a PR. Before submission, sign the CLA, avoid merge commits, rebase onto current upstream, and run relevant tests. Maven is the reproducible test path. | These requirements are authoritative for upstream Neo4j submissions. No agent may sign the CLA or create an issue/PR without explicit human authorization. |
| Repository `CONTRIBUTING.md` | Use a personal fork, a useful branch name, rebase rather than merge, follow language style, include appropriate unit tests, and sign the CLA. Check for duplicate issues before opening one. | Research and portfolio branches never become PR branches. A clean `contrib/*` branch starts from the current upstream SHA. |
| Repository `pom.xml`, `.scalafmt.conf` | Java 21/Maven 3.9.11 baseline; Maven integration-test, enforcer, Spotless/Palantir Java Format, Scalafmt, ANTLR, and sorted-POM rules are build metadata, not optional conventions. | Derive formatting, generated-source, and validation commands from the current tree. Never guess or bypass them. |
| [Representative performance PR #13831](https://github.com/neo4j/neo4j/pull/13831) | Maintainer review requested exact queries and PROFILE evidence after internal benchmarks did not reproduce a significant benefit. | Performance PR drafts must include exact workloads, plans, SHAs, equivalence proof, measurements, regressions, and limitations; local speed alone is insufficient. |

The repository currently documents a CLA, not a repository-wide DCO rule.
Incidental `Signed-off-by` lines in individual commits do not establish a DCO
requirement and do not replace the CLA.

## Microsoft findings

| First-party source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [BenchPress schema](https://github.com/microsoft/benchpress/blob/main/benchpress/data/SCHEMA.md) | Canonical evaluation settings, primary-source provenance, and explicit audit state are required before a score is included. | Every result binds to immutable settings, inputs, harness, metric, and verification status. |
| [SetupBench](https://github.com/microsoft/SetupBench/blob/main/README.md) | Tasks pin a base image/repository commit, define a machine-checkable success command, isolate execution, and retain logs/timing. | Pin baseline and environment, define the oracle before execution, and preserve raw evidence. |
| [Waza](https://github.com/microsoft/waza/blob/main/README.md) | Snapshots preserve input digests, environment, events, and pinned remote grader revisions; cache validity follows inputs. | Hash all benchmark inputs and version protocol changes; distinguish configuration/incomplete failures from measured failures. |
| [Microsoft engineering design reviews](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/design/design-reviews/README.md) | Consequential designs and trade studies are recorded and reviewed with the code. | Write a versioned design note before consequential architecture, API, schema, or benchmark-contract changes. |
| [Non-functional requirements capture](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/design/design-patterns/non-functional-requirements-capture-guide.md) | Non-functional requirements identify impact, priority, metric, verification, constraints, owner, and dependencies. | Acceptance criteria must be measurable, owned, and testable. |
| [Microsoft pull-request guidance](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/code-reviews/pull-requests.md) | PRs begin from defined acceptance criteria, compile cleanly, include tests/docs, and remain small and focused. | One coherent outcome per PR; tests and documentation change with behavior. |

Microsoft agent-evaluation sample-count and pass-rate heuristics were not
adopted as Neo4j performance thresholds because they measure a different domain.

## Google findings

| First-party source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [Google Benchmark user guide](https://github.com/google/benchmark/blob/main/docs/user_guide.md) | Explicit warmup, repetitions, aggregate statistics, machine preamble, and raw JSON output are supported. | Declare warmup/forks/repetitions, preserve raw samples, and record machine/build context. |
| [Reducing variance](https://github.com/google/benchmark/blob/main/docs/reducing_variance.md) | CPU frequency, scheduling, SMT, caches, NUMA, and competing load can materially distort results. | Control or record material sources of variance and keep realistic-vs-cache-fit tradeoffs explicit. |
| [Random interleaving](https://github.com/google/benchmark/blob/main/docs/random_interleaving.md) | Random interleaving is intended to reduce run-to-run variance. | Randomize/interleave alternatives when feasible and record the seed/order. |
| [Benchmark comparison tools](https://github.com/google/benchmark/blob/main/docs/tools.md) | Results are noisy; the comparison tool reports central tendency, variability, and a stated statistical test while warning that statistical and practical difference are not equivalent. | Predeclare the statistical method and practical threshold; report effect size and confidence, not p-value alone. |
| [Tachometer](https://github.com/google/tachometer/blob/main/README.md) | Repeated sampling continues toward a confidence/effect target and reports ambiguous results instead of forcing a conclusion. | Define a sample stop rule and retain ambiguity as a valid outcome. |
| [Secure and reliable design](https://google.github.io/building-secure-and-reliable-systems/raw/ch04.html) | Design review addresses scale, transient failure, data loss/recovery, dependency failure, integrity, monitoring/SLA, and security/privacy before implementation. | Specifications cover lifecycle and failure behavior, resource limits, observability, security/privacy, and recovery—or explicitly mark them inapplicable. |
| [Documentation best practices](https://github.com/google/styleguide/blob/gh-pages/docguide/best_practices.md) | Keep a small accurate documentation set, update it with code, document entry points/testing/debugging/release, and retain design decisions. | Stale docs are defects; link rather than duplicate authority; preserve decision history. |
| [Engineering review practices](https://google.github.io/eng-practices/review/) | Review covers design, correctness, complexity, tests, style, comments, and docs; changes should be small and self-contained. | Review every changed line in context, separate refactors/formatting, and require tests/docs for the same logical change. |

Google-specific command flags, its internal review terminology, and a universal
Mann-Whitney repetition count were not adopted. The selected statistical method
must be valid for the actual design and sample regime.

## Meta findings

| First-party source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [Velox contributing guide](https://github.com/facebookincubator/velox/blob/main/CONTRIBUTING.md) | Efficiency claims should have objective microbenchmarks; changes have repeatable tests; red CI is investigated; authors self-review; large work is discussed and split; formatting-only cleanup stays separate. | Performance claims need runnable evidence, but microbenchmarks remain distinct from end-to-end proof. Keep changes atomic and investigate every failure. |
| [FAMBench](https://github.com/facebookresearch/FAMBench/blob/main/README.md) | Benchmark identity includes model/workload, implementation, mode, configuration, units, target misses, and hardware-separated results. | Version workload/configuration and never aggregate different hardware without stratification. |
| [React RFC process](https://github.com/reactjs/rfcs/blob/main/README.md) and [template](https://github.com/reactjs/rfcs/blob/main/0000-template.md) | Substantial changes document motivation, examples, design/corners, drawbacks, alternatives, adoption, teaching/docs, and unresolved questions; active status is not merge approval. | Consequential specifications include alternatives, risks, migration/docs impact, and unresolved human decisions. |

FAMBench permits synthetic inputs for its ML scope; that does not justify a
synthetic-only Neo4j performance claim.

## Amazon/AWS findings

| First-party source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [AWS CDK RFC process](https://github.com/aws/aws-cdk-rfcs/blob/main/README.md) and [template](https://github.com/aws/aws-cdk-rfcs/blob/main/0000-template.md) | Consequential changes use a unique tracked proposal, search related work, describe public behavior, design, alternatives, risks, prototype, rollout, and open issues, then require explicit review/sign-off. | Give architecture-affecting work a unique scope, owner, alternatives/risks, evidence plan, rollout/rollback, and explicit status transition. |
| [AWS SDK for Java contributing guide](https://github.com/aws/aws-sdk-java-v2/blob/master/CONTRIBUTING.md) | Non-trivial work is issue-aware and compatibility-conscious; code requires tests, public docs, local build evidence, and review. | Search/discuss before large work; document compatibility and exact local proof. |
| [Graviton performance lab](https://github.com/aws-samples/sample-graviton-performance-lab/blob/main/README.md) | Controlled baseline/alternative systems, a consistent metric/statistical rubric, and durable execution/infrastructure logs support reproducibility. | Declare systems under test, environment, metrics, statistical method, raw storage, teardown, and any cloud cost/credential boundary. |

AWS-specific API review roles and cloud infrastructure steps were not adopted as
Neo4j governance requirements.

## Consolidated rules adopted

The root `AGENTS.md` turns the shared practices into enforceable local policy:

1. specifications precede consequential implementation and contain measurable
   requirements, invariants, alternatives, failure modes, and decision gates;
2. benchmark protocols are frozen and versioned before candidate selection;
3. exact code, binaries, inputs, environment, plan/operator, seeds, and raw
   samples remain traceable and append-only;
4. correctness, causal, timing, memory, regression, and deployment evidence are
   separate claims;
5. confidence intervals and practical effect sizes accompany performance claims,
   while ambiguity and negative results remain visible;
6. documentation changes with behavior and contains reproducible commands and
   explicit limitations;
7. research work never contaminates a clean contribution branch;
8. upstream PRs follow Neo4j's fork/branch/test/docs/CLA/rebase/no-merge rules,
   remain one coherent change, and require explicit human authorization.
