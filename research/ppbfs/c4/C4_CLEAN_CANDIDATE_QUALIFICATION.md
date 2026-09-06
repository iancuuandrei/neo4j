# Clean C4 candidate qualification

The exact clean contribution commit
`7411ac3853c3408466725e54bca7379c5f8f8f0d` was built and tested without the
research hooks, counters, or diagnostic plumbing.

## Tests and build

| Gate | Result |
| --- | --- |
| focused C4 plus PPBFS suites | PASS, 114 tests, 0 failures/errors, 5 existing skips |
| complete `runtime-util` suite | PASS, 529 tests, 0 failures/errors, 5 existing skips |
| Community distribution reactor | PASS, 129 modules, 19:27 |
| formatting/license checks | PASS as part of the Maven lifecycle |

Environment: Windows 11, Java 21, Maven Wrapper-compatible Maven invocation.
The exact environment strings are retained in per-fork metadata.

## Exact-clean timing confirmation

`MEASURED`: two paired, independently restarted JVM forks, one warmup and three
timed executions. This is a qualification subset, not a replacement for the
complete synchronized study.

| Workload | B0 / clean C4 | 95% CI | Result equality |
| --- | ---: | ---: | --- |
| roadNet-PA d772 | 7.589x | [5.202, 11.072] | yes |
| roadNet-CA d800 | 7.267x | [1.041, 50.719] | yes |
| LiveJournal d3, `SHORTEST 2` | 1.008x | [0.229, 4.430] | yes |
| Hetionet H3 d6 | 0.874x | [0.178, 4.291] | yes |
| as-Skitter d2 | 0.999x | [0.137, 7.298] | yes |

The wide two-fork control intervals are expected and are not promoted as new
generality claims. Their point estimates and equality checks show no material
extraction discrepancy. The complete 5-10 fork research evidence remains
authoritative.

## Controlled depth-4096 confirmation

The instrumented B0 and research-C4 harnesses were rerun at depth 4096. Both
returned an 8,193-entity path from 4,097 lookup attempts. B0 performed
8,382,465 historical probes; C4 performed 32,724 total historical probes,
including 32,688 post-activation frozen-prefix probes, and allocated zero
canonical buckets after activation. Median warmed latency
in this local five-repetition check was 163.59 ms for B0 and 63.19 ms for C4.

Raw artifacts are under `$PPBFS_ARTIFACTS/runs/clean-c4-qualification-v1/`.
