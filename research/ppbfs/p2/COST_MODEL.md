# P2 state-bucket cost model

Status: pre-candidate analytical model

Date: 2026-09-07

Evidence labels:

- source formulas: `SOURCE-CONFIRMED` at Neo4j
  `f213380f812b820a1b312e2ea52cb3d8f1931ccc`;
- algebraic consequences: `DERIVED`;
- illustrative HotSpot values: `SPECULATIVE` until measured on the benchmark JVM;
- runtime constants and crossover coefficients: `NOT RUN`.

## 1. Variables

For a concrete level-owned state bucket:

```text
S  = total NFA state count
k  = occupied states in this bucket
rho = k / S

gs = successful state-ID lookups
gf = failed state-ID lookups
g  = gs + gf
i  = complete active-state iterations
w  = distinct writes
wd = duplicate writes
m  = merges (zero for independent baseline P2; relevant to C4)

r  = JVM object-reference size
A  = aligned array-header size
alpha = JVM object alignment
H_L = shallow size of HeapTrackingArrayList
H_B(t) = shallow size of a candidate bucket with t inline slots
H_H = shallow size of HeapTrackingIntObjectHashMap
```

All aligned-array formulas use:

```text
arr_ref(n) = align_alpha(A + r*n)
arr_int(n) = align_alpha(A + 4*n)
arr_long(n) = align_alpha(A + 8*n)
```

`HeapEstimator` determines `r`, `A`, and `alpha` from the active JVM. On a
64-bit JVM, `r` is 4 bytes with compressed ordinary object pointers and 8 bytes
otherwise. No final crossover may assume compressed pointers without recording
the benchmark JVM values.

## 2. Baseline dense bucket

`FoundNodes` creates a bucket with:

```java
HeapTrackingArrayList.newEmptyArrayList(S, memoryTracker)
```

The source allocates an object array of capacity `S`, sets the logical list size
to `S`, and charges:

```text
tracked_dense(S) = H_L + arr_ref(S) + S
actual_dense(S)  = H_L + arr_ref(S)
```

The extra `S` in the tracked formula is source-confirmed behavior of
`newEmptyArrayList`: it passes `exactSize` as `initialTrackedSize`. It is not a
second JVM array and must not be described as resident heap.

Operation model:

```text
lookup successful or failed: O(1), one bounds check + array load
write:                       O(1), one bounds check + array store
full iteration:              O(S)
merge:                       O(S)
```

One expanded frontier bucket is scanned across all `S` slots, after which only
its `k` active states are copied into `statesList`.

## 3. Illustrative dense sizes

The following is an explanatory calculation only. It assumes typical 64-bit
HotSpot values:

```text
r = 4 bytes
A = 16 bytes
alpha = 8 bytes
H_L = 40 bytes
```

The first three constants are runtime-dependent. `H_L=40` is a layout estimate,
not retained evidence, until the benchmark JVM reports
`HeapEstimator.shallowSizeOfInstance(HeapTrackingArrayList.class)`.

| S | object array | estimated actual structure | Neo4j tracked structure |
| ---: | ---: | ---: | ---: |
| 4 | 32 B | 72 B | 76 B |
| 8 | 48 B | 88 B | 96 B |
| 16 | 80 B | 120 B | 136 B |
| 32 | 144 B | 184 B | 216 B |
| 64 | 272 B | 312 B | 376 B |
| 128 | 528 B | 568 B | 696 B |
| 256 | 1,040 B | 1,080 B | 1,336 B |
| 512 | 2,064 B | 2,104 B | 2,616 B |

The important scaling fact does not depend on the illustrative shallow size:
for fixed `k`, the questioned part grows linearly with `S`.

## 4. Candidate S1: compact unsorted references

A compact vector can recover each key from:

```java
nodeState.state().id()
```

so it need not allocate a parallel `int[]`.

With backing capacity `c >= k`:

```text
actual_vector(c)  = H_B + arr_ref(c)
tracked_vector(c) = H_B + arr_ref(c)
```

A design with references stored directly in the bucket object has:

```text
actual_inline(t) = tracked_inline(t) = H_B(t)
```

until promotion.

Expected lookup comparisons for uniformly distributed successful targets and
failed targets which scan the full vector:

```text
E_cmp(k, h) = h*(k + 1)/2 + (1 - h)*k
            = k - h*(k - 1)/2

h = gs / g
```

Operation model:

```text
lookup:     O(k), E_cmp comparisons
iteration:  O(k)
append:     O(1) amortized if uniqueness is already established
safe put:   O(k) if the bucket itself enforces uniqueness
merge:      O(k1*k2) without sorting/indexing
```

For `k <= 2` or `k <= 4`, a linear scan can be cheaper than hashing or binary
search. That is a hypothesis to benchmark, not an asymptotic claim.

## 5. Candidate S2: sorted compact references

State IDs can still be read from each `NodeState`, avoiding a parallel key
array. The vector remains sorted by `state().id()`.

```text
memory:     H_B + arr_ref(c)
lookup:     O(log k)
iteration:  O(k)
insert:     O(k) reference shifts
merge:      O(k1 + k2)
```

If instrumentation shows insertions are overwhelmingly nondecreasing, the
practical insert cost approaches append cost. If state IDs are not ordered, the
shift cost and cache traffic become material.

## 6. Candidate S3: int-to-object hash map

The existing `HeapTrackingIntObjectHashMap` starts with tracked capacity 16 and
charges one `int[]` plus one reference array:

```text
actual_hash_initial >= H_H + arr_int(16) + arr_ref(16)
tracked_hash_initial = H_H + arr_int(16) + arr_ref(16)
```

Under the illustrative compressed-reference JVM, the two backing arrays alone
are:

```text
80 B + 80 B = 160 B
```

before the map object itself. This makes the existing map an important
constant-time control but an implausible default for singleton and two-state
buckets. It can become competitive only when `S` is sufficiently large or `k`
exceeds the inline/vector regime.

Operation model:

```text
lookup:     expected O(1)
iteration:  O(table capacity), not O(k) in the strict sense
insert:     expected O(1), with resize spikes
merge:      expected O(k_incoming)
```

The candidate must measure table capacity, rehash allocation, branch behavior,
and failed lookup cost. A generic boxed `HashMap<Integer, NodeState>` is outside
the candidate set.

## 7. Candidate S4: bitmap plus packed references

Let:

```text
W = ceil(S / 64)
```

A basic representation costs:

```text
M_bitmap(S, c) = H_B + arr_long(W) + arr_ref(c)
```

For `S <= 64`, lookup can use one membership test and one `Long.bitCount` rank:

```text
rank(id) = popcount(bitmap & ((1L << id) - 1))
```

Special handling is required for `id=64` boundaries. For `S > 64`, naive rank
cost is `O(W)` unless prefix counts or chunk-local indexing are added. Packed
insertion also shifts references after the insertion rank.

This candidate is justified only if baseline data show a substantial
intermediate-occupancy regime for which inline vectors have excessive lookup
cost and dense arrays still waste material space.

## 8. Candidate S5: segmented dense chunks

For chunk width `B`:

```text
C = ceil(S / B)           top-level chunk count
q = occupied chunks

M_chunked(S, B, q) = H_B + arr_ref(C) + q*arr_ref(B)
```

Operation model:

```text
lookup:     O(1), two array accesses
iteration:  O(C + q*B)
write:      O(1), with one chunk allocation on first use
merge:      O(C + occupied slots in allocated chunks)
```

This candidate is attractive only when active state IDs cluster into few
chunks. The instrumentation records occupied 8-, 16-, and 32-state chunks to
make that decision before implementation.

Approximate memory break-even against dense, ignoring the similar bucket/list
shallow terms:

```text
arr_ref(C) + q*arr_ref(B) < arr_ref(S)
```

For scattered singleton states, `q` approaches `k`; for contiguous states,
`q` approaches `ceil(k/B)`.

## 9. Candidate A: one-way inline-to-dense bucket

A final concrete adaptive class can store `t` references directly in object
fields and allocate a raw `NodeState[S]` only on the `(t+1)`-th distinct state.
It should not wrap a `HeapTrackingArrayList` after promotion: doing so would
retain the adaptive wrapper and add the full list object, reproducing the extra
wrapper cost observed in the earlier P1 C3 experiment.

Conceptual memory:

```text
k <= t:
    M_adaptive = H_B(t)

k > t:
    M_adaptive = H_B(t) + arr_ref(S)
```

A carefully laid-out bucket with two or four inline references may have a
shallow size close to `H_L`; therefore dense-mode overhead can be near zero
while sparse mode avoids the backing array. This must be verified with
`HeapEstimator` and JOL/JAMM on the exact JVM.

One-way promotion cost:

```text
C_promote(S, t) = allocate arr_ref(S) + copy t references + clear inline fields
```

The transient allocation peak and tracked-memory sequence must be measured.

## 10. Candidate T: tagged singleton map value

If singleton buckets dominate overwhelmingly, the outer long-object map could
store a `NodeState` directly and promote its value to a bucket only on the
second state:

```text
map value = NodeState | StateBucket
```

This can remove even the sparse wrapper allocation for `k=1`. It also adds type
tests/casts to every map hit, complicates generic types, hooks, iteration,
merging, and ownership, and makes the production diff harder to review.

It is not a first-round candidate. It becomes eligible only if:

```text
fraction(k=1) >= 90%
```

on representative, lookup-weighted, and iteration-weighted workload views, and
the simpler inline bucket leaves material memory on the table.

## 11. CPU objective

For one bucket, separate map lookup from bucket representation because P2 does
not alter the outer long-object map.

Dense:

```text
C_D =
    g*c_direct
  + i*(S*c_scan_slot + k*c_emit)
  + w*c_store
```

Unsorted compact:

```text
C_V =
    gs*c_compare*(k + 1)/2
  + gf*c_compare*k
  + i*k*c_emit_compact
  + w*c_append_or_put
  + p*C_promote(S,t)
```

where `p` is one if the bucket promotes and zero otherwise.

The compact representation has an expected CPU advantage when:

```text
C_V < C_D
```

or, isolating the principal trade-off:

```text
i*c_scan_slot*(S-k)
>
g*(c_compare*E_cmp(k,h) - c_direct)
 + write_delta
 + promotion_delta
 + iteration_emit_delta
```

This equation explains why occupancy alone is insufficient. A sparse bucket
with hundreds of state-ID lookups may prefer dense access, while a sparse bucket
with one iteration and almost no direct lookup may prefer compact storage.

## 12. Role-dependent operation mix

Independent B0/P2 roles:

```text
frontierBuffer:
    writes + recursive encounter lookups

frontier:
    one full iteration + expansion lookups + recursive encounter lookups

history:
    recursive encounter lookups only
```

P1 changes this mix:

```text
C1 allStates:
    canonical lookup-heavy, long-lived

frontier:
    iteration + expansion lookup, then released

C4 retired index:
    lookup + ownership transfer + merge
```

Therefore:

- P2 must first choose a defensible independent representation;
- the independent threshold is not automatically valid for C1/C4;
- P1 interaction may justify role specialization, but only after the simple
  universal candidate is measured.

## 13. Weighted empirical loss

For candidate `j`, workload `x`, and bucket regime `(S,k,role)`:

```text
L_j =
    lambda_g * C_lookup_j
  + lambda_i * C_iteration_j
  + lambda_w * C_write_j
  + lambda_m * C_merge_j
  + lambda_a * allocated_bytes_j
  + lambda_q * tracked_query_memory_j
```

The lambdas are not subjective tuning parameters. Operation-count lambdas come
from the baseline telemetry. Allocation and query-memory terms remain separate
reported outcomes rather than being collapsed into one opaque score for the
upstream decision.

The final design is chosen by multi-metric gates:

1. end-to-end latency and non-inferiority;
2. allocation and tracked-memory benefit;
3. strongest regression;
4. implementation and lifecycle complexity.

No weighted scalar score can override a failed correctness or common-case
regression gate.

## 14. Microbenchmark coefficient plan

Estimate the constants on the exact benchmark JVM using JMH or an equivalent
forked harness:

```text
c_direct
c_compare_success(k)
c_compare_failure(k)
c_scan_slot
c_emit
c_store
C_promote(S,t)
C_merge(k1,k2,S)
```

Matrix:

```text
S = 4, 8, 16, 32, 64, 128, 256, 512
k = 1, 2, 3, 4, 6, 8, 16, 32, 64, ... <= S
ID shape = low contiguous, high contiguous, alternating, clustered, random
lookup = hit, miss, 50/50 mixed
operation = construct, lookup, iterate, late insert, promote, merge
```

Each benchmark must consume results through a black hole and must not allocate
`NodeState` objects inside the timed operation unless object construction is the
explicit metric.

## 15. Runtime memory probe

Before interpreting size equations, record:

```text
java.version
java.vm.name
UseCompressedOops
HeapEstimator.OBJECT_REFERENCE_BYTES
HeapEstimator.OBJECT_HEADER_BYTES
HeapEstimator.ARRAY_HEADER_BYTES
HeapEstimator.OBJECT_ALIGNMENT_BYTES
H_L
H_H
candidate shallow sizes
```

For every `(S,k)` candidate instance record both:

```text
LocalMemoryTracker delta
JAMM/JOL actual structural deep size
```

The elements are shared `NodeState` references and must be excluded from
structural deep-size comparisons.

## 16. Amdahl gate

Let:

```text
p = baseline elapsed or CPU fraction attributable to bucket allocation,
    iteration, and state-ID lookup that P2 can actually change
s = measured speedup of that fraction
```

Then:

```text
maximum total speedup = 1 / ((1-p) + p/s)
```

If representative baseline profiles show `p < 0.05`, P2 should not be sold as a
latency project. It may still be a valid allocation/query-memory improvement,
but that claim must stand independently.

## 17. Pre-candidate deductions

The following are analytical, not measured final conclusions:

1. The existing Neo4j int-object hash map is unlikely to be optimal for
   singleton and two-state buckets because its two capacity-16 arrays impose a
   large fixed cost.
2. A custom inline bucket can eliminate the dense backing array for tiny `k`
   without adding a second object in sparse mode.
3. The prior P1 two-slot wrapper is useful prototype evidence but is not the
   mathematically minimal P2 dense-mode design because it promotes by allocating
   a full `HeapTrackingArrayList` behind the wrapper.
4. A segmented array is worth implementing only if measured IDs cluster.
5. Bitmap/rank storage is worth implementing only if a material intermediate
   regime survives the inline-vs-dense shortlist.
6. The promotion threshold cannot be selected from `rho=k/S` alone; `S`, `k`,
   hit rate, lookups per bucket, and iterations per bucket all enter the CPU
   equation.

## 18. Decision after baseline measurement

The first implementation shortlist will be selected from the following rules:

```text
If k<=2 or k<=4 dominates and lookup pressure is modest:
    inline-to-dense candidate first

If k=3..16 is material and insertions are ordered:
    sorted compact candidate enters

If state IDs cluster into few chunks:
    segmented candidate enters

If intermediate occupancy is material and S<=64/128:
    bitmap-packed candidate may enter

Hash map:
    retain as a control unless measured constants unexpectedly dominate
```

No candidate branch is authorized until the telemetry tests and baseline
workload characterization pass.