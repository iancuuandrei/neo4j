from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path

from analyze_paired_runs import analyze, load_csv, summarize_speedups
from cypher_http_benchmark import find_operator


class AnalyzePairedRunsTest(unittest.TestCase):
    def test_practical_classification_uses_log_ratio_equivalence_bounds(self) -> None:
        equivalent = summarize_speedups([1.0] * 5)
        positive = summarize_speedups([1.1] * 5)
        non_inferior = summarize_speedups([0.98, 1.0, 1.04, 1.08, 1.15])
        regression = summarize_speedups([0.8] * 5)
        inconclusive = summarize_speedups([0.8, 0.9, 1.0, 1.1, 1.2])

        self.assertEqual("EQUIVALENT WITHIN +/-5%", equivalent["practicalClassification"])
        self.assertEqual("POSITIVE", positive["practicalClassification"])
        self.assertEqual("PRACTICALLY NON-INFERIOR", non_inferior["practicalClassification"])
        self.assertEqual("MATERIAL REGRESSION", regression["practicalClassification"])
        self.assertEqual("INCONCLUSIVE", inconclusive["practicalClassification"])

    def test_find_operator_fails_closed_and_finds_nested_expected_operator(self) -> None:
        plan = {
            "operatorType": "ProduceResults",
            "children": [{"operatorType": "StatefulShortestPath", "args": {"Memory": 123}, "children": []}],
        }

        self.assertEqual("StatefulShortestPath", find_operator(plan, "StatefulShortestPath")["operatorType"])
        self.assertIsNone(find_operator(plan, "ShortestPath"))

        detailed = {"operatorType": "StatefulShortestPath(Into, Trail)", "children": []}
        self.assertEqual(detailed, find_operator(detailed, "StatefulShortestPath"))

        wrapped = {"root": plan}
        self.assertEqual(
            "StatefulShortestPath",
            find_operator(wrapped["root"], "StatefulShortestPath")["operatorType"],
        )

    def test_load_csv_groups_repetitions_without_treating_them_as_forks(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run.csv"
            with path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.writer(output)
                writer.writerow(
                    ("order", "repetition", "source", "target", "distance", "elapsed_ns", "result_lengths")
                )
                writer.writerow((0, 0, 1, 2, 10, 100, "10;11"))
                writer.writerow((1, 1, 1, 2, 10, 300, "10;11"))

            loaded = load_csv(path)

        self.assertEqual([100, 300], loaded[(1, 2, 10)]["elapsedNs"])
        self.assertEqual((10, 11), loaded[(1, 2, 10)]["resultLengths"])

    def test_load_csv_supports_multiple_pairs_at_the_same_distance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run.csv"
            with path.open("w", encoding="utf-8", newline="") as output:
                writer = csv.writer(output)
                writer.writerow(
                    ("order", "repetition", "source", "target", "distance", "elapsed_ns", "result_lengths")
                )
                writer.writerow((0, 0, 1, 2, 10, 100, "10;11"))
                writer.writerow((1, 0, 1, 3, 10, 200, "10;10"))

            loaded = load_csv(path)

        self.assertEqual({(1, 2, 10), (1, 3, 10)}, set(loaded))

    def test_analysis_pairs_independent_fork_medians(self) -> None:
        baseline = {
            1: {
                (1, 2, 10): {"elapsedNs": [100, 300], "resultLengths": (10, 11)},
                (1, 3, 250): {"elapsedNs": [800, 1200], "resultLengths": (250, 251)},
            },
            2: {
                (1, 2, 10): {"elapsedNs": [200, 400], "resultLengths": (10, 11)},
                (1, 3, 250): {"elapsedNs": [1000, 1400], "resultLengths": (250, 251)},
            },
        }
        candidate = {
            1: {
                (1, 2, 10): {"elapsedNs": [50, 150], "resultLengths": (10, 11)},
                (1, 3, 250): {"elapsedNs": [400, 600], "resultLengths": (250, 251)},
            },
            2: {
                (1, 2, 10): {"elapsedNs": [100, 200], "resultLengths": (10, 11)},
                (1, 3, 250): {"elapsedNs": [500, 700], "resultLengths": (250, 251)},
            },
        }

        _, per_distance, aggregates = analyze(baseline, candidate)

        self.assertEqual(2.0, per_distance[0]["pairedSpeedup"]["geometricMeanSpeedup"])
        self.assertEqual(2.0, aggregates["all"]["pairedSpeedup"]["geometricMeanSpeedup"])

    def test_analysis_omits_empty_predefined_aggregate(self) -> None:
        baseline = {
            1: {(1, 2, 10): {"elapsedNs": [100, 120], "resultLengths": (10, 11)}},
            2: {(1, 2, 10): {"elapsedNs": [110, 130], "resultLengths": (10, 11)}},
        }
        candidate = {
            1: {(1, 2, 10): {"elapsedNs": [90, 100], "resultLengths": (10, 11)}},
            2: {(1, 2, 10): {"elapsedNs": [95, 105], "resultLengths": (10, 11)}},
        }

        _, _, aggregates = analyze(baseline, candidate)

        self.assertIn("all", aggregates)
        self.assertIn("shallow_through_100", aggregates)
        self.assertNotIn("deep_250_plus", aggregates)


if __name__ == "__main__":
    unittest.main()
