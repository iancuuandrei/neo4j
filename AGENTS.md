# Neo4j contribution workspace

## Purpose

This is the mandatory entrypoint for agents working in this personal Neo4j
fork. It and `docs/standards/` are local research governance and must not enter
an upstream pull request unless Neo4j maintainers explicitly request them.

## Instruction order

Follow, in order:

1. the user's current task and safety constraints;
2. the nearest scoped `AGENTS.md` for the files being changed;
3. Neo4j's `CONTRIBUTING.md`, current build metadata, and module conventions;
4. the self-contained rules in `docs/standards/`;
5. this entrypoint.

Stop and report a real conflict. Never weaken an upstream requirement.

## Required standards

Read the standards relevant to the task before changing files:

- [`EVIDENCE.md`](docs/standards/EVIDENCE.md) for audits, experiments, tests,
  qualification, and result claims;
- [`SPECIFICATIONS.md`](docs/standards/SPECIFICATIONS.md) before consequential
  architecture, API, storage, protocol, benchmark-contract, or workflow changes;
- [`BENCHMARKS.md`](docs/standards/BENCHMARKS.md) before designing, running,
  interpreting, or publishing a benchmark;
- [`DOCUMENTATION.md`](docs/standards/DOCUMENTATION.md) for durable documentation;
- [`UPSTREAM_PULL_REQUESTS.md`](docs/standards/UPSTREAM_PULL_REQUESTS.md) before
  graduating research or preparing an upstream contribution.

[`SOURCES.md`](docs/standards/SOURCES.md) records provenance and rationale. It
does not replace the executable local rules.

## Repository safety

- `origin` is the personal fork; `upstream` is the official Neo4j repository.
- The exact fetched `upstream/HEAD` SHA is the engineering source of truth.
- `research/*` may contain prototypes, instrumentation, harnesses, and reports.
- `contrib/*` starts directly from upstream and contains one reviewable change.
- `portfolio/*` contains personal records only.
- Never merge research or portfolio history into a contribution branch. Transfer
  only the reviewed production delta and upstream-worthy tests/documentation.
- Use separate worktrees for concurrent baseline, research, and contribution
  states. Never reset, clean, delete, or repurpose unpublished work or evidence.
- Never push to `upstream`.
- Never create an upstream issue, discussion, PR, or CLA signature without
  explicit human authorization.

## Machine storage

On this machine, rebuildable and large state belongs on `D:`: build caches and
outputs, downloaded datasets, imported databases, distributions, profiles,
logs, and raw benchmark runs. Do not create persistent accumulation on `C:`.
Prefer one shared immutable artifact store over copies in every worktree.

## Non-negotiable conduct

- Preserve or strengthen tests; never weaken a gate to make work pass.
- Keep raw evidence append-only. Retries get new names; failed and negative
  evidence remains visible.
- Distinguish `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`, and `EXCLUDED`.
- Do not claim correctness, statistical confidence, memory safety, hosted
  behavior, or production readiness unless recorded evidence proves it.
- Do not mix behavior changes with unrelated cleanup, formatting, research
  infrastructure, portfolio files, or other contribution ideas.
