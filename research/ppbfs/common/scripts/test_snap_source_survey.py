from __future__ import annotations

import gzip
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("snap_source_survey.py")


class SnapSourceSurveyTest(unittest.TestCase):
    def test_seeded_survey_is_reproducible(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "graph.txt.gz"
            first = root / "first.csv"
            second = root / "second.csv"
            with gzip.open(source, "wt", encoding="ascii", newline="\n") as stream:
                stream.write("1 2\n2 3\n3 4\n10 11\n")

            command = [
                sys.executable,
                str(SCRIPT),
                str(source),
                str(first),
                "--sample-count",
                "4",
                "--seed",
                "7",
            ]
            subprocess.run(command, check=True, capture_output=True, text=True)
            command[3] = str(second)
            subprocess.run(command, check=True, capture_output=True, text=True)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertIn("max_distance", first.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
