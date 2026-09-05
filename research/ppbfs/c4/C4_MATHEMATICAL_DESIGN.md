# C4-DCRI mathematical design

## Status and objective

Status: accepted for experiment, pre-implementation, 2026-09-05. C4-DCRI asks whether PPBFS can remain structurally identical to B0 for shallow searches, then bound history-dependent lookup by freezing an early prefix and transferring later retired buckets into one direct index. It is a research hypothesis, not an upstream design decision.

Goals are: preserve exact PPBFS semantics; avoid historical backfill; avoid a second canonical dense bucket for newly reached nodes; make lookup independent of final depth after activation; approach B0 shallow resource behavior; retain at least roughly 85–90% of C1's logarithmic deep improvement if evidence permits. Non-goals are C1 modification, a custom pair table, singleton/dense promotion, sharding without a measured rehash problem, and an upstream PR.

## State and transition model

Before activation, state is exactly B0:

```text
buffer + forward frontier + optional backward frontier + history[level]
```

Activation is one-way and occurs at a `commitBuffer` retirement boundary. Existing history becomes the immutable logical `frozenHistory`; no entry or bucket is copied. The retiring active frontier is detached. If the retired index is absent, that whole outer map becomes `retiredIndex`; otherwise its buckets are transferred or merged into the existing index. The new buffer becomes the active directional frontier. After activation, no frontier is appended to history.

Post-activation lookup order is:

```text
buffer -> forward frontier -> optional backward frontier -> retiredIndex -> frozenHistory newest-to-oldest
```

This order preserves active visibility and bounded access to pre-activation identities. It does not alias active and retired scheduling storage.

## Correctness argument

Before activation, behavior is observationally B0. Activation changes only ownership of a frontier that has finished expansion. Because no active map aliases the retired index, future buffer inserts cannot enlarge the expansion set. A first-seen retired node transfers its existing dense bucket unchanged, preserving every object reference and allocating no canonical bucket. A repeated node merges only empty slots; a non-empty collision is legal only for the identical `NodeState` object. Frozen history remains intact and lookup-visible. Induction over retirements therefore preserves one exact object per product-state key and the same frontier schedule.

Direction is not part of the repository key. Bidirectional rediscovery must return the same object from active, retired, or frozen storage and then update direction-specific discovery metadata on that object. Closing the scoped tracker remains the authoritative whole-repository cleanup; explicit close operations are needed only when a retired outer map or merged incoming bucket no longer owns retained data.

## Lookup complexity

Let final BFS depth be `D`, activation history size `H0`, total lookups `X = X0 + X1`, and pre/post-activation historical probes `P0` and `P1`. B0 lookup bookkeeping is

```text
T_B0 = Theta(X + sum(probes_i))
```

and is `Theta(D^2)` on the controlled chain. C1 expected lookup is `Theta(X)` with an always-on canonical repository.

For C4, pre-activation work is B0 work over a bounded prefix. Each post-activation lookup performs a constant number of active/index probes and at most `H0` frozen-history probes:

```text
T_C4 = T_pre + Theta(X1 * (1 + H0))
```

If the trigger bounds `H0` independently of final `D`, then `T_C4 = Theta(X)` as `D` grows. C4 need not match C1's constant; it must remove dependence on unbounded final history.

## Retained-memory model

Let `B0p` be buckets in the frozen prefix, `R` transferred retired buckets, `A` active buckets, and `I` retired-index outer capacity. Then

```text
M_C4 ~= M(B0p) + M(R) + M(A) + M(I)
```

The inner storage in `R` was already allocated for B0 frontier scheduling and would otherwise be retained in history. C4 adds principally the coalesced outer-index growth and merge work, not one new dense canonical bucket per reached node. The first whole-map transfer can eliminate even the first new outer index. This hypothesis must be tested using fixed limits, tracked operator memory, JFR allocation, transfer/merge counters, and outer-map growth evidence.

## Activation calibration

Production activation may use only deterministic structural counters available at level boundaries: history depth `H`, lookups in the retired expansion level `L`, and retiring node buckets `B`. No per-lookup timer is allowed.

Stage A measures history hit/miss probes, direct lookup, whole-map transfer, and repeated-node merge over `|Q| = 1,2,4,8,16,32,64` and representative map sizes. Stage B evaluates the simplest threshold supported by those measurements. A fixed `H >= H*` is preferred if it is indistinguishable from a cost expression such as

```text
L * (H*c_history - c_direct) > B*c_transfer*safetyFactor
```

Calibration selected the fixed rule `H >= 8` when a non-empty frontier retires. This freezes exactly eight pre-activation history levels, activates exactly once, never deactivates, avoids activation on depth-1–3 controls, and activates early relative to deep road queries. Threshold alternatives are calibration evidence, not a candidate zoo. See `C4_MICROBENCHMARK.md` for the measurements and limitations.

## Performance metrics and decision rule

For `S1 = B0/C1` and `S4 = B0/C4`, report raw retention `S4/S1` and, when both exceed one, logarithmic retention `ln(S4)/ln(S1)`. Formal timing uses fresh B0/C1/C4 distributions, balanced seeded ordering, medians within fork, paired log comparisons, two-sided 95% intervals, and 90% practical intervals with a +/-5% margin for controls.

C4 wins only if it materially improves shallow/memory/allocation behavior while preserving most deep logarithmic gain and all correctness. If it fixes resources but leaves C1 and C4 non-dominated, classify partial. If deep performance collapses or resources do not improve, reject it. Ambiguous intervals remain inconclusive.

## Failure behavior and rollback

Activation has no runtime rollback: deactivation would complicate ownership and is forbidden. Any invariant violation fails fast in tests rather than replacing identity. The experimental branch can be abandoned by retaining its commits and selecting B0 or C1; no store format, public API, migration, security, or privacy behavior changes. C4 diagnostics are research-only and must not enter a clean contribution branch.

## Validation contract

Before formal timing: focused ownership/identity/lifecycle tests, generated differential PPBFS tests, bidirectional and path-mode coverage, interruption and cleanup, Spotless, and the complete `community/cypher/runtime-util` suite with zero failures/errors. Formal evidence then reruns the full frozen benchmark matrix, fixed-memory boundaries, activation diagnostics, and representative JFR. Large/rebuildable artifacts and all append-only raw runs remain under `D:/dev/neo4j-research`.
