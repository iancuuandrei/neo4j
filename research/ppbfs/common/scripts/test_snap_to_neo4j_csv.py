from __future__ import annotations

import gzip
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("snap_to_neo4j_csv.py")


class SnapToNeo4jCsvTest(unittest.TestCase):
    def run_converter(self, *options: str) -> tuple[list[str], list[str], str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "graph.txt.gz"
            output = root / "prepared"
            with gzip.open(source, "wt", encoding="ascii", newline="\n") as stream:
                stream.write("# fixture\n1 2\n2 3\n3 3\n")

            completed = subprocess.run(
                [sys.executable, str(SCRIPT), str(source), str(output), *options],
                check=True,
                capture_output=True,
                text=True,
            )
            nodes = (output / "nodes.csv").read_text(encoding="utf-8").splitlines()
            relationships = (output / "relationships.csv").read_text(encoding="utf-8").splitlines()
            return nodes, relationships, completed.stdout

    def test_preserves_source_direction_by_default(self) -> None:
        nodes, relationships, output = self.run_converter()

        self.assertEqual(["1,1,SnapNode", "2,2,SnapNode", "3,3,SnapNode"], nodes)
        self.assertEqual(["1,2,LINK", "2,3,LINK", "3,3,LINK"], relationships)
        self.assertIn("source_edges=3 imported_edges=3", output)

    def test_materializes_reverse_relationships_without_duplicating_self_loops(self) -> None:
        _, relationships, output = self.run_converter("--materialize-undirected")

        self.assertEqual(
            ["1,2,LINK", "2,1,LINK", "2,3,LINK", "3,2,LINK", "3,3,LINK"],
            relationships,
        )
        self.assertIn("source_edges=3 imported_edges=5", output)


if __name__ == "__main__":
    unittest.main()
