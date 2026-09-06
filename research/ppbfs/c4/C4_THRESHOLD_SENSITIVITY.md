# C4 threshold sensitivity

## Decision

`MEASURED`: H=4, H=8, and H=16 returned identical result-length sets in all
45 timed executions. On the two discriminating deep-road cases, point estimates
between thresholds differed by at most 5.7%; every two-sided 95% interval
included parity except the deliberately weaker practical classification for
H=8 versus H=4 on roadNet-CA. The three shallow controls were too noisy at three
forks to establish equivalence or a threshold-specific regression.

H=8 is retained as a conservative fixed boundary in a broad measured region.
It is not claimed to be optimal.

| Workload | H=8 / H=4 | 95% CI | H=8 / H=16 | 95% CI |
| --- | ---: | ---: | ---: | ---: |
| roadNet-PA d772 | 1.057x | [0.897, 1.246] | 0.992x | [0.928, 1.061] |
| roadNet-CA d800 | 1.049x | [0.998, 1.103] | 0.964x | [0.907, 1.025] |
| LiveJournal d3 | 0.829x | [0.611, 1.126] | 0.857x | [0.556, 1.322] |
| Hetionet H3 d6 | 1.011x | [0.695, 1.472] | 1.121x | [0.636, 1.973] |
| as-Skitter d2 | 0.930x | [0.482, 1.792] | 0.849x | [0.168, 4.299] |

The shallow rows are single-query, millisecond-scale controls. Their broad
intervals are disclosed; the completed 5-10 fork research matrix remains the
authoritative common-case evidence.

## Protocol and provenance

- three seeded, independently restarted JVM forks per threshold;
- one warmup and three timed executions per fork;
- identical imported store, JDK, configuration, and manifest within a workload;
- H=4: `25cddd01de077f23c8a597c955bc77c5853d3be6`;
- H=8 clean candidate: `7411ac3853c3408466725e54bca7379c5f8f8f0d`;
- H=16: `06fc35a16aed9814e508627809cba52f5eb757a5`.

The original run protocols contain the mistyped declarative H=16 SHA
`06fc35a16ae0e7a1fd66c128b29e156f665a51ed`. The immutable tested-JAR hash is
`6877EAD961E934BA8F40E5C98BA6BFEC03FBD8EF7699DA5DBA7B0F0C73457743`; an
append-only correction record beside the raw runs binds it to the verified
commit above. No timing data was changed.

Raw CSV, metadata, logs, protocols, and analyses are append-only under
`$PPBFS_ARTIFACTS/runs/threshold-sensitivity-v1/`.
