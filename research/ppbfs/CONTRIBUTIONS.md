# Contribution index

This index belongs only to `research/ppbfs-lab`; never copy it into an upstream
contribution branch.

| Contribution | Research branch | Contribution branch | Base SHA | Status | Headline evidence | Upstream issue/PR |
| --- | --- | --- | --- | --- | --- | --- |
| P1 C1: direct canonical product-state lookup | `research/ppbfs-lab` (+ `research/ppbfs-c4-deferred-index` history) | `contrib/ppbfs-direct-state-index` | `f213380f812b820a1b312e2ea52cb3d8f1931ccc` | clean contribution candidate; waiting for maintainer decision | C1-class deep speedups (≈4.1–4.4× roadNet d250+); small allocation tax | `neo4j/neo4j#13966` (open); personal-fork draft PR #2 (open) |
| P1 C4: deferred retired-state index (H=8) | `research/ppbfs-lab` (+ `research/ppbfs-c4-*`) | `contrib/ppbfs-deferred-state-index` | `f213380f812b820a1b312e2ea52cb3d8f1931ccc` | clean contribution candidate; waiting for maintainer decision | 4.370x/4.098x deep roads; H3 equivalent; below-C1 allocation | `neo4j/neo4j#13966` (open); personal-fork draft PR #3 (open) |
| P2 fixed sorted-vector state bucket | `research/ppbfs-p2-final-qualification` (telemetry: `research/ppbfs-p2-baseline-instrumentation-v2`) | `research/ppbfs-p2-sorted-vector` (research; no standalone contrib) | `f213380f812b820a1b312e2ea52cb3d8f1931ccc` | research branch; not proposed standalone | sparse +17%/2.5× memory; k≤256 falsification survived; H3 neutral | `neo4j/neo4j#13966` update posted 2026-09-09 |
| P2+C1 interaction | `research/ppbfs-p2-final-qualification` | `research/ppbfs-p2-c1-interaction` (research) | C1 `0a8ef51868e…` + P2 delta | research branch; companion extraction ready | C1→C1+P2 −2.6–4.2× memory; chain255 +51%; H3 −6% disclosed tax | see #13966 update |
| P2+C4 interaction | `research/ppbfs-p2-final-qualification` | `research/ppbfs-p2-c4-interaction` (research) | C4 `7411ac3853c…` + P2 delta | research branch; companion extraction ready | same memory shape; merge audit passed; H3 −3% disclosed tax | see #13966 update |

Superseded stage verdict (kept for provenance): at the C1/C2/C3 stage, C3 failed the 92 MiB
gate that B0 passed and no direct-repository contribution was justified (`state-index/`
reports). That verdict no longer describes the project: C1/C4 clean candidates exist.

Fields for future updates: validated improvement, regression result, NCIS, UFS,
benchmark report, merged SHA, and provenance/archive ref.
