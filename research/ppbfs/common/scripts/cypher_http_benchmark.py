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

PROFILE_QUERY = "PROFILE " + QUERY


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


def execute_profile(connection: http.client.HTTPConnection, source: int, target: int) -> dict[str, object]:
    body = json.dumps(
        {
            "statements": [
                {
                    "statement": PROFILE_QUERY,
                    "parameters": {"source": source, "target": target},
                    "resultDataContents": ["row"],
                    "includeStats": True,
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
        raise RuntimeError(f"PROFILE HTTP {response.status}: {payload['errors']}")
    return {"elapsedNs": elapsed, "source": source, "target": target, "response": payload}


def find_operator(plan: dict[str, object], name: str) -> dict[str, object] | None:
    operator_type = plan.get("operatorType")
    if isinstance(operator_type, str) and operator_type.startswith(name):
        return plan
    for child in plan.get("children", []):
        found = find_operator(child, name)
        if found is not None:
            return found
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7474)
    parser.add_argument("--warmups", type=int, default=1)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--seed", type=int, default=20260904)
    parser.add_argument("--profile-jsonl", type=Path)
    parser.add_argument("--profile-summary", type=Path)
    args = parser.parse_args()

    if (args.profile_jsonl is None) != (args.profile_summary is None):
        parser.error("--profile-jsonl and --profile-summary must be supplied together")

    with args.manifest.open(encoding="utf-8", newline="") as source_file:
        pairs = [
            (int(row["source"]), int(row["target"]), int(row["measured_distance"]))
            for row in csv.DictReader(source_file)
        ]

    if args.profile_jsonl is not None:
        for profile_path in (args.profile_jsonl, args.profile_summary):
            if profile_path.exists():
                raise FileExistsError(f"append-only protection: {profile_path} already exists")
            profile_path.parent.mkdir(parents=True, exist_ok=True)

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

    if args.profile_jsonl is not None:
        with args.profile_jsonl.open("x", encoding="utf-8", newline="\n") as raw_output, args.profile_summary.open(
            "x", encoding="utf-8", newline=""
        ) as summary_output:
            summary = csv.writer(summary_output, lineterminator="\n")
            summary.writerow(
                ("source", "target", "distance", "elapsed_ns", "result_lengths", "operator", "arguments_json")
            )
            for order, (source, target, expected) in enumerate(pairs, start=1):
                record = execute_profile(connection, source, target)
                result = record["response"]["results"][0]
                lengths = [row["row"][0] for row in result["data"]]
                record["distance"] = expected
                record["resultLengths"] = lengths
                raw_output.write(json.dumps(record, separators=(",", ":")) + "\n")
                raw_output.flush()
                if len(lengths) != 2 or min(lengths) != expected:
                    raise RuntimeError(f"PROFILE mismatch {source}->{target}: {lengths}, expected {expected}")
                plan_container = result.get("plan")
                if plan_container is None:
                    raise RuntimeError(f"PROFILE returned no plan for {source}->{target}")
                plan = plan_container.get("root", plan_container)
                operator = find_operator(plan, "StatefulShortestPath")
                if operator is None:
                    raise RuntimeError(f"PROFILE did not execute StatefulShortestPath for {source}->{target}")
                operator_summary = {
                    key: value for key, value in operator.items() if key not in ("children", "identifiers")
                }
                summary.writerow(
                    (
                        source,
                        target,
                        expected,
                        record["elapsedNs"],
                        ";".join(map(str, lengths)),
                        operator["operatorType"],
                        json.dumps(operator_summary, separators=(",", ":"), sort_keys=True),
                    )
                )
                summary_output.flush()
                print(f"PROFILE {order}/{len(pairs)} distance={expected}", flush=True)
    connection.close()


if __name__ == "__main__":
    main()
