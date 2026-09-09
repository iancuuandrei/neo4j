# DRAFT — do NOT post without explicit human authorization

Intended target: `neo4j/neo4j#13966` (C1/C4 maintainer thread).
Prepared 2026-09-09 on `research/ppbfs-p2-final-qualification`. Local research only.

---

P2 follow-up completed: sparse NFA-state storage for PPBFS (`FoundNodes` per-node buckets).

**Design:** fixed sorted-vector `StateBucket` replacing dense `NodeState[S]` arrays — ascending
state-ID order preserved, linear lookup with early exit, append fast-path, exact memory
tracking (~33-line production delta + focused unit tests). No adaptive promotion, no hash,
no range layer, no role specialization (each rejected with measured evidence).

**Large-k falsification:** fixed-V survives through k=256 at ≈1150 exact lookups per bucket
iteration (hostile-k128: B0/V 0.996 [0.967, 1.022], 10 fresh-JVM paired forks, oracles match).
No dense crossover found; worst point 0.925 (CI covering parity).

**C1/C4 interaction (10 forks each + servers):** C1+P2 +51%/+10% on sparse chains with
−2.6…−4.2× bucket memory; C4+P2 mirrors it; C4 retired merge stays occupied-proportional
(no merge redesign needed). roadNet-PA deep behavior preserved (depth ≤772, result sets
matched). Hetionet H3 neutrals: B0/V 1.017; C1/C1P2 0.939, C4/C4P2 0.970 pooled over 10
order-balanced forks — a small repeatable tax (~3–6%) on lookup-saturated tiny-NFA canonical
traffic is the disclosed boundary (unfixable by promotion at k=3–4; net case for P1 adopters
remains strongly positive via memory + deep preservation).

**Recommendation:** ship P2 with whichever P1 architecture is preferred (extraction deltas
ready for both `contrib/ppbfs-direct-state-index` and `contrib/ppbfs-deferred-state-index`),
not as a standalone latency PR.

Full archive: `research/ppbfs/p2/` on the linked research branch (`FINAL_QUALIFICATION.md`,
`FINAL_REPORT.md`, telemetry, microbenches, 10-fork qualifications, JFR, paired-server runs).
No PR opened; no code proposed for merge here — maintainer evaluation only.
