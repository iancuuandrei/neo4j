#!/usr/bin/env python3
"""Select deterministic typed-graph BFS pairs before candidate timing."""

from __future__ import annotations

import argparse
import csv
import json
import random
from collections import deque
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("prepared", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("survey", type=Path)
    parser.add_argument("--source-label", required=True)
    parser.add_argument("--target-label", required=True)
    parser.add_argument("--relationship-types", required=True)
    parser.add_argument("--distances", required=True)
    parser.add_argument("--source-count", type=int, default=8)
    parser.add_argument("--pairs-per-distance", type=int, default=1)
    parser.add_argument("--seed", type=int, default=20260905)
    args = parser.parse_args()

    allowed = set(args.relationship_types.split(","))
    requested = [int(value) for value in args.distances.split(",")]
    labels: list[str] = []
    with (args.prepared / "nodes.csv").open(encoding="utf-8", newline="") as source:
        for row in csv.reader(source):
            node_id = int(row[0])
            if node_id != len(labels):
                raise ValueError("Hetionet converter IDs must be contiguous and ordered")
            labels.append(row[4])
    adjacency = [[] for _ in labels]
    with (args.prepared / "relationships.csv").open(encoding="utf-8", newline="") as source:
        for row in csv.reader(source):
            if row[2] in allowed:
                adjacency[int(row[0])].append(int(row[1]))

    sources = [node for node, label in enumerate(labels) if label == args.source_label and adjacency[node]]
    rng = random.Random(args.seed)
    selected_sources = rng.sample(sources, min(args.source_count, len(sources)))
    rows: list[tuple[int, int, int, int]] = []
    surveys: list[dict[str, object]] = []
    for source in selected_sources:
        distance = [-1] * len(labels)
        distance[source] = 0
        queue = deque([source])
        while queue:
            node = queue.popleft()
            for target in adjacency[node]:
                if distance[target] == -1:
                    distance[target] = distance[node] + 1
                    queue.append(target)
        retained: dict[str, int] = {}
        for expected in requested:
            targets = [
                node
                for node, actual in enumerate(distance)
                if actual == expected and labels[node] == args.target_label
            ][: args.pairs_per_distance]
            retained[str(expected)] = len(targets)
            rows.extend((source, target, expected, 1) for target in targets)
        surveys.append(
            {
                "source": source,
                "reachable": sum(actual >= 0 for actual in distance),
                "maxDistance": max(distance),
                "retainedByDistance": retained,
            }
        )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("source", "target", "measured_distance", "expected_paths_min"))
        writer.writerows(rows)
    args.survey.write_text(
        json.dumps(
            {
                "seed": args.seed,
                "sourceLabel": args.source_label,
                "targetLabel": args.target_label,
                "relationshipTypes": sorted(allowed),
                "requestedDistances": requested,
                "selectedSources": selected_sources,
                "sources": surveys,
                "retainedPairs": len(rows),
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"sources={len(selected_sources)} retained_pairs={len(rows)}")


if __name__ == "__main__":
    main()
