#!/usr/bin/env python3
"""Validate the frozen PPBFS P2 workload manifest.

This is intentionally strict: malformed provenance, duplicate workload IDs,
invalid hashes, contradictory synthetic controls, and stale hashes for existing
query files are evidence failures.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable, Mapping, Sequence

EXPECTED_COLUMNS = (
    "phase",
    "workload_id",
    "dataset",
    "graph_family",
    "query_family",
    "parameter",
    "expected_role",
    "measured_role",
    "status",
    "dataset_sha256",
    "query_path",
    "query_sha256",
    "pair_seed",
    "source_id",
    "target_id",
    "target_S",
    "target_k",
    "observed_S",
    "observed_median_k",
    "observed_p95_k",
    "search_mode",
    "required_operator",
    "observed_operator",
    "correctness_oracle",
    "resource_gate",
    "notes",
)

KNOWN_DATASET_HASHES = {
    "roadNet-PA": "450B8733635D887466A2B96B26411F6E62CAF7006F8A264F59CF9B5D75CDF549",
    "roadNet-CA": "383F7B14424530A9E25C392969A9C19DACE32EBCFD141F0967A1433498C2ACDD",
    "web-Stanford": "49BFC0E366C3028DBD84E8557B09287CBD61051BDE29D160C277688C727CB679",
    "cit-Patents": "9D05955AA997FBC84953C9EB7786675AF80336A356404E75DC18AAA90D3553B0",
    "LiveJournal": "D7BCD5A87B88C896C35FDB9611E804C3F4033C39B58C4C9EA3BA53C680D516D8",
    "as-Skitter": "743D4F91FBCBCACE4A70D44D1190FE75869FAA1398D3EB73BB2D7663C2417510",
    "Hetionet-v1": "A342AB57E9073E6C02BB5E109D1F16917E6F933BE4E5C77EBBAEBFA26B984C19",
    "gMark-test-30k": "A1889F7A1BC804868D392A21FD0D02E702A6E46C7A06A3CACD10C481B8D85F38",
}

ALLOWED_PHASES = {"A", "B", "Q", "I"}
ALLOWED_STATUSES = {
    "PRE_REGISTERED",
    "UNQUALIFIED",
    "PLANNED",
    "BLOCKED_ON_P2_WINNER",
    "QUALIFIED",
    "REJECTED",
}
ALLOWED_SEARCH_MODES = {"unidirectional", "bidirectional", "unknown"}
ALLOWED_REQUIRED_OPERATORS = {"direct-PPBFS", "StatefulShortestPath"}
HEX_64 = re.compile(r"^[0-9A-F]{64}$")
COMMIT_REF = re.compile(r"^commit-[0-9a-f]{40}$")
SYNTHETIC_PARAMETER = re.compile(r"^S(?P<S>[0-9]+)-k(?P<k>[0-9]+)$")


class ManifestError(ValueError):
    """Raised when the manifest violates the evidence contract."""


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def parse_positive_int(value: str, field: str, row_number: int) -> int:
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ManifestError(f"row {row_number}: {field} must be an integer, got {value!r}") from exc
    if parsed <= 0:
        raise ManifestError(f"row {row_number}: {field} must be positive, got {parsed}")
    return parsed


def require(value: str, field: str, row_number: int) -> None:
    if not value.strip():
        raise ManifestError(f"row {row_number}: {field} must not be empty")


def validate_hash(value: str, field: str, row_number: int, *, allow_commit_ref: bool = False) -> None:
    if not value:
        return
    if HEX_64.fullmatch(value):
        return
    if allow_commit_ref and COMMIT_REF.fullmatch(value):
        return
    expected = "64 uppercase hexadecimal characters"
    if allow_commit_ref:
        expected += " or commit- plus 40 lowercase hexadecimal characters"
    raise ManifestError(f"row {row_number}: {field} must be {expected}, got {value!r}")


def validate_existing_query_hash(
    repository_root: Path, row: Mapping[str, str], row_number: int
) -> None:
    query_path = row["query_path"]
    expected_hash = row["query_sha256"]
    if not query_path or query_path.startswith("generated/") or query_path in {
        "generated-by-P2StateBucketWorkloadTest",
        "upstream-official-queries",
    }:
        return

    candidate = repository_root / query_path
    if not candidate.is_file():
        raise ManifestError(
            f"row {row_number}: existing query_path does not exist: {query_path!r}"
        )
    if not expected_hash:
        raise ManifestError(
            f"row {row_number}: existing query_path requires query_sha256: {query_path!r}"
        )
    actual_hash = sha256(candidate)
    if actual_hash != expected_hash:
        raise ManifestError(
            f"row {row_number}: query hash mismatch for {query_path}: "
            f"manifest={expected_hash}, actual={actual_hash}"
        )


def validate_synthetic(row: Mapping[str, str], row_number: int) -> None:
    if row["phase"] != "A":
        return
    if row["dataset"] != "direct-in-memory":
        raise ManifestError(
            f"row {row_number}: phase A must use dataset direct-in-memory"
        )
    match = SYNTHETIC_PARAMETER.fullmatch(row["parameter"])
    if match is None:
        raise ManifestError(
            f"row {row_number}: phase A parameter must have form S<number>-k<number>"
        )
    parameter_s = int(match.group("S"))
    parameter_k = int(match.group("k"))
    target_s = parse_positive_int(row["target_S"], "target_S", row_number)
    target_k = parse_positive_int(row["target_k"], "target_k", row_number)
    if (parameter_s, parameter_k) != (target_s, target_k):
        raise ManifestError(
            f"row {row_number}: parameter S/k {(parameter_s, parameter_k)} does not match "
            f"target S/k {(target_s, target_k)}"
        )
    if target_k >= target_s:
        raise ManifestError(
            f"row {row_number}: controlled active-state count k={target_k} must be < S={target_s} "
            "because state 0 is the start state"
        )
    expected_id = f"p2-synth-s{target_s}-k{target_k}"
    if row["workload_id"] != expected_id:
        raise ManifestError(
            f"row {row_number}: synthetic workload_id must be {expected_id!r}, "
            f"got {row['workload_id']!r}"
        )
    if row["search_mode"] != "unidirectional":
        raise ManifestError(f"row {row_number}: phase A must be unidirectional")
    if row["required_operator"] != "direct-PPBFS":
        raise ManifestError(f"row {row_number}: phase A must require direct-PPBFS")


def validate_rows(
    rows: Sequence[Mapping[str, str]], repository_root: Path
) -> list[str]:
    errors: list[str] = []
    workload_ids = [row["workload_id"] for row in rows]
    duplicates = sorted(
        workload_id
        for workload_id, count in Counter(workload_ids).items()
        if count > 1
    )
    if duplicates:
        errors.append(f"duplicate workload_id values: {', '.join(duplicates)}")

    for row_number, row in enumerate(rows, start=2):
        try:
            for field in (
                "phase",
                "workload_id",
                "dataset",
                "graph_family",
                "query_family",
                "parameter",
                "expected_role",
                "status",
                "query_path",
                "search_mode",
                "required_operator",
                "correctness_oracle",
                "resource_gate",
                "notes",
            ):
                require(row[field], field, row_number)

            if row["phase"] not in ALLOWED_PHASES:
                raise ManifestError(
                    f"row {row_number}: unsupported phase {row['phase']!r}"
                )
            if row["status"] not in ALLOWED_STATUSES:
                raise ManifestError(
                    f"row {row_number}: unsupported status {row['status']!r}"
                )
            if row["search_mode"] not in ALLOWED_SEARCH_MODES:
                raise ManifestError(
                    f"row {row_number}: unsupported search_mode {row['search_mode']!r}"
                )
            if row["required_operator"] not in ALLOWED_REQUIRED_OPERATORS:
                raise ManifestError(
                    f"row {row_number}: unsupported required_operator "
                    f"{row['required_operator']!r}"
                )

            validate_hash(
                row["dataset_sha256"],
                "dataset_sha256",
                row_number,
                allow_commit_ref=True,
            )
            validate_hash(row["query_sha256"], "query_sha256", row_number)

            known_hash = KNOWN_DATASET_HASHES.get(row["dataset"])
            if known_hash is not None and row["dataset_sha256"] != known_hash:
                raise ManifestError(
                    f"row {row_number}: dataset hash for {row['dataset']} must be "
                    f"{known_hash}, got {row['dataset_sha256']!r}"
                )

            validate_synthetic(row, row_number)
            validate_existing_query_hash(repository_root, row, row_number)

            if row["status"] == "QUALIFIED":
                for field in (
                    "measured_role",
                    "observed_S",
                    "observed_median_k",
                    "observed_p95_k",
                    "observed_operator",
                    "query_sha256",
                ):
                    require(row[field], field, row_number)
            elif any(
                row[field]
                for field in (
                    "measured_role",
                    "observed_S",
                    "observed_median_k",
                    "observed_p95_k",
                    "observed_operator",
                )
            ):
                raise ManifestError(
                    f"row {row_number}: measured fields are populated before QUALIFIED status"
                )

            if row["observed_S"] and row["observed_median_k"]:
                observed_s = parse_positive_int(
                    row["observed_S"], "observed_S", row_number
                )
                observed_median = float(row["observed_median_k"])
                if not 0.0 <= observed_median <= observed_s:
                    raise ManifestError(
                        f"row {row_number}: observed_median_k={observed_median} "
                        f"outside [0, observed_S={observed_s}]"
                    )
        except (ManifestError, ValueError) as exc:
            errors.append(str(exc))
    return errors


def read_manifest(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ManifestError("manifest is empty")
        if tuple(reader.fieldnames) != EXPECTED_COLUMNS:
            raise ManifestError(
                "column mismatch:\n"
                f"expected={EXPECTED_COLUMNS}\n"
                f"actual={tuple(reader.fieldnames)}"
            )
        rows: list[dict[str, str]] = []
        for row_number, row in enumerate(reader, start=2):
            if None in row:
                raise ManifestError(
                    f"row {row_number}: too many CSV fields: {row[None]!r}"
                )
            if any(value is None for value in row.values()):
                missing = [key for key, value in row.items() if value is None]
                raise ManifestError(
                    f"row {row_number}: too few CSV fields; missing {missing}"
                )
            rows.append(dict(row))
    if not rows:
        raise ManifestError("manifest contains no workload rows")
    return rows


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate the frozen Neo4j PPBFS P2 workload manifest"
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("research/ppbfs/p2/WORKLOAD_MANIFEST.csv"),
    )
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=Path("."),
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        rows = read_manifest(args.manifest)
        errors = validate_rows(rows, args.repository_root)
    except (ManifestError, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 2

    by_phase = Counter(row["phase"] for row in rows)
    print(
        "PASS: "
        f"{len(rows)} workload rows; "
        + ", ".join(f"phase {phase}={by_phase[phase]}" for phase in sorted(by_phase))
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
