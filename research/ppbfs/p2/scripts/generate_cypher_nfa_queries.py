#!/usr/bin/env python3
"""Generate deterministic StatefulShortestPath Cypher query families for P2.

The generated queries are *candidates*, not automatically qualified workloads.
Every query must be PROFILEd and its runtime NFA state count and concrete bucket
occupancy must be measured before it enters candidate timing.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


@dataclass(frozen=True)
class QueryArtifact:
    query_id: str
    family: str
    parameter: int
    expected_regime: str
    cypher: str

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.cypher.encode("utf-8")).hexdigest().upper()


def parse_positive_ints(raw: str) -> list[int]:
    values = []
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        value = int(token)
        if value <= 0:
            raise argparse.ArgumentTypeError(f"expected positive integers, got {value}")
        values.append(value)
    if not values:
        raise argparse.ArgumentTypeError("at least one positive integer is required")
    return sorted(set(values))


def relationship_pattern(left: str, right: str, rel_type: str) -> str:
    type_fragment = f":{rel_type}" if rel_type else ""
    return f"({left})-[{type_fragment}]->({right})"


def mandatory_body(length: int, rel_types: Sequence[str], prefix: str) -> str:
    if length <= 0:
        raise ValueError("body length must be positive")
    pieces = [f"({prefix}0)"]
    for index in range(length):
        rel_type = rel_types[index % len(rel_types)] if rel_types else ""
        type_fragment = f":{rel_type}" if rel_type else ""
        pieces.append(f"-[{type_fragment}]->({prefix}{index + 1})")
    return "".join(pieces)


def match_endpoints(label: str, id_property: str) -> str:
    label_fragment = f":{label}" if label else ""
    return (
        f"MATCH (s{label_fragment} {{{id_property}: $source}}), "
        f"(t{label_fragment} {{{id_property}: $target}})"
    )


def long_body_query(
    body_length: int,
    rel_types: Sequence[str],
    label: str,
    id_property: str,
) -> QueryArtifact:
    body = mandatory_body(body_length, rel_types, "q")
    cypher = (
        f"{match_endpoints(label, id_property)}\n"
        f"MATCH p = SHORTEST 2 (s) ({body}){{1, }} (t)\n"
        "RETURN length(p) AS pathLength;\n"
    )
    return QueryArtifact(
        query_id=f"long-body-{body_length}",
        family="long-body",
        parameter=body_length,
        expected_regime="positive-sparse",
        cypher=cypher,
    )


def optional_cascade_query(
    optional_segments: int,
    rel_types: Sequence[str],
    label: str,
    id_property: str,
) -> QueryArtifact:
    if optional_segments <= 0:
        raise ValueError("optional segment count must be positive")

    parts: list[str] = []
    for index in range(optional_segments):
        rel_type = rel_types[index % len(rel_types)] if rel_types else ""
        segment = relationship_pattern(f"o{index}a", f"o{index}b", rel_type)
        parts.append(f"({segment}){{0, 1}}")

    tail_type = rel_types[optional_segments % len(rel_types)] if rel_types else ""
    tail = relationship_pattern("tailA", "tailB", tail_type)
    parts.append(f"({tail}){{1, }}")
    cypher = (
        f"{match_endpoints(label, id_property)}\n"
        f"MATCH p = SHORTEST 2 (s) {' '.join(parts)} (t)\n"
        "RETURN length(p) AS pathLength;\n"
    )
    return QueryArtifact(
        query_id=f"optional-cascade-{optional_segments}",
        family="optional-cascade",
        parameter=optional_segments,
        expected_regime="negative-or-crossover-dense",
        cypher=cypher,
    )


def tiny_query(rel_types: Sequence[str], label: str, id_property: str) -> QueryArtifact:
    rel_type = rel_types[0] if rel_types else ""
    type_fragment = f":{rel_type}" if rel_type else ""
    cypher = (
        f"{match_endpoints(label, id_property)}\n"
        f"MATCH p = SHORTEST 2 (s)-[{type_fragment}]->+(t)\n"
        "RETURN length(p) AS pathLength;\n"
    )
    return QueryArtifact(
        query_id="tiny-plus",
        family="tiny-plus",
        parameter=1,
        expected_regime="negative-or-neutral-tiny-nfa",
        cypher=cypher,
    )


def artifacts(args: argparse.Namespace) -> Iterable[QueryArtifact]:
    yield tiny_query(args.relationship_types, args.label, args.id_property)
    for length in args.long_body_lengths:
        yield long_body_query(
            length,
            args.relationship_types,
            args.label,
            args.id_property,
        )
    for segments in args.optional_segments:
        yield optional_cascade_query(
            segments,
            args.relationship_types,
            args.label,
            args.id_property,
        )


def write_artifacts(output_dir: Path, generated: Sequence[QueryArtifact]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = output_dir / "query-manifest.csv"
    with manifest_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(
            [
                "query_id",
                "family",
                "parameter",
                "expected_regime",
                "query_file",
                "query_sha256",
                "qualification_status",
                "observed_operator",
                "observed_S",
                "observed_median_k",
                "notes",
            ]
        )
        for artifact in generated:
            file_name = f"{artifact.query_id}.cypher"
            (output_dir / file_name).write_text(artifact.cypher, encoding="utf-8")
            writer.writerow(
                [
                    artifact.query_id,
                    artifact.family,
                    artifact.parameter,
                    artifact.expected_regime,
                    file_name,
                    artifact.sha256,
                    "UNQUALIFIED",
                    "",
                    "",
                    "",
                    "must PROFILE and measure concrete level buckets",
                ]
            )

    metadata = {
        "schemaVersion": 1,
        "status": "UNQUALIFIED",
        "warning": (
            "Generated syntax and intended NFA shape are hypotheses. Retain a query only after "
            "PROFILE confirms StatefulShortestPath and P2 telemetry confirms its measured S/k regime."
        ),
        "queries": [
            {
                "queryId": artifact.query_id,
                "family": artifact.family,
                "parameter": artifact.parameter,
                "expectedRegime": artifact.expected_regime,
                "sha256": artifact.sha256,
            }
            for artifact in generated
        ],
    }
    (output_dir / "query-manifest.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate candidate Cypher NFA-shape queries for PPBFS P2"
    )
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--label", default="SnapNode")
    parser.add_argument("--id-property", default="snapId")
    parser.add_argument(
        "--relationship-types",
        type=lambda value: [part.strip() for part in value.split(",") if part.strip()],
        default=["LINK"],
        help="comma-separated relationship-type cycle; empty means untyped",
    )
    parser.add_argument(
        "--long-body-lengths",
        type=parse_positive_ints,
        default=parse_positive_ints("1,3,7,15,31,63,127"),
    )
    parser.add_argument(
        "--optional-segments",
        type=parse_positive_ints,
        default=parse_positive_ints("1,2,4,8,16,32,64"),
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    generated = list(artifacts(args))
    write_artifacts(args.output_dir, generated)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
