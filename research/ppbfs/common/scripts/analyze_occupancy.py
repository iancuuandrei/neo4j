#!/usr/bin/env python3
"""Summarize append-only PPBFS occupancy JSONL segments."""

from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path


FIELDS = (
    "nfaStateCount", "uniqueProductStates", "distinctGraphNodes", "globalOccupancy",
    "medianStatesPerNode", "p95StatesPerNode", "maxStatesPerNode", "searchDepth",
    "lookupCount", "historyProbeCount", "acceptedProductStateEncounters",
)


def summarize(rows: list[dict[str, float]]) -> dict[str, object]:
    return {
        "observations": len(rows),
        "metrics": {
            field: {
                "minimum": min(row[field] for row in rows),
                "median": statistics.median(row[field] for row in rows),
                "maximum": max(row[field] for row in rows),
            }
            for field in FIELDS
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--segment", action="append", required=True, help="NAME:START:END, zero-based half-open")
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.input.read_text(encoding="utf-8").splitlines() if line]
    result = {"schemaVersion": 1, "source": str(args.input), "segments": {}}
    for specification in args.segment:
        name, start, end = specification.split(":")
        selected = rows[int(start):int(end)]
        if not selected:
            raise ValueError(f"empty segment: {specification}")
        result["segments"][name] = summarize(selected)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
