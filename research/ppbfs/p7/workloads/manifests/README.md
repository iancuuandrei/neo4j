# Source manifests

One snapId (LiveJournal / web-Stanford / as-Skitter) or hetioId (Hetionet)
per line. These are the exact lists used; copy them verbatim, do not
regenerate, if the goal is to reproduce the reported numbers.

## How each list was built

- `lj_discovery_sources.txt` (68): unique sources of the existing
  `research/ppbfs/common/manifests/LiveJournal-pairs-v1.csv` (8 curated
  sources) plus 60 uniform random snapIds in `[0, 4847571)` (seed 20260909).
- `lj_timing_sources.txt` (5, frozen timing set): 2022306, 3705591, 3075385
  (high opportunity), 1042128, 2591291 (low-opportunity controls).
- `lj_k2_groups_trail_subset.txt` (20): first 20 lines of the discovery list;
  used for the K=2, K GROUPS K=2, and TRAIL spot checks.
- `web_discovery_sources.txt` (61): source of
  `research/ppbfs/common/manifests/web-Stanford-pairs.csv` (1) plus 60 uniform
  random snapIds in `[0, 281903)` (seed 20260909).
- `web_timing_sources.txt` (5) / `web_monster_source.txt` (48965 alone):
  frozen timing set; 48965 is the censored feasibility case.
- `skitter_discovery_sources.txt` (61): source of
  `research/ppbfs/common/manifests/as-Skitter-pairs.csv` (1) plus 60 uniform
  random snapIds in `[0, 1696000)` (seed 20260909).
- `skitter_timing_sources.txt` (3): 1696073, 1254887, 1297801 (low control).
- `hetionet_gene_sources.txt` (23): the 8 Gene sources of
  `research/ppbfs/common/manifests/hetionet-H3-pairs-v1.csv` plus 15 uniform
  random Genes (seed 20260909).
- `hetionet_compound_sources.txt` (15) / `hetionet_disease_sources.txt` (14):
  uniform random Compounds / Diseases (seed 20260909).
- `hetionet_timing_genes.txt` (2): 28050, 17330.

## Bound-pair control (not duplicated here)

The 48-pair bound Walk control used
`research/ppbfs/common/manifests/LiveJournal-pairs-v1.csv` directly
(`source,target,measured_distance` per line; intoTarget = target node).

## Node identity

- SNAP stores: label `SnapNode`, id property `snapId` (long).
- Hetionet store: per-entity-type labels (`Gene`, `Compound`, `Disease`, …),
  id property `hetioId` (long).
