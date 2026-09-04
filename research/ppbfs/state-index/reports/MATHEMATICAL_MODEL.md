# PPBFS product-state lookup mathematical model

Evidence labels distinguish SOURCE-CONFIRMED implementation facts, DERIVED
consequences, and assumptions that measurements must validate.

## Definitions

Let the data graph be `G=(V,E)` and the NFA be `A=(Q,Sigma,delta,q0,F)`. A
discovered product state is `(v,q) in V x Q`.

- `U`: unique discovered product states / `NodeState` objects.
- `X`: calls to `FoundNodes.get`, i.e. accepted product-state encounter attempts.
- `D`: maximum combined expansion depth.
- `H_t`: historical level-map count at time `t`.
- `h_i`: historical maps examined by lookup `i`.
- `a_i`: historical-hit age, zero for the newest historical map.
- `x_d`: encounters made at approximate history depth `d`.
- `N`: distinct reached data-node IDs represented in product states.
- `B`: data-node buckets across history, frontier(s), and buffer.
- `|Q|`: NFA-state count.
- `rho=U/(N|Q|)`: global state occupancy.

## Current lookup

SOURCE-CONFIRMED: baseline lookup performs a constant number of active-structure
probes and then scans history newest-to-oldest.

DERIVED under expected constant-time hash lookup:

`T_i = Theta(1+h_i)` and `T_current = Theta(X + sum_i h_i)`.

A miss at history size `H` costs `Theta(1+H)`. An active hit costs expected
`Theta(1)`. A historical hit of age `a` costs expected `Theta(1+a)`.

For a chain with one compatible repeatable state and one new encounter at each
depth, `x_d=Theta(1)` and average probes are `Theta(d)`, so the lookup component
sums to `Theta(D^2)`. This is not a total-PPBFS complexity claim: cursor
evaluation, signposts, propagation, tracing, and result enumeration remain
separate terms and may dominate.

Reconvergent graphs can also create old hits. Their cost depends on hit-age
distribution rather than only maximum depth, so instrumentation must retain
history-probe and hit-age distributions.

## Direct repository

For a primitive-key direct repository `I:(nodeId,stateId)->NodeState`, expected
lookup is `Theta(1)` and total direct lookup bookkeeping is expected `Theta(X)`.
This statement does not bound total PPBFS execution.

An adaptive repository activated after prior discovery costs
`B_build + Theta(X_post)`. Dense backfill scans `Theta(B_a|Q|)` slots, which can
materially exceed `U_a` under sparse occupancy. An occupancy-aware sparse source
can approach `Theta(U_a+B_a)` logical work but has larger implementation constants
and review risk.

## Structural memory

Ignoring headers, capacity slack, and load factors:

- current dense level buckets: `Theta(B|Q|)` references;
- duplicate dense side index: current memory plus `Theta(N|Q|)` references and a global outer map;
- sparse direct index: logical `Theta(U+N)` with representation-dependent constants;
- canonical dense repository plus active frontier(s)/buffer: approximately `Theta(N|Q|)` canonical references plus active structural duplication.

The tested JVM's compressed-reference mode and actual heap-tracker/JOL estimates
must replace any assumed byte-per-reference value.

## Adaptive break-even

Let measured historical lookup cost be `L(h)=alpha+beta*h`, direct lookup cost be
`gamma`, build cost be `B(U,B,|Q|)`, and expected future encounters be `R`.
Activation can amortize only when:

`R * (E[L(h)] - gamma) > B(U,B,|Q|)`.

If the denominator is positive, `R_break_even = B / (E[L(h)]-gamma)`. Prior probe
debt is sunk cost and is only a predictor. Thresholds, safety factors, fanout
estimators, sparse/dense crossover, and coefficients remain SPECULATIVE until
measured.

## Counterexamples and rejection conditions

- Low-depth/high-fanout traversal: direct insertion and global resizing may cost more than shallow history scans.
- Early termination: adaptive backfill may never amortize.
- Very sparse large NFA: dense canonical buckets may fail memory gates.
- Enumeration-heavy query: lookup improvement may be immaterial end-to-end.
- Cursor/predicate-heavy query: storage/predicate work may dominate.
- Query planned to another operator: it provides no direct PPBFS evidence.

The source comment alone does not establish materiality. End-to-end latency must
correlate with measured probe counts and profiles before a production candidate
can graduate.
