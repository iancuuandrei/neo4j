#!/usr/bin/env python3
"""Run randomized, raw-sample StatefulShortestPath queries over Neo4j HTTP."""

from __future__ import annotations

import argparse
import csv
import http.client
import json
import random
import time
from pathlib import Path


QUERY = """
MATCH (s:SnapNode {snapId: $source}), (t:SnapNode {snapId: $target})
MATCH p = SHORTEST 2 (s)-[:LINK]->+(t)
RETURN length(p) AS pathLength
"""


def execute(connection: http.client.HTTPConnection, source: int, target: int) -> tuple[int, list[int]]:
    body = json.dumps(
        {
            "statements": [
                {
                    "statement": QUERY,
                    "parameters": {"source": source, "target": target},
                    "resultDataContents": ["row"],
                }
            ]
        }
    )
    started = time.perf_counter_ns()
    connection.request("POST", "/db/neo4j/tx/commit", body, {"Content-Type": "application/json"})
    response = connection.getresponse()
    payload = json.loads(response.read())
    elapsed = time.perf_counter_ns() - started
    if response.status != 200 or payload["errors"]:
        raise RuntimeError(f"HTTP {response.status}: {payload['errors']}")
    lengths = [row["row"][0] for row in payload["results"][0]["data"]]
    return elapsed, lengths


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7474)
    parser.add_argument("--warmups", type=int, default=1)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--seed", type=int, default=20260904)
    args = parser.parse_args()

    with args.manifest.open(encoding="utf-8", newline="") as source_file:
        pairs = [
            (int(row["source"]), int(row["target"]), int(row["measured_distance"]))
            for row in csv.DictReader(source_file)
        ]

    connection = http.client.HTTPConnection(args.host, args.port, timeout=600)
    for _ in range(args.warmups):
        for source, target, expected in pairs:
            _, lengths = execute(connection, source, target)
            if len(lengths) != 2 or min(lengths) != expected:
                raise RuntimeError(f"warmup mismatch {source}->{target}: {lengths}, expected {expected}")

    schedule = [(repetition, *pair) for repetition in range(args.repetitions) for pair in pairs]
    random.Random(args.seed).shuffle(schedule)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("order", "repetition", "source", "target", "distance", "elapsed_ns", "result_lengths"))
        for order, (repetition, source, target, expected) in enumerate(schedule):
            elapsed, lengths = execute(connection, source, target)
            if len(lengths) != 2 or min(lengths) != expected:
                raise RuntimeError(f"result mismatch {source}->{target}: {lengths}, expected {expected}")
            writer.writerow((order, repetition, source, target, expected, elapsed, ";".join(map(str, lengths))))
            output.flush()
            print(f"{order + 1}/{len(schedule)} distance={expected} elapsed_ms={elapsed / 1_000_000:.3f}", flush=True)
    connection.close()


if __name__ == "__main__":
    main()
