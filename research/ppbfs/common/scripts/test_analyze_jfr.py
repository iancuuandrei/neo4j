import unittest

from analyze_jfr import duration_seconds, summarize


class AnalyzeJfrTest(unittest.TestCase):
    def test_summarizes_weighted_allocation_and_gc(self):
        thread = {"javaThreadId": 7}
        payload = {
            "recording": {
                "events": [
                    {"type": "jdk.ThreadAllocationStatistics", "values": {"startTime": "2026-01-01T00:00:00+00:00", "allocated": 100, "thread": thread}},
                    {"type": "jdk.ThreadAllocationStatistics", "values": {"startTime": "2026-01-01T00:00:02+00:00", "allocated": 500, "thread": thread}},
                    {"type": "jdk.ObjectAllocationSample", "values": {"weight": 600, "objectClass": {"name": "NodeState"}, "stackTrace": None}},
                    {"type": "jdk.GarbageCollection", "values": {}},
                    {"type": "jdk.GCPhasePause", "values": {"duration": "PT0.012S"}},
                ]
            }
        }

        result = summarize(payload)

        self.assertEqual(result["threadAllocatedBytes"], 400)
        self.assertEqual(result["sampledAllocatedBytes"], 600)
        self.assertEqual(result["gcCount"], 1)
        self.assertAlmostEqual(result["gcPauseSeconds"], 0.012)

    def test_parses_iso_and_unit_durations(self):
        self.assertEqual(duration_seconds("PT0.5S"), 0.5)
        self.assertEqual(duration_seconds("10 ms"), 0.01)


if __name__ == "__main__":
    unittest.main()
