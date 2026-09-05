import unittest

from analyze_occupancy import summarize


class OccupancyAnalysisTest(unittest.TestCase):
    def test_summarizes_minimum_median_maximum(self) -> None:
        rows = [
            {field: value for field in (
                "nfaStateCount", "uniqueProductStates", "distinctGraphNodes", "globalOccupancy",
                "medianStatesPerNode", "p95StatesPerNode", "maxStatesPerNode", "searchDepth",
                "lookupCount", "historyProbeCount", "acceptedProductStateEncounters",
            )}
            for value in (1, 3, 2)
        ]
        result = summarize(rows)
        self.assertEqual(2, result["metrics"]["historyProbeCount"]["median"])


if __name__ == "__main__":
    unittest.main()
