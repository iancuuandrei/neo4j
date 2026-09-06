#!/usr/bin/env python3
"""Reproduce the controlled PPBFS pathology and validate the clean C4 tests."""

from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
from pathlib import Path

B0_SHA = "1529bdd7fdb8fbbee28cbee7cf2f44c3379e2381"
C4_RESEARCH_SHA = "0f8678e159b0837b448093be8607fcc6ebeaddf1"
C4_CLEAN_SHA = "7411ac3853c3408466725e54bca7379c5f8f8f0d"
MODULE = "community/cypher/runtime-util"
MAVEN = "mvn.cmd" if os.name == "nt" else "mvn"


def run(command: list[str], cwd: Path) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def head(worktree: Path) -> str:
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=worktree, text=True
    ).strip()


def require_head(worktree: Path, expected: str) -> None:
    actual = head(worktree)
    if actual != expected:
        raise SystemExit(f"{worktree}: expected {expected}, found {actual}")


def materiality(worktree: Path, stem: Path) -> None:
    run(
        [
            MAVEN,
            "-pl",
            MODULE,
            "-Dtest=PPBFSMaterialityBenchmarkTest",
            f"-Dppbfs.metrics.output={stem.with_name(stem.name + '-metrics.csv')}",
            f"-Dppbfs.timing.output={stem.with_name(stem.name + '-timing.csv')}",
            "-Dppbfs.depths=4096",
            "-Dppbfs.warmups=3",
            "-Dppbfs.repetitions=5",
            "-DsequentialTests",
            "test",
        ],
        worktree,
    )


def first_row(path: Path) -> dict[str, str]:
    with path.open(newline="", encoding="utf-8") as handle:
        return next(csv.DictReader(handle))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--b0-worktree", type=Path, required=True)
    parser.add_argument("--c4-research-worktree", type=Path, required=True)
    parser.add_argument("--clean-c4-worktree", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    output = args.output.resolve()
    if output.exists():
        raise SystemExit(f"append-only output already exists: {output}")
    output.mkdir(parents=True)

    require_head(args.b0_worktree, B0_SHA)
    require_head(args.c4_research_worktree, C4_RESEARCH_SHA)
    require_head(args.clean_c4_worktree, C4_CLEAN_SHA)

    materiality(args.b0_worktree, output / "b0")
    materiality(args.c4_research_worktree, output / "c4")
    run(
        [
            MAVEN,
            "-pl",
            MODULE,
            "-Dtest=FoundNodesDeferredIndexTest,PGPathPropagatingBFSTest,GeneratedPGPathPropagatingBFSTest",
            "-DsequentialTests",
            "test",
        ],
        args.clean_c4_worktree,
    )

    b0_metrics = first_row(output / "b0-metrics.csv")
    c4_metrics = first_row(output / "c4-metrics.csv")
    b0_timing = first_row(output / "b0-timing.csv")
    c4_timing = first_row(output / "c4-timing.csv")
    summary = {
        "schemaVersion": 1,
        "source": {"b0": B0_SHA, "c4Research": C4_RESEARCH_SHA, "c4Clean": C4_CLEAN_SHA},
        "depth": 4096,
        "metrics": {
            "b0Lookups": int(b0_metrics["lookups"]),
            "b0HistoryProbes": int(b0_metrics["history_probes"]),
            "c4Lookups": int(c4_metrics["lookups"]),
            "c4FrozenHistoryProbes": int(c4_metrics["c4_frozen_history_probes"]),
            "b0PathEntities": int(b0_metrics["path_entities"]),
            "c4PathEntities": int(c4_metrics["path_entities"]),
            "c4CanonicalBucketAllocations": int(c4_metrics["c4_canonical_bucket_allocations"]),
        },
        "timing": {
            "b0MedianNs": int(b0_timing["median_ns"]),
            "c4MedianNs": int(c4_timing["median_ns"]),
            "b0OverC4": int(b0_timing["median_ns"]) / int(c4_timing["median_ns"]),
        },
        "cleanFocusedTests": "PASS",
    }
    if summary["metrics"]["b0PathEntities"] != summary["metrics"]["c4PathEntities"]:
        raise SystemExit("B0/C4 controlled result mismatch")
    (output / "summary.json").write_text(
        json.dumps(summary, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
