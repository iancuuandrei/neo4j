#!/usr/bin/env python3
"""Convert an original gMark `source predicate target` graph to Neo4j import CSV."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


def triples(path: Path):
    with path.open(encoding="ascii") as source:
        for line in source:
            if line.strip():
                left, predicate, right = line.split()[:3]
                yield int(left), int(predicate), int(right)


def convert(source: Path, output: Path) -> tuple[int, int, int]:
    output.mkdir(parents=True, exist_ok=True)
    nodes: set[int] = set()
    predicates: set[int] = set()
    edge_count = 0
    for left, predicate, right in triples(source):
        nodes.update((left, right))
        predicates.add(predicate)
        edge_count += 1
    (output / "nodes-header.csv").write_text(":ID(GMark),gmarkId:long,:LABEL\n", encoding="utf-8")
    with (output / "nodes.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        for node_id in sorted(nodes):
            writer.writerow((node_id, node_id, "GMarkNode"))
    (output / "relationships-header.csv").write_text(":START_ID(GMark),:END_ID(GMark),:TYPE\n", encoding="utf-8")
    with (output / "relationships.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        for left, predicate, right in triples(source):
            writer.writerow((left, right, f"P{predicate}"))
    return len(nodes), edge_count, len(predicates)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    nodes, edges, predicates = convert(args.input, args.output)
    print(f"nodes={nodes} edges={edges} predicates={predicates} output={args.output}")


if __name__ == "__main__":
    main()
