#!/usr/bin/env python3
"""Emit deterministic source-target pairs at exact BFS distances in a SNAP graph."""

from __future__ import annotations

import argparse
import csv
import gzip
from array import array
from collections import deque
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source", type=int, required=True)
    parser.add_argument("--distances", default="10,25,50,100,250,500")
    parser.add_argument("--pairs-per-distance", type=int, default=3)
    parser.add_argument("--directed", action="store_true")
    args = parser.parse_args()

    requested = {int(value) for value in args.distances.split(",")}
    adjacency: dict[int, array] = {}
    with gzip.open(args.input, "rt", encoding="ascii") as source_file:
        for line in source_file:
            if not line or line.startswith("#"):
                continue
            left_text, right_text = line.split()[:2]
            left, right = int(left_text), int(right_text)
            adjacency.setdefault(left, array("q")).append(right)
            adjacency.setdefault(right, array("q"))
            if not args.directed:
                adjacency[right].append(left)

    distances = {args.source: 0}
    targets: dict[int, list[int]] = {distance: [] for distance in requested}
    queue = deque([args.source])
    while queue:
        current = queue.popleft()
        next_distance = distances[current] + 1
        for neighbor in adjacency[current]:
            if neighbor in distances:
                continue
            distances[neighbor] = next_distance
            queue.append(neighbor)
            if next_distance in targets:
                targets[next_distance].append(neighbor)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("source", "target", "measured_distance"))
        for distance in sorted(requested):
            candidates = sorted(targets[distance])[: args.pairs_per_distance]
            if len(candidates) < args.pairs_per_distance:
                raise RuntimeError(f"distance {distance}: only {len(candidates)} candidates")
            for target in candidates:
                writer.writerow((args.source, target, distance))

    print(f"reached={len(distances)} pairs={sum(min(len(v), args.pairs_per_distance) for v in targets.values())}")


if __name__ == "__main__":
    main()
