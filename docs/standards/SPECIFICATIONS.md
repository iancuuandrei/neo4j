# Specification standard

## When a specification is required

Write a versioned Markdown specification before a consequential architecture,
API, storage, protocol, correctness, performance, benchmark-contract, or
workflow change. A small local fix may use a detailed task or issue statement.
Do not create an ADR unless the user explicitly requests one.

## Required content

A specification contains:

1. status, date, owner, repository, branch, and exact upstream base SHA;
2. problem, motivation, affected users/workloads, and source evidence;
3. goals, non-goals, scope boundaries, and affected components;
4. functional and quantitative non-functional requirements;
5. invariants, preconditions, compatibility, and visible behavior;
6. design, data ownership, lifecycle, concurrency, and failure behavior;
7. scale, resource limits, dependencies, recovery, observability, security, and
   privacy, with inapplicable sections explicitly marked;
8. alternatives, drawbacks, counterexamples, and rejection reasons;
9. correctness oracle, benchmark plan, acceptance/rejection gates, and exact
   reproducible validation commands;
10. migration, rollback, documentation impact, unresolved questions, and the
    human or maintainer decision still required.

## Requirements quality

Each non-functional requirement names its metric, threshold, verification
method, constraints, dependencies, and evidence owner. Requirements must be
specific, measurable, and testable. Choose gates before observing candidate
results; a changed gate requires a versioned rationale.

## Lifecycle

- Give each consequential proposal a unique stable identifier or task scope.
- Search source, specifications, issues, and PRs before work.
- Review the specification before large or invariant-sensitive implementation.
- Update it when evidence changes the design; retain rejected alternatives and
  prior decisions for provenance.
- Record status: draft, accepted for experiment, rejected, graduated, or
  superseded. Experiment acceptance is not upstream or merge approval.
- A prototype proves feasibility only within its evidence boundary.
