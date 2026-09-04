#!/usr/bin/env python3
"""Find reproducible long-distance endpoint candidates in a SNAP graph."""

from __future__ import annotations

import argparse
import gzip
from array import array
from collections import deque
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("--directed", action="store_true")
    parser.add_argument("--start", type=int)
    args = parser.parse_args()

    adjacency: dict[int, array] = {}
    with gzip.open(args.input, "rt", encoding="ascii") as source:
        for line in source:
            if not line or line.startswith("#"):
                continue
            left_text, right_text = line.split()[:2]
            left, right = int(left_text), int(right_text)
            adjacency.setdefault(left, array("q")).append(right)
            adjacency.setdefault(right, array("q"))
            if not args.directed:
                adjacency[right].append(left)

    start = args.start if args.start is not None else min(adjacency)

    def sweep(source: int) -> tuple[int, int, int]:
        distances = {source: 0}
        queue = deque([source])
        farthest = source
        while queue:
            current = queue.popleft()
            farthest = current
            for neighbor in adjacency[current]:
                if neighbor not in distances:
                    distances[neighbor] = distances[current] + 1
                    queue.append(neighbor)
        return farthest, distances[farthest], len(distances)

    first, first_distance, first_reached = sweep(start)
    second, second_distance, second_reached = sweep(first)
    print(
        f"start={start} first={first} first_distance={first_distance} first_reached={first_reached} "
        f"second={second} second_distance={second_distance} second_reached={second_reached}"
    )


if __name__ == "__main__":
    main()
