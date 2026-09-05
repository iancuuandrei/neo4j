#!/usr/bin/env python3
"""Convert a SNAP edge-list gzip into deterministic neo4j-admin import CSV files."""

from __future__ import annotations

import argparse
import csv
import gzip
from pathlib import Path


def edges(path: Path):
    with gzip.open(path, "rt", encoding="ascii") as source:
        for line in source:
            if not line or line.startswith("#"):
                continue
            left, right = line.split()[:2]
            yield int(left), int(right)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--relationship-type", default="LINK")
    parser.add_argument(
        "--materialize-undirected",
        action="store_true",
        help="write the reverse relationship for each non-self edge",
    )
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    node_ids: set[int] = set()
    source_edge_count = 0
    for source, target in edges(args.input):
        node_ids.add(source)
        node_ids.add(target)
        source_edge_count += 1

    with (args.output / "nodes-header.csv").open("w", encoding="utf-8", newline="") as output:
        output.write(":ID(Snap),snapId:long,:LABEL\n")
    with (args.output / "nodes.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        for node_id in sorted(node_ids):
            writer.writerow((node_id, node_id, "SnapNode"))

    with (args.output / "relationships-header.csv").open("w", encoding="utf-8", newline="") as output:
        output.write(":START_ID(Snap),:END_ID(Snap),:TYPE\n")
    with (args.output / "relationships.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        imported_edge_count = 0
        for source, target in edges(args.input):
            writer.writerow((source, target, args.relationship_type))
            imported_edge_count += 1
            if args.materialize_undirected and source != target:
                writer.writerow((target, source, args.relationship_type))
                imported_edge_count += 1

    print(
        f"nodes={len(node_ids)} source_edges={source_edge_count} "
        f"imported_edges={imported_edge_count} output={args.output}"
    )


if __name__ == "__main__":
    main()
