# Neo4j contribution workspace

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
- `research/ppbfs-lab`: shared PPBFS instrumentation, candidates, and evidence.
- `research/<architectural-family>`: one lab per coherent research family.
- `contrib/<specific-change>`: one clean upstream contribution, based directly on upstream.
- `archive/<topic>-<date>`: immutable provenance for superseded unpublished research.

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
unless the topic becomes operationally independent.

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
