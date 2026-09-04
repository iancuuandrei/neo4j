# Evidence standard

## Evidence states

Use explicit labels in specifications, experiment logs, reports, and PR drafts:

- `SOURCE-CONFIRMED`: established from an identified source revision.
- `PAPER-CONFIRMED`: established by a cited paper.
- `MEASURED`: produced by the recorded experiment.
- `DERIVED`: calculated from confirmed source or measurements.
- `INFERRED`: reasoned but not explicitly stated by a source.
- `SPECULATIVE`: an unvalidated possibility.
- `PASS`, `FAIL`, `NOT RUN`, `BLOCKED`, and `EXCLUDED`: execution states.

A timeout, missing tool, failed sample, empty output, or unrecognized query plan
is not zero and not success. Do not turn missing evidence into a pass.

## Claim boundaries

Correctness, causal mechanism, wall-clock performance, allocation, tracked
memory, process memory, concurrency, deployment, and production readiness are
separate claims. Proving one does not imply another.

Every material claim identifies:

1. exact source, code SHA, or artifact hash;
2. command or observation that produced it;
3. environment and inputs;
4. applicable evidence label;
5. limitations, exclusions, failures, and unrun checks;
6. decision or gate the evidence supports.

## Preservation

- Raw evidence is append-only after it is cited.
- Retry under a new filename; never overwrite failed, partial, or completed runs.
- Preserve contradictory, ambiguous, and negative results.
- Never delete, skip, narrow, weaken, or relabel tests, workloads, exclusions,
  or samples to make a gate pass.
- Derived reports resolve to their raw inputs and analysis command.
- Source audits record retrieval date and distinguish source from interpretation.

## Reporting

Lead with the gate verdict, then evidence. Report commands, exit codes, test
counts, elapsed time, hashes, and artifact locations where relevant. State what
remains `NOT RUN` or `BLOCKED`; do not use readiness language prematurely.
