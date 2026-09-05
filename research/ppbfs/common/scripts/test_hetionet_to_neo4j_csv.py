import bz2
import json
import subprocess
import sys
from pathlib import Path


SCRIPT = Path(__file__).with_name("hetionet_to_neo4j_csv.py")


def test_preserves_native_types_and_materializes_bidirectional_edges(tmp_path):
    graph = {
        "kind_to_abbrev": {"Compound": "C", "Gene": "G", "binds": "b"},
        "metanode_kinds": ["Compound", "Gene"],
        "metaedge_tuples": [["Compound", "Gene", "binds", "both"]],
        "nodes": [
            {"kind": "Compound", "identifier": "x", "name": "X", "data": {}},
            {"kind": "Gene", "identifier": 1, "name": "G", "data": {}},
        ],
        "edges": [
            {
                "source_id": ["Compound", "x"],
                "target_id": ["Gene", 1],
                "kind": "binds",
                "direction": "both",
                "data": {},
            }
        ],
    }
    source = tmp_path / "input.json.bz2"
    with bz2.open(source, "wt", encoding="utf-8") as output:
        json.dump(graph, output)
    destination = tmp_path / "out"
    subprocess.run([sys.executable, SCRIPT, source, destination], check=True)
    assert (destination / "relationships.csv").read_text().splitlines() == ["0,1,C_B_G", "1,0,C_B_G"]
    stats = json.loads((destination / "conversion-stats.json").read_text())
    assert stats["sourceEdges"] == 1
    assert stats["importedEdges"] == 2
