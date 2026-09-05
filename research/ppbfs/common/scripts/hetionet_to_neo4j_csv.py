#!/usr/bin/env python3
"""Convert pinned Hetionet JSON to deterministic neo4j-admin CSV files."""

from __future__ import annotations

import argparse
import bz2
import csv
import json
import re
from pathlib import Path


def token(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9_]", "_", value).strip("_")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    with bz2.open(args.input, "rt", encoding="utf-8") as source:
        graph = json.load(source)

    abbreviations = graph["kind_to_abbrev"]
    edge_types: dict[tuple[str, str, str], str] = {}
    for source_kind, target_kind, edge_kind, _ in graph["metaedge_tuples"]:
        edge_types[(source_kind, target_kind, edge_kind)] = token(
            f"{abbreviations[source_kind]}_{abbreviations[edge_kind]}_{abbreviations[target_kind]}"
        ).upper()

    node_ids: dict[tuple[str, str], int] = {}
    with (args.output / "nodes-header.csv").open("w", encoding="utf-8", newline="") as output:
        output.write(":ID(Hetio),hetioId:long,identifier:string,name:string,:LABEL\n")
    with (args.output / "nodes.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        for numeric_id, node in enumerate(graph["nodes"]):
            key = (node["kind"], str(node["identifier"]))
            if key in node_ids:
                raise ValueError(f"duplicate node key: {key}")
            node_ids[key] = numeric_id
            writer.writerow((numeric_id, numeric_id, node["identifier"], node.get("name", ""), token(node["kind"])))

    imported_edges = 0
    relationship_counts: dict[str, int] = {}
    with (args.output / "relationships-header.csv").open("w", encoding="utf-8", newline="") as output:
        output.write(":START_ID(Hetio),:END_ID(Hetio),:TYPE\n")
    with (args.output / "relationships.csv").open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        for edge in graph["edges"]:
            source_kind, source_identifier = edge["source_id"]
            target_kind, target_identifier = edge["target_id"]
            relationship_type = edge_types[(source_kind, target_kind, edge["kind"])]
            source_id = node_ids[(source_kind, str(source_identifier))]
            target_id = node_ids[(target_kind, str(target_identifier))]
            writer.writerow((source_id, target_id, relationship_type))
            imported_edges += 1
            relationship_counts[relationship_type] = relationship_counts.get(relationship_type, 0) + 1
            if edge["direction"] == "both" and source_id != target_id:
                writer.writerow((target_id, source_id, relationship_type))
                imported_edges += 1
                relationship_counts[relationship_type] += 1

    stats = {
        "sourceNodes": len(graph["nodes"]),
        "sourceEdges": len(graph["edges"]),
        "importedEdges": imported_edges,
        "nodeTypes": sorted(graph["metanode_kinds"]),
        "relationshipTypes": dict(sorted(relationship_counts.items())),
        "metaedgeTypes": len(graph["metaedge_tuples"]),
    }
    (args.output / "conversion-stats.json").write_text(
        json.dumps(stats, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n"
    )
    print(json.dumps(stats, separators=(",", ":")))


if __name__ == "__main__":
    main()
