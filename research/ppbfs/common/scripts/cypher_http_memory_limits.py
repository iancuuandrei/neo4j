#!/usr/bin/env python3
"""Probe PPBFS queries against a server started with one fixed memory limit."""

from __future__ import annotations

import argparse
import csv
import http.client
import json
import re
from pathlib import Path


QUERY = """
MATCH (s:SnapNode {snapId: $source}), (t:SnapNode {snapId: $target})
MATCH p = SHORTEST 2 (s)-[:LINK]->+(t)
RETURN length(p) AS pathLength
"""


def request(
    connection: http.client.HTTPConnection,
    statement: str,
    parameters: dict[str, object],
    database: str = "neo4j",
) -> dict[str, object]:
    body = json.dumps(
        {
            "statements": [
                {"statement": statement, "parameters": parameters, "resultDataContents": ["row"]}
            ]
        }
    )
    connection.request("POST", f"/db/{database}/tx/commit", body, {"Content-Type": "application/json"})
    response = connection.getresponse()
    return {"httpStatus": response.status, "payload": json.loads(response.read())}


def bytes_from_setting(value: str) -> int:
    match = re.fullmatch(r"\s*(\d+(?:\.\d+)?)\s*([kmgt]?i?b?|bytes?)?\s*", value, re.IGNORECASE)
    if not match:
        raise ValueError(f"Unrecognized byte-size value: {value!r}")
    amount = float(match.group(1))
    unit = (match.group(2) or "b").lower()
    factors = {
        "b": 1,
        "byte": 1,
        "bytes": 1,
        "k": 1024,
        "kb": 1024,
        "kib": 1024,
        "m": 1024**2,
        "mb": 1024**2,
        "mib": 1024**2,
        "g": 1024**3,
        "gb": 1024**3,
        "gib": 1024**3,
        "t": 1024**4,
        "tb": 1024**4,
        "tib": 1024**4,
    }
    return round(amount * factors[unit])


def is_transaction_memory_error(errors: list[dict[str, object]]) -> bool:
    accepted_codes = {
        "Neo.ClientError.General.TransactionOutOfMemoryError",
        "Neo.TransientError.General.MemoryPoolOutOfMemoryError",
    }
    return bool(errors) and all(
        error.get("code") in accepted_codes
        and "db.memory.transaction.max" in str(error.get("message", ""))
        for error in errors
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=7474)
    parser.add_argument("--limit", required=True)
    parser.add_argument("--role", choices=("baseline", "candidate"), required=True)
    args = parser.parse_args()

    if args.output.exists():
        raise FileExistsError(f"append-only protection: {args.output} already exists")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.manifest.open(encoding="utf-8", newline="") as source:
        cases = [row for row in csv.DictReader(source) if row["limit"].lower() == args.limit.lower()]
    if not cases:
        raise ValueError(f"Memory-limit manifest has no cases for {args.limit}")

    connection = http.client.HTTPConnection(args.host, args.port, timeout=600)
    mismatches: list[str] = []
    try:
        with args.output.open("x", encoding="utf-8", newline="\n") as output:
            setting_result = request(
                connection,
                "SHOW SETTINGS 'db.memory.transaction.max' "
                "YIELD name, value, startupValue, isExplicitlySet",
                {},
                database="system",
            )
            setting_record = {
                "recordType": "configuration",
                "requestedLimit": args.limit,
                "response": setting_result,
            }
            output.write(json.dumps(setting_record, separators=(",", ":")) + "\n")
            output.flush()
            if setting_result["httpStatus"] != 200 or setting_result["payload"]["errors"]:
                raise RuntimeError(f"Could not read db.memory.transaction.max: {setting_result}")
            rows = setting_result["payload"]["results"][0]["data"]
            if len(rows) != 1:
                raise RuntimeError(f"Expected one memory setting row, got {rows}")
            setting_row = rows[0]["row"]
            if setting_row[0] != "db.memory.transaction.max" or not setting_row[3]:
                raise RuntimeError(f"Memory limit is not explicitly configured: {setting_row}")
            if bytes_from_setting(str(setting_row[1])) != bytes_from_setting(args.limit):
                raise RuntimeError(f"Active memory limit {setting_row[1]!r} does not match {args.limit!r}")

            for case in cases:
                result = request(
                    connection,
                    QUERY,
                    {"source": int(case["source"]), "target": int(case["target"])},
                )
                errors = result["payload"]["errors"]
                observed = "PASS" if result["httpStatus"] == 200 and not errors else "FAIL"
                lengths: list[int] = []
                if observed == "PASS":
                    lengths = [row["row"][0] for row in result["payload"]["results"][0]["data"]]
                record = {
                    "recordType": "query",
                    "caseId": case["case_id"],
                    "source": int(case["source"]),
                    "target": int(case["target"]),
                    "distance": int(case["distance"]),
                    "limit": case["limit"],
                    "baselineExpected": case["expected"],
                    "variantRole": args.role,
                    "observed": observed,
                    "resultLengths": lengths,
                    "response": result,
                }
                output.write(json.dumps(record, separators=(",", ":")) + "\n")
                output.flush()

                if observed == "PASS" and (len(lengths) != 2 or min(lengths) != int(case["distance"])):
                    mismatches.append(f"{case['case_id']}: unexpected results {lengths}")
                if observed == "FAIL" and not is_transaction_memory_error(errors):
                    mismatches.append(f"{case['case_id']}: failure was not the transaction memory limit: {errors}")
                required_outcome = case["expected"] if args.role == "baseline" else (
                    "PASS" if case["expected"] == "PASS" else None
                )
                if required_outcome is not None and observed != required_outcome:
                    mismatches.append(
                        f"{case['case_id']}: {args.role} required {required_outcome}, observed {observed}"
                    )
                print(
                    f"{case['case_id']} role={args.role} limit={case['limit']} "
                    f"baselineExpected={case['expected']} observed={observed}",
                    flush=True,
                )
    finally:
        connection.close()

    if mismatches:
        raise RuntimeError("Memory-limit mismatches: " + "; ".join(mismatches))


if __name__ == "__main__":
    main()
