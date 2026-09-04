# Cypher runtime-util contribution rules

## Scope

This governs `community/cypher/runtime-util/`, including PPBFS production and
test code. Follow root standards and current Maven/module conventions.

## Correctness and lifecycle

- Read existing tests and ownership/lifecycle code before editing.
- Preserve identity, frontier/history visibility, propagation order, tracing,
  cancellation, close behavior, and scoped memory accounting.
- Faster lookup must not change path multiplicity, contractual result order,
  target-signpost behavior, or bidirectional traversal semantics.
- Canonical retained state and frontier membership are distinct lifecycles;
  tests and memory assertions must identify which lifecycle they verify.
- Preserve or strengthen assertions. Never replace a relational or exact
  invariant with a nonzero smoke check merely because implementation changes.

## Validation

- Add small focused tests for lookup identity, retired-frontier visibility,
  cleanup, memory limits, cancellation, and bidirectional cases as applicable.
- Reuse generated/differential or the strongest existing correctness oracle.
- Run focused tests after each change, then affected module and downstream suites.
- Performance evidence follows `docs/standards/BENCHMARKS.md`; instrumentation
  and research-only benchmarks stay on `research/*` unless a small maintainable
  benchmark is demonstrably appropriate upstream.
