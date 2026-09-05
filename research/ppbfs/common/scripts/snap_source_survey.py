#!/usr/bin/env python3
"""Survey deterministic SNAP sources for reachable count and BFS depth."""

from __future__ import annotations

import argparse
import csv
import gzip
import random
from array import array
from collections import deque
from pathlib import Path


def load_adjacency(path: Path, directed: bool) -> dict[int, array]:
    adjacency: dict[int, array] = {}
    with gzip.open(path, "rt", encoding="ascii") as source:
        for line in source:
            if not line or line.startswith("#"):
                continue
            left_text, right_text = line.split()[:2]
            left, right = int(left_text), int(right_text)
            adjacency.setdefault(left, array("q")).append(right)
            adjacency.setdefault(right, array("q"))
            if not directed and left != right:
                adjacency[right].append(left)
    return adjacency


def survey(adjacency: dict[int, array], source: int) -> tuple[int, int, int]:
    distances = {source: 0}
    queue = deque([source])
    farthest = source
    while queue:
        current = queue.popleft()
        if distances[current] > distances[farthest] or (
            distances[current] == distances[farthest] and current < farthest
        ):
            farthest = current
        next_distance = distances[current] + 1
        for neighbor in adjacency[current]:
            if neighbor not in distances:
                distances[neighbor] = next_distance
                queue.append(neighbor)
    return len(distances), distances[farthest], farthest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--directed", action="store_true")
    parser.add_argument("--sample-count", type=int, default=16)
    parser.add_argument("--seed", type=int, default=20260905)
    args = parser.parse_args()

    if args.sample_count < 1:
        parser.error("--sample-count must be positive")

    adjacency = load_adjacency(args.input, args.directed)
    nodes = sorted(adjacency)
    random_source = random.Random(args.seed)
    selected = sorted(random_source.sample(nodes, min(args.sample_count, len(nodes))))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("source", "reached", "max_distance", "farthest_target"))
        for index, source in enumerate(selected, start=1):
            reached, maximum, farthest = survey(adjacency, source)
            writer.writerow((source, reached, maximum, farthest))
            output.flush()
            print(
                f"source={index}/{len(selected)} id={source} reached={reached} "
                f"max_distance={maximum} farthest={farthest}",
                flush=True,
            )


if __name__ == "__main__":
    main()
