# Upstream pull-request standard

## Neo4j contribution workflow

For an upstream Neo4j contribution, the mandatory sequence is:

1. use the appropriate personal GitHub fork;
2. create a specifically named branch for the feature or fix;
3. add unit tests, including for small behavior changes;
4. implement the change using current repository conventions;
5. add appropriate Javadocs and manual documentation;
6. make logical commits and send a pull request;
7. sign the Neo4j CLA before submission;
8. avoid merge commits and rebase onto the latest intended upstream branch;
9. run all relevant tests through the repository-supported Maven path.

The repository documents a CLA, not a repository-wide DCO rule. A
`Signed-off-by` line does not replace the CLA. No agent may sign a CLA.

## Graduation from research

- Refresh `upstream/HEAD` and record the exact target SHA.
- Re-audit current source and open/recent issues and PRs for overlap.
- Obtain human agreement before creating a required upstream issue/discussion.
- Create a clean `contrib/*` branch directly from upstream; rebase, do not merge.
- Transfer only the production delta, focused tests, and necessary Javadocs,
  manual content, or maintainable benchmark additions.
- Never merge `research/*` or `portfolio/*` into a contribution branch.
- Keep independent contributions independent unless one literally cannot work
  or be reviewed without the other.

## Diff gate

An upstream diff contains one coherent outcome. Remove instrumentation, debug
flags, alternative strategies, generic harnesses, raw results, datasets,
profiles, machine paths, credentials, portfolio files, unrelated cleanup, and
broad reformatting.

Inspect every changed line in ownership and lifecycle context. Preserve or
strengthen assertions. If an assertion no longer models the intended invariant,
replace it with a more precise one and explain the semantic reason.

## Validation gate

Before PR preparation:

1. derive formatting, static analysis, license, generated-source, and test
   commands from the current build tree;
2. run the focused suite, affected module/downstream suites, and broadest
   practical build, recording command, exit code, counts, skips, and time;
3. verify clean status, no merge commits, intentional contribution identity, no
   secrets/large artifacts, and the diff against the exact upstream SHA;
4. investigate every CI failure; never assume it is unrelated without evidence;
5. truthfully list every check not run.

ANTLR grammar changes follow the current module's generated-source process and
include required generated files. Do not use obsolete commands.

## Performance evidence

A performance PR draft includes exact query/workload manifests, baseline and
candidate SHAs, result equivalence, `EXPLAIN`/`PROFILE`, before/after timing and
memory, statistical method, regressions, limitations, and reproducible commands.
Local speed alone is insufficient; controlled graphs alone are not representative.

## PR description and human boundary

The local draft explains problem and intent, invariant/public behavior, issue
reference when present, files, tests and commands, performance evidence,
compatibility, rollback, regressions, limitations, and maintainer questions.
Use accurate English and logical commit messages.

Disposition every review comment. Avoid rewriting public history in a way that
destroys review context unless maintainers request it. Never describe an open
proposal as accepted or merged.

Explicit human authorization is required before every GitHub write action:
issue, discussion, PR, comment, review, label, merge, or CLA action.
