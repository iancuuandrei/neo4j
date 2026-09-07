# P2 workload and experiment design

Status: pre-candidate protocol

Date: 2026-09-07

Causal baseline: `f213380f812b820a1b312e2ea52cb3d8f1931ccc`

Current-source cross-check: upstream `2026.08` at
`736cad02a36bb4a0d32c1064f44768339c814269`

## 1. Question and evidence layers

P2 asks whether Neo4j should replace the exact-size `NodeState[S]`-like list used
for each level-local `(nodeId -> states)` entry.

No single benchmark layer can answer that question. The program has five
distinct evidence layers:

| Layer | Purpose | Can support upstream claim? |
| --- | --- | --- |
| collection microbenchmark | establish constant-factor crossover | no, mechanism only |
| exact direct PPBFS harness | independently control `S`, `k`, ID shape, depth, width | no, mechanism and falsification |
| planner-generated controlled queries | prove the shape can arise from Cypher/runtime planning | yes, with qualification |
| real external graphs | test materiality and regression risk | yes, with qualification |
| P1 interaction | test C1/C4 resource and latency interaction | yes, but only after independent P2 selection |

A result is not promoted from one layer to another. In particular, a fast bucket
microbenchmark is not an end-to-end PPBFS result, and an NFA with unreachable
padding is not a representative Cypher claim.

## 2. Independent variables

The controlled design varies:

```text
S       total NFA states
k       occupied states in one concrete level bucket
rho     k/S
shape   low-contiguous, high-contiguous, spread, seeded-random
d       BFS depth
w       graph width / number of buckets per level
b       graph degree
mode    unidirectional or bidirectional
hit     successful state-ID lookup fraction
g/i     state-ID lookups per full bucket iteration
```

The principal outcome dimensions remain separate:

```text
elapsed latency
CPU samples / affected CPU fraction
tracked query memory
allocation bytes
allocation count
GC behavior
correctness
implementation complexity
```

## 3. Workload roles are predeclared, then measured

Topology alone does not determine P2 behavior. A road graph with a four-state
NFA may be a negative or neutral P2 workload; a social graph with a 128-state
sparse NFA may still expose dense-array waste.

Each workload therefore has two labels:

```text
expected_role   declared before occupancy measurement
measured_role   frozen after baseline telemetry and before candidate timing
```

Expected roles are hypotheses. Measured roles use these gates:

### Positive

A workload is P2-positive when all apply:

```text
S >= 32
median k <= 4 or median rho <= 10%
iteration null-slot fraction >= 75%
concrete bucket count is large enough to accumulate material allocation
```

At least one of the following must also be true before making an end-to-end
performance claim:

```text
bucket-related baseline CPU fraction >= 5%
state-bucket structural allocation >= 10% of total allocation
state-bucket tracked structure materially moves the query-memory boundary
```

### Negative

A workload is deliberately adversarial when one or more apply:

```text
S <= 8
median or lookup-weighted rho >= 75%
high lookup-to-iteration ratio in a regime where compact lookup loses
candidate promotion occurs in most buckets
high fanout makes extra lookup/branch cost visible
```

### Neutral

A workload is neutral when the candidate should have little opportunity or
penalty:

```text
intermediate occupancy and modest bucket count
or
bucket management is <5% of baseline CPU and structurally immaterial
or
small S with low total allocation contribution
```

A measured role is frozen in the workload manifest before any P2 timing. A
workload whose measured shape contradicts its expected role is retained under
the measured role; it is not silently replaced.

## 4. Tier A: exact direct PPBFS mechanism matrix

Research class:

```text
P2StateBucketWorkloadTest
```

The graph is a shallow fan-out of disjoint equal-length chains. The NFA has:

```text
one start state
k active lane states matching each relationship
S-k-1 unreachable padding states
```

This yields a controlled target of `k` coexisting states per non-source graph
node while keeping depth shallow enough that P1 history scanning does not
dominate.

Default axes:

```text
S = 4, 8, 16, 32, 64, 128, 256
k = 1, 2, 3, 4, 6, 8, 16, 32, 64, 96, 127 where k < S
shape = low, high, spread, random
depth = 8
width = 64
seed = 0x5eed
mode = unidirectional
```

The default is a screening matrix. Confirmatory mechanism points add:

```text
S = 512
width = 256 and 1024
bidirectional fixtures
lookup-heavy fixtures
```

The harness writes an immutable CSV containing expected `S/k`, graph shape,
result count, path-entity count, and result hash. The telemetry JSONL is the
authority for observed bucket occupancy.

### Strength

This matrix isolates representation behavior and establishes exact crossover,
ID-distribution sensitivity, and worst-case dense behavior.

### Limitation

Unreachable padding is not evidence that Neo4j's planner emits the same NFA.
The direct matrix can falsify a representation but cannot establish real-query
prevalence.

## 5. Tier B: planner-generated controlled NFA families

Generator:

```text
research/ppbfs/p2/scripts/generate_cypher_nfa_queries.py
```

Every generated query uses `SHORTEST 2` so it cannot be accepted merely because
a `SHORTEST 1` rewrite executed another implementation. `PROFILE` remains
mandatory.

### B1. Tiny-plus control

Conceptual pattern:

```text
SHORTEST 2 (s)-[:R]->+(t)
```

Expected role: tiny-NFA negative or neutral.

This is close to the existing P1 road query and is a critical common-case
regression control.

### B2. Long QPP body

Conceptual pattern:

```text
SHORTEST 2 (s)
  ((q0)-[:R]->(q1)-[:R]-> ... -[:R]->(qB)){1, }
(t)
```

Body lengths:

```text
1, 3, 7, 15, 31, 63, 127
```

The long body is reachable and repeated. It is expected to produce a large NFA
while graph depth generally determines one position within the body, yielding
small per-level `k` on chain/road/DAG-like topology.

Expected role: positive sparse.

This expectation must be rejected if the planner rewrites the pattern, the
runtime NFA does not scale with body length, or telemetry shows material
co-occupancy.

### B3. Optional cascade

Conceptual pattern:

```text
SHORTEST 2 (s)
  ((a0)-[:R]->(b0)){0,1}
  ((a1)-[:R]->(b1)){0,1}
  ...
  ((x)-[:R]->(y)){1, }
(t)
```

Optional segment counts:

```text
1, 2, 4, 8, 16, 32, 64
```

Zero-length bypasses may put many automaton positions on the same graph node.
Expected role: dense negative or crossover.

This is intentionally a probe, not an assumed fact. It enters timing only if
telemetry confirms high/intermediate co-occupancy and semantic results remain
bounded.

### B4. Multi-type body

For gMark and Hetionet, long bodies cycle through real relationship types:

```text
P0, P1, P2, P3, ...
```

or a source-confirmed Hetionet schema sequence. Queries without reachable
source-target pairs are rejected before candidate timing using baseline-only
screening.

## 6. Tier C: controlled graph topologies

Use the same NFA families against several graph shapes so graph and automaton
causes do not collapse into one result.

### C1. Fan-out chains

```text
source -> W independent chains of depth D
```

- shallow history;
- exact bucket count scaling;
- low degree after the source;
- primary mechanism graph.

### C2. Layered sparse DAG

```text
W nodes/layer
fixed out-degree 2-4
D layers
one deterministic target
```

- many buckets without deep history;
- branch/reconvergence;
- tests whether same-level reconvergence changes `k`.

### C3. Grid

```text
N x N directed or bidirectional grid
```

- road-like locality with many equal-length routes;
- possible product-state reconvergence;
- useful bridge to SNAP roads.

### C4. High-fanout layered graph

```text
small depth
large degree
```

- deliberately dilutes storage gains with cursor/relationship work;
- stresses candidate lookup and branch overhead;
- negative end-to-end control.

### C5. Deep chain

Retain a limited deep-chain sweep only to measure P1 interaction:

```text
D = 64, 256, 1024, 4096
```

It is not the primary independent-P2 benchmark because B0 history scanning can
dominate the same run.

## 7. Tier D: external graph portfolio

Existing source archives, checksums, conversion scripts, and database stores
from P1 are reused without mutation. A P2 manifest records the exact reused
hashes.

### roadNet-PA

Characteristics: large sparse road graph, low degree, high diameter.

Workloads:

1. existing tiny-plus `SHORTEST 2` query — expected P2 neutral/negative and P1
   depth-sensitive control;
2. long-body QPP family — expected positive if `S` scales and `k` remains tiny;
3. optional cascade screening — expected crossover/negative only if measured;
4. deterministic distance strata and P1 source-target pairs for continuity.

### roadNet-CA

Same families at larger scale. It is the principal independent replication and
later P1+P2 interaction dataset.

### web-Stanford

Directed, reconvergent web topology.

Expected use:

- long-body sparse candidate validation;
- optional-cascade/intermediate occupancy search;
- regression test where reconvergence may increase `k`;
- a bridge between roads and social graphs.

No role is frozen from topology alone.

### cit-Patents

Directed DAG-like citation graph.

Expected use:

- large external allocation validation;
- sparse long-body queries;
- moderate reconvergence control;
- deterministic reachable-pair strata.

The existing P1 result was practically non-inferior but statistically broad;
P2 uses fresh qualification rather than inheriting that classification.

### LiveJournal

Shallow, high-fanout social graph.

Expected use:

- strongest external regression detector;
- tiny-NFA common-case control;
- optional-cascade stress if resource-feasible;
- high lookup/cursor-work environment in which sparse indirection may lose.

The prior P1 result was inconclusive and is not called neutral. P2 increases
fork count or reports the same uncertainty honestly.

### as-Skitter

Low-diameter, high-fanout Internet topology.

Expected use:

- negative or neutral control;
- candidate overhead under shallow traversal;
- test where state-bucket optimization may be immaterial to total query cost.

### Hetionet v1

Heterogeneous semantic graph with native labels and 24 relationship types.

Expected use:

- retain the qualified H3 workload as a realistic small-NFA neutral control;
- generate source-confirmed multi-type bodies for larger NFA probes;
- reject any query that does not preserve native schema semantics or execute
  `StatefulShortestPath`.

The previous global-node occupancy result (`S=4`) is not reused as level-bucket
P2 evidence.

### gMark

The prior generated instance has approximately 27,000 nodes, 36,000
relationships, four predicate types, and shallow reachable diameter. Its
shallowness made it weak for P1's deep-history question but does **not** make it
weak for P2.

P2 roles:

- generate regex/NFA structural families;
- separate graph topology from automaton structure;
- exercise sparse, optional, alternative, and reconvergent expressions;
- retain only translated queries whose measured runtime NFA and bucket regime
  match the declared family.

A second gMark configuration should deliberately increase predicate diversity,
branching, and reachable path length. The original instance remains a frozen
replication/control rather than being overwritten.

### FinBench

Official path workloads previously failed to execute `StatefulShortestPath`.
They remain a plan-qualification control and do not count as direct P2 evidence.

Derived GPM queries over the FinBench dataset may be explored, but they must be
labelled `derived`, not `official FinBench`, and cannot be presented as standard
benchmark results.

### LDBC SNB

Screen SF1 first, then SF10 only if import and resource cost are proportionate.
Use it as an additional property-graph regression suite, not as a mandatory
success condition. A workload counts only after operator and P2-codepath
qualification.

## 8. Source-target pair selection

Pair selection is frozen before candidate execution.

### Rules

1. Select pairs on B0 only.
2. Use deterministic seeds recorded in the manifest.
3. Preserve all selected pairs, including slow or unfavorable ones, unless a
   predeclared resource gate excludes the whole workload family.
4. Never select or reject a pair using P2 timing.
5. Record source/target degree, reachable distance, result count, and result
   hash.

### Strata

Where topology permits, use:

```text
short:   distance 2-5
medium:  distance 8-32
long:    distance 64-256
deep:    >256, only where resource-feasible
```

Each retained dataset/query family should have at least five deterministic
pairs across applicable strata. Confirmatory positive/negative comparisons use
at least ten fork/pair blocks or ten independent JVM forks at a fixed pair,
depending on the existing harness.

For long QPP bodies, choose reachable pairs whose path lengths satisfy the body
semantics. If modulo constraints make selection unstable, treat pair generation
as part of the query-family qualification and record the exact baseline search
procedure.

## 9. Baseline characterization wave

Run the instrumented baseline only. No P2 candidate exists yet.

For every query/pair record:

```text
operator qualification
S
concrete bucket count
k histogram
rho thresholds
ID span/runs/chunks
writes and insertion order
level probes and actual map gets
bucket lookups and hit rate by role
full iterations and null-slot fraction
result hash/path lengths
search mode and depths
```

Run at least three deterministic pair/seed replicates for each family before
freezing measured roles. Instrumented execution uses generous memory and is not
timed.

### Characterization acceptance

A workload enters candidate timing only when:

```text
StatefulShortestPath or direct PPBFS harness is confirmed
unknownBucketOperations == 0
result oracle passes
telemetry row validates
resource consumption is bounded
measured role is frozen
```

## 10. Baseline CPU/materiality wave

Use an instrumentation-free B0 build with JFR on one representative positive,
neutral, and negative workload.

Attribute samples/allocation to:

```text
FoundNodes.addToBuffer / get
HeapTrackingArrayList allocation and iteration
BFSExpander state compaction
BFSExpander direct bucket lookup
ProductGraphTraversalCursor
relationship cursor/cache
GC
```

Estimate the P2-addressable fraction `p` and apply the Amdahl bound documented
in `COST_MODEL.md`.

If `p < 5%` on all realistic workloads and structural memory is immaterial,
latency optimization work stops unless memory evidence independently justifies
P2.

## 11. Candidate screening order

After baseline characterization:

1. dense B0 control;
2. final concrete inline-to-dense bucket with thresholds selected from measured
   `k` and microbenchmarks;
3. sorted compact candidate only if `k=3..16` and ordered insertions are
   material;
4. existing Neo4j int-object hash map as a control;
5. segmented candidate only if chunk metrics show locality;
6. bitmap-packed candidate only if a substantial intermediate regime remains.

Do not build the full cross-product. A candidate eliminated by structural memory
and microbenchmark gates does not receive an end-to-end branch.

## 12. Candidate timing waves

### Screening

```text
3 fresh paired JVM forks
representative positive, neutral, negative points
randomized B0/candidate order
```

A candidate advances only if:

- correctness is exact;
- positive mechanism points improve in the predicted direction;
- no negative/common-case point shows a reproducible >5% regression;
- allocation/tracked-memory behavior matches the implementation model.

### Confirmation

```text
10 fresh paired JVM forks
paired log-ratio analysis
95% confidence interval
90% TOST-style practical-equivalence interval
predeclared +/-5% latency band
```

For very stable controlled points, fewer forks require a written variance-based
justification. Raw samples remain immutable.

## 13. Memory and allocation wave

For the winning P2 candidate only:

```text
MemoryTracker structural deltas
JFR allocation bytes and counts
GC collections and pause contribution
configured query-memory boundary
```

Boundary testing uses a predeclared grid/binary search and reports the minimum
observed passing configuration, not resident memory.

Repeat at:

- one strong sparse positive;
- one crossover;
- one dense negative;
- roadNet-PA and roadNet-CA;
- one high-fanout external control.

## 14. `statesList` follow-up

The first P2 candidate changes state-bucket storage only. It retains the current
`statesList` construction so storage causality remains interpretable.

Only after a winning storage representation exists, test:

```text
P2 storage
vs
P2 storage + direct active-state exposure / statesList simplification
```

This follow-up is not allowed to rescue a storage candidate that fails on its
own.

## 15. P1 interaction wave

Use the exact frozen P1 candidates:

```text
C1 always-on canonical index
C4 deferred retired index
```

Compare only:

```text
B0
P2-best
C1
C1 + P2-best
C4
C4 + P2-best
```

Primary questions:

1. Does P2 recover C1 allocation/query-memory overhead?
2. Does compact frontier storage preserve C1's direct-lookup speedup?
3. Does C4 merge become cheaper or more expensive?
4. Is one universal bucket adequate, or is frontier/canonical specialization
   justified?

Do not retune the independent P2 threshold using one favorable P1 workload.
Any role-specialized P1+P2 design is a separate candidate with its own gates.

## 16. Statistical definitions

For elapsed time, define:

```text
ratio = B0 / candidate
```

so values above one mean the candidate is faster.

Report:

```text
fork medians
geometric mean paired ratio
95% confidence interval
90% practical-equivalence interval
coefficient of variation where useful
all raw samples
```

Classification:

```text
faster       CI establishes improvement and practical size is meaningful
equivalent   90% interval lies wholly within [0.95, 1.05]
slower       CI establishes regression and practical size is meaningful
inconclusive neither superiority, regression, nor equivalence established
```

Never call an interval containing one neutral without an equivalence test.

## 17. Resource gates and exclusions

Resource exclusions are set with B0 before candidate timing:

- maximum wall time per fork;
- maximum query-memory configuration;
- maximum database/import footprint;
- minimum successful result/operator qualification rate.

If a family exceeds a gate, exclude the full predeclared family or reduce it
using a B0-only rule. Do not retain only candidate-favorable subsets.

Known prior exclusions remain explicit:

- official FinBench path queries: wrong operator;
- old gMark instance: too shallow for P1, but still eligible for P2;
- invalid concurrent-server LiveJournal runs: never reused;
- startup failures below 2 MiB: infrastructure, not query-memory evidence.

## 18. Reproducibility record

Every row records:

```text
workload_id
dataset and source SHA-256
database-store hash/version
query file and SHA-256
source and target IDs
pair-selection seed
expected and measured role
S and occupancy summary
operator plan hash
result hash
search mode and depth
Neo4j source SHA
candidate source SHA
JDK and JVM flags
Neo4j configuration
memory limit
fork and repetition index
start/end timestamps
raw artifact paths and hashes
```

## 19. Execution sequence

```text
A. validate instrumentation tests
B. run exact synthetic occupancy matrix
C. generate and PROFILE planner NFA probes
D. run external baseline telemetry
E. freeze measured roles and workload manifest
F. run B0 JFR/materiality profiles
G. run memory probe and collection microbenchmarks
H. authorize candidate shortlist
I. implement and screen P2 candidates
J. select and clean P2 winner
K. confirm external timing/memory/falsification
L. combine winner with C1 and C4
M. extract final clean candidate and rerun retained suite
N. prepare maintainer handoff only if gates pass
```

No candidate implementation precedes step H.