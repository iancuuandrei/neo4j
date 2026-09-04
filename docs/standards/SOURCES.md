# Standards source audit

## Status and method

- Audit date: 2026-09-04
- Scope: benchmark, specification, documentation, and contribution practices
  applicable to a personal Neo4j research fork
- Method: independent Luna-agent audits of Microsoft, Google, Meta/AWS, and
  Neo4j, followed by repository-local synthesis
- Evidence boundary: official documentation and first-party repositories only

This appendix records provenance and rationale. Normative local rules are in
the sibling standards files. Organization-specific tools, numeric thresholds,
review roles, and legal processes were not copied as universal requirements.

## Neo4j sources

| Source | Confirmed practice | Local adoption |
| --- | --- | --- |
| [Neo4j contribution workflow](https://neo4j.com/developer/contributing-code/) | Fork, dedicated branch, unit tests, code, Javadocs/manual, commits, PR; sign CLA, avoid merge commits, rebase, and run relevant tests. | Mandatory upstream workflow in `UPSTREAM_PULL_REQUESTS.md`; agents cannot perform CLA or GitHub writes without human authorization. |
| Repository `CONTRIBUTING.md` | Personal fork, useful branch, rebase rather than merge, style/tests, CLA, and duplicate-issue check. | Research never becomes a PR branch; contribution branches start cleanly from upstream. |
| Repository `pom.xml` and `.scalafmt.conf` | Current Java/Maven, integration-test, enforcer, formatting, ANTLR, and sorted-POM rules live in build metadata. | Derive commands from the current tree; never guess or bypass them. |
| [Representative performance PR #13831](https://github.com/neo4j/neo4j/pull/13831) | Maintainers requested exact queries and PROFILE evidence when internal benchmarks did not reproduce material benefit. | Performance drafts carry exact workloads, plans, SHAs, equivalence, measurements, regressions, and limitations. |

The repository documents a CLA, not a repository-wide DCO rule. An incidental
`Signed-off-by` line does not establish DCO policy or replace the CLA.

## Microsoft sources

| Source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [BenchPress schema](https://github.com/microsoft/benchpress/blob/main/benchpress/data/SCHEMA.md) | Canonical settings, primary-source provenance, and explicit audit state precede score inclusion. | Bind results to immutable settings, inputs, harness, metric, and verification state. |
| [SetupBench](https://github.com/microsoft/SetupBench/blob/main/README.md) | Tasks pin base image/repository commit, define a machine oracle, isolate execution, and retain logs/timing. | Pin baseline/environment, predefine the oracle, preserve raw evidence. |
| [Waza](https://github.com/microsoft/waza/blob/main/README.md) | Snapshots preserve input digests, environment, events, and pinned evaluator revisions; cache validity follows inputs. | Hash inputs, version protocols, and distinguish configuration failures from measured failures. |
| [Design reviews](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/design/design-reviews/README.md) | Consequential designs and trade studies are recorded and reviewed with code. | Version specifications before consequential changes and retain decisions. |
| [Non-functional requirements](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/design/design-patterns/non-functional-requirements-capture-guide.md) | Requirements identify metric, verification, constraints, owner, and dependencies. | Acceptance criteria are measurable, owned, and testable. |
| [Pull-request guidance](https://github.com/microsoft/code-with-engineering-playbook/blob/main/docs/code-reviews/pull-requests.md) | PRs start from acceptance criteria, compile cleanly, include tests/docs, and remain focused. | One coherent outcome per PR; tests and docs change with behavior. |

Microsoft agent-evaluation sample-count and pass-rate heuristics were excluded
because they are not valid universal Neo4j performance thresholds.

## Google sources

| Source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [Google Benchmark guide](https://github.com/google/benchmark/blob/main/docs/user_guide.md) | Explicit warmup, repetitions, aggregates, machine preamble, and raw JSON. | Declare sampling and preserve raw data plus environment. |
| [Reducing variance](https://github.com/google/benchmark/blob/main/docs/reducing_variance.md) | Frequency, scheduling, SMT, caches, NUMA, and competing load distort results. | Control or record material variance and cache-fit tradeoffs. |
| [Random interleaving](https://github.com/google/benchmark/blob/main/docs/random_interleaving.md) | Interleaving reduces run-to-run variance. | Randomize/interleave alternatives and record order/seed. |
| [Comparison tools](https://github.com/google/benchmark/blob/main/docs/tools.md) | Comparison reports variability/statistical tests and warns that significance is not practical importance. | Predeclare method and practical threshold; report effect and confidence. |
| [Tachometer](https://github.com/google/tachometer/blob/main/README.md) | Sampling works toward confidence/effect targets and may report ambiguity. | Predeclare stop rules and retain ambiguity as a result. |
| [Secure and reliable design](https://google.github.io/building-secure-and-reliable-systems/raw/ch04.html) | Design review covers scale, failure, recovery, integrity, monitoring, security, and privacy. | Specifications cover lifecycle/failures/resources/observability or mark them inapplicable. |
| [Documentation practices](https://github.com/google/styleguide/blob/gh-pages/docguide/best_practices.md) | Keep a small accurate doc set, update with code, document entry points/testing, and retain decisions. | Stale docs are defects; link authority and preserve decision history. |
| [Engineering review practices](https://google.github.io/eng-practices/review/) | Review covers design, correctness, complexity, tests, style, comments, and docs; changes stay small. | Review every changed line in context and keep changes atomic. |

Google-specific flags, internal review terminology, and universal sample counts
were excluded; statistical methods must fit the actual experiment design.

## Meta and AWS sources

| Source | Confirmed practice | Generalized rule |
| --- | --- | --- |
| [Velox contribution guide](https://github.com/facebookincubator/velox/blob/main/CONTRIBUTING.md) | Efficiency claims use objective microbenchmarks; repeatable tests, self-review, CI investigation, and atomic changes are expected. | Performance requires runnable evidence; microbenchmarks remain distinct from end-to-end proof. |
| [FAMBench](https://github.com/facebookresearch/FAMBench/blob/main/README.md) | Benchmark identity includes workload, implementation, mode, configuration, units, targets, and hardware separation. | Version workload/configuration and stratify different hardware. |
| [React RFC process](https://github.com/reactjs/rfcs/blob/main/README.md) | Substantial changes document motivation, design/corners, drawbacks, alternatives, adoption, docs, and questions. | Specifications retain alternatives, risks, migration, docs impact, and unresolved decisions. |
| [AWS CDK RFC process](https://github.com/aws/aws-cdk-rfcs/blob/main/README.md) | Consequential proposals have unique tracking, public behavior, design, alternatives, risks, prototype, rollout, and explicit approval. | Use unique scope, evidence plan, rollout/rollback, and explicit status transitions. |
| [AWS SDK for Java contribution guide](https://github.com/aws/aws-sdk-java-v2/blob/master/CONTRIBUTING.md) | Non-trivial work is issue-aware and compatibility-conscious; changes need tests, public docs, local build, and review. | Search overlap; document compatibility and exact proof. |
| [Graviton performance lab](https://github.com/aws-samples/sample-graviton-performance-lab/blob/main/README.md) | Controlled baseline/alternative systems and durable execution/infrastructure logs support reproducibility. | Declare systems, environment, metrics, statistics, raw storage, teardown, and cost boundary. |

FAMBench's synthetic-data allowance and AWS-specific review roles/cloud steps
were excluded because they do not transfer universally to Neo4j.

## Synthesis

The common practices adopted locally are: specify before consequential work;
freeze benchmark contracts before candidate selection; preserve exact provenance
and raw samples; separate correctness, causal, timing, memory, deployment, and
production claims; report confidence and practical effect; keep documentation
current; isolate research from contribution branches; and follow Neo4j's own
fork, test, documentation, CLA, rebase, and no-merge workflow.
