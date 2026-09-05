import csv
import tempfile
import unittest
from pathlib import Path

from gmark_to_neo4j_csv import convert


class GMarkConversionTest(unittest.TestCase):
    def test_preserves_predicate_types(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "graph.txt"
            source.write_text("2 7 3\n1 4 2\n", encoding="ascii")
            self.assertEqual((3, 2, 2), convert(source, root / "out"))
            with (root / "out" / "relationships.csv").open(newline="") as stream:
                self.assertEqual([["2", "3", "P7"], ["1", "2", "P4"]], list(csv.reader(stream)))


if __name__ == "__main__":
    unittest.main()
