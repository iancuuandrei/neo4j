# Neo4j contribution workspace

Personal repository canonical name: `iancuuandrei/neo4j-contributions`
(renamed from `iancuuandrei/neo4j`; GitHub redirects the old name; use the new name in all
new documentation and maintainer-facing links).

## Remotes and authority

Each upstream repository has one personal fork. Local repositories use:

```text
origin   personal fork (push destination)
upstream official Neo4j repository (fetch only; push URL disabled)
```

The upstream remote and its current default branch are authoritative. Refresh
them before starting or rebasing work:

```powershell
git fetch origin --prune --tags
git fetch upstream --prune --tags
git remote set-head upstream -a
git log -1 --format='%H %cI %s' upstream/HEAD
```

Never merge a research branch into a contribution branch.

## Branch taxonomy

- `upstream-baseline`: untouched local reference tracking `upstream/<default>`.
- `research/ppbfs-lab`: shared PPBFS instrumentation, candidates, evidence, and the canonical
  `research/ppbfs/` docs/status (this branch also carries the imported authoritative P2 archive).
- `research/<architectural-family>`: one lab per coherent research family.
- `research/ppbfs-p2-*`: P2 family — baseline instrumentation, fixed-V candidate, C1/C4
  interactions, final qualification (see `STATUS.md` branch map).
- `contrib/<specific-change>`: one clean upstream contribution, based directly on upstream.
- `archive/<topic>-<date>`: immutable provenance for superseded unpublished research
  (e.g. `archive/ppbfs-lab-pre-p1-p2-status-sync-2026-09-09`).

Create a new contribution from upstream, not from research:

```powershell
git fetch upstream --prune --tags
git branch contrib/<specific-change> upstream/HEAD
git worktree add <new-worktree-path> contrib/<specific-change>
```

Transfer only the production delta and upstream-worthy tests using a reviewed
cherry-pick, patch extraction, or clean reimplementation. Confirm ancestry with:

```powershell
git merge-base contrib/<specific-change> upstream/HEAD
git log --oneline upstream/HEAD..contrib/<specific-change>
git diff --check upstream/HEAD..contrib/<specific-change>
```

## Starting a related PPBFS experiment

Use the existing `research/ppbfs-lab` worktree. Add variant-neutral facilities
under `research/ppbfs/common/` and only experiment-specific deltas under the
appropriate experiment directory. Do not create another PPBFS research branch
unless the topic becomes operationally independent (that clause is what authorized the
`research/ppbfs-p2-*` family and the P1/P2 interaction branches; it does not authorize
merging research history into contribution branches — that prohibition stands).

Every run must record the upstream SHA, variant SHA, JDK/JVM, OS/CPU/RAM,
Neo4j configuration, dataset and query-manifest hashes, warmup/iteration method,
and timestamp. Never overwrite an old run directory.

## Shared artifacts

Downloaded datasets, imported stores, Maven caches, built distributions, JFR
profiles, and bulky raw results stay outside Git on a local D: volume. Worktrees
reference a single shared immutable copy. Git contains URLs, checksums, import
scripts, query manifests, small canonical results, and reports needed to rebuild
or interpret those artifacts.

Before pushing any branch, inspect:

```powershell
git status --short
git diff --check upstream/HEAD..HEAD
git diff --name-only upstream/HEAD..HEAD
git log --oneline upstream/HEAD..HEAD
```

Also search the outgoing diff for credentials, tokens, absolute user paths,
large binaries, database stores, build outputs, IDE state, and agent scratch.
