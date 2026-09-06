# Maintainer reproduction

Run `reproduce.py` from the research checkout. It needs three source worktrees
at the exact SHAs documented in `../REPRODUCING_THE_STUDY.md`; it does not need
SNAP downloads or an installed Neo4j server.

The generated depth-4096 chain is in-memory and deterministic. The output binds
the source SHAs, commands, metrics CSVs, timing CSVs, focused-test status, and a
machine-readable summary. Existing output is never overwritten.

