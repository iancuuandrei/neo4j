# WITHHELD — proposed pull-request description

Status: `DO NOT OPEN`. No candidate passed the required memory gate, so no
production diff should be extracted to `contrib/ppbfs-direct-state-index`.

## Why

Not applicable: although the baseline lookup is measurably expensive on deep
roadNet-PA paths, every direct canonical representation tested crosses a query
memory boundary that unchanged upstream passes.

## Selected change and invariant

Selected change: none. Required identity remains exactly one `NodeState` per
`(nodeId,stateId)` within a PPBFS execution.

## Before/after evidence

| Design | Deep roadNet-PA timing | Minimum observed d250 limit |
| --- | --- | ---: |
| B0 unchanged | reference | 92 MiB |
| C1 dense node-major | 4.373x, 95% CI `[2.126x,8.993x]` | 93 MiB |
| C2 state-major maps | 85.6% C1 deep retention in screening | 94 MiB |
| C3 adaptive node-major | not timed after fail-fast gate | greater than 92 MiB |

## Tests and rollback

Experimental candidates passed focused identity, bidirectional, lifecycle,
interruption, and generated differential tests. Rollback is B0, which is the
retained state; there is no patch, data migration, configuration, or public API
to revert.

## Limitation

Only one real topology received formal timing, common-case uncertainty remains,
and later allocation/GC and multi-topology work was intentionally not run after
the mandatory memory failure. These limitations prohibit, rather than weaken,
an upstream PR.
