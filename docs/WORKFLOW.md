# Contribution portfolio workflow

This document is for maintaining the personal fork and for future Codex sessions.
It is portfolio-only documentation and must not be copied into an upstream pull
request.

## Repository identity and remotes

```text
personal fork: iancuuandrei/neo4j-contributions
official upstream: neo4j/neo4j
origin: personal fork
upstream: official repository, fetch only
```

Each campaign pins one exact upstream baseline (PPBFS state-index work: `2026.07`
at `f213380f812b820a1b312e2ea52cb3d8f1931ccc`; WALK bookkeeping, P10, and #13937:
`2026.08` at `736cad02a36bb4a0d32c1064f44768339c814269`). Always re-read
`upstream/HEAD` before new work; branch names and SHAs can change.

The personal fork's default branch is intentionally the portfolio branch so a
visitor sees this documentation first. Therefore, never use `origin/HEAD` as an
engineering baseline. `upstream/HEAD` is authoritative.

### Default-branch decision

Making the portfolio branch the fork default changes the branch selected by a
plain clone and makes one-click/default-branch synchronization less direct. It
does not change fork ancestry, upstream refs, existing contribution branches, or
the explicit local `upstream` remote. The visibility benefit was selected because
the safeguards above and explicit `gh repo sync --branch` command keep engineering
bases unambiguous. If this becomes operationally confusing, restore the upstream-
named branch as the fork default and link the portfolio from that branch without
mixing its documentation into contribution diffs.

Refresh metadata explicitly:

```powershell
git fetch origin --prune --tags
git fetch upstream --prune --tags
git remote set-head upstream -a
git log -1 --format='%H %cI %s' upstream/HEAD
```

To synchronize the fork's upstream-named branch, specify it rather than relying
on the fork default:

```powershell
gh repo sync iancuuandrei/neo4j-contributions --source neo4j/neo4j --branch 2026.07
```

## Branch roles

- `portfolio/contributions`: concise landing page, case studies, and workflow. Documentation-only divergence.
- `research/ppbfs-lab`: shared PPBFS analysis, instrumentation, candidates, harnesses, and evidence.
- `research/<family>`: one laboratory per coherent architectural research family, created only when work starts.
- `contrib/<change>`: one upstream-oriented change created directly from current upstream.
- `archive/<topic>-<date>`: preserved provenance for useful superseded or consolidated work.

Never merge `portfolio/*` or `research/*` into `contrib/*`.

## Worktrees

Use a separate worktree for each concurrently active branch. Before adding one,
run `git worktree list --porcelain`, verify the branch is not already checked out,
and verify the target path does not exist.

Current mapping is maintained in the portfolio report and the local workspace
record. Do not create worktrees for hypothetical work.

## Starting or extending research

1. Identify the existing research family.
2. Reuse its branch and common harness when the work is related.
3. Record the exact upstream SHA before experiments.
4. Keep large datasets, imported databases, builds, profiles, and raw logs outside Git.
5. Commit URLs, checksums, manifests, scripts, small canonical results, and reports.
6. Label evidence as source-confirmed, research-supported, experimental, validated, upstream-confirmed, or merged.
7. Preserve negative results when technically useful.

PPBFS work belongs in `research/ppbfs-lab`; do not create one branch per micro-idea.

## Graduating research into a contribution

Create or refresh a clean branch from upstream:

```powershell
git fetch upstream --prune --tags
git branch contrib/<specific-change> upstream/HEAD
git worktree add <path> contrib/<specific-change>
```

Transfer only the selected production delta and upstream-worthy tests by reviewed
patch extraction, carefully selected cherry-picks, or clean reimplementation.
Instrumentation, raw evidence, portfolio files, unrelated experiments, and other
contributions stay out.

Before publication:

```powershell
git merge-base HEAD upstream/HEAD
git log --oneline upstream/HEAD..HEAD
git diff --check upstream/HEAD..HEAD
git diff --name-status upstream/HEAD..HEAD
```

Rebase contribution branches onto current upstream; do not merge research or
upstream history into them.

## Contribution documentation

Create `docs/contributions/<slug>.md` on this portfolio branch when a substantial
upstream-oriented idea exists. Record the status, problem, architecture, analysis,
design, alternatives, implementation, correctness, methodology, measured results,
trade-offs, and upstream lifecycle. Use “Pending” or “Not submitted” rather than
inventing missing facts.

Update both the case study and `docs/contributions/index.json` when status changes:

```text
Planned → Research → Benchmarking → Proposed → PR Open → Merged
```

Alternative terminal states are Rejected, Superseded, and Not worth pursuing.
An open PR is never described as merged.

## Benchmark evidence

Case studies link to shared methodology and machine-readable results; they do not
copy entire datasets or raw logs into Markdown. Every result must identify the
baseline and variant SHAs, JDK/JVM, OS/CPU/RAM, Neo4j configuration, dataset and
query-manifest hashes, warmup, iterations, timestamp, and exclusions.

## Never commit

- credentials, tokens, private environment values, or personal absolute paths;
- downloaded datasets or imported Neo4j stores;
- Maven/Gradle caches, build outputs, JFR recordings, or benchmark temporary files;
- IDE or agent scratch state;
- claims, PR numbers, issue links, outcomes, or performance figures lacking evidence.

Before every push, inspect status, outgoing commits/files, large files, and the
diff for secrets and machine-specific state. Push only to `origin`; the local
`upstream` push URL must remain disabled.
