#!/usr/bin/env python3
"""Classify pinned Cypher queries by physical operators without timing them."""

from __future__ import annotations

import argparse
import hashlib
import http.client
import json
from pathlib import Path


def operators(plan: dict[str, object]) -> list[str]:
    result: list[str] = []
    operator = plan.get("operatorType")
    if isinstance(operator, str):
        result.append(operator)
    for child in plan.get("children", []):
        if isinstance(child, dict):
            result.extend(operators(child))
    return result


def execute(connection: http.client.HTTPConnection, query: str, parameters: dict[str, object]) -> dict[str, object]:
    body = json.dumps(
        {
            "statements": [
                {
                    "statement": "EXPLAIN " + query,
                    "parameters": parameters,
                    "resultDataContents": ["row"],
                }
            ]
        }
    )
    connection.request("POST", "/db/neo4j/tx/commit", body, {"Content-Type": "application/json"})
    response = connection.getresponse()
    payload = json.loads(response.read())
    return {"httpStatus": response.status, "payload": payload}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("source_root", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7474)
    args = parser.parse_args()

    if args.output.exists():
        raise FileExistsError(f"append-only protection: {args.output} already exists")
    cases = json.loads(args.manifest.read_text(encoding="utf-8"))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    connection = http.client.HTTPConnection(args.host, args.port, timeout=120)
    with args.output.open("x", encoding="utf-8", newline="\n") as output:
        for case in cases:
            source_path = args.source_root / case["sourcePath"]
            source = source_path.read_bytes()
            query = source.decode("utf-8")
            response = execute(connection, query, case.get("parameters", {}))
            payload = response["payload"]
            errors = payload.get("errors", [])
            result = payload.get("results", [{}])[0] if not errors else {}
            plan_container = result.get("plan")
            plan = plan_container.get("root", plan_container) if isinstance(plan_container, dict) else None
            operator_names = operators(plan) if isinstance(plan, dict) else []
            if any(name.startswith("StatefulShortestPath") for name in operator_names):
                classification = "PPBFS-DIRECT-EVIDENCE"
            elif errors:
                classification = "EXCLUDED"
            else:
                classification = "NON-PPBFS-REGRESSION-CONTROL"
            record = {
                "id": case["id"],
                "sourcePath": case["sourcePath"],
                "sourceSha256": hashlib.sha256(source).hexdigest().upper(),
                "classification": classification,
                "operators": operator_names,
                "errors": errors,
                "response": response,
            }
            output.write(json.dumps(record, separators=(",", ":")) + "\n")
            output.flush()
            print(f"{case['id']}: {classification}: {','.join(operator_names)}", flush=True)
    connection.close()


if __name__ == "__main__":
    main()
