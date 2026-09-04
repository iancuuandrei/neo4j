import unittest

from cypher_http_memory_limits import bytes_from_setting, is_transaction_memory_error


class MemoryLimitProbeTest(unittest.TestCase):
    def test_parses_binary_size_spellings(self):
        self.assertEqual(1024**2, bytes_from_setting("1m"))
        self.assertEqual(1024**2, bytes_from_setting("1.00 MiB"))
        self.assertEqual(12 * 1024**2, bytes_from_setting("12MiB"))

    def test_rejects_unrelated_failure(self):
        self.assertFalse(is_transaction_memory_error([{"code": "Neo.ClientError.Statement.SyntaxError"}]))

    def test_accepts_only_transaction_memory_failures(self):
        errors = [{
            "code": "Neo.ClientError.General.TransactionOutOfMemoryError",
            "message": "db.memory.transaction.max threshold reached",
        }]
        self.assertTrue(is_transaction_memory_error(errors))

    def test_accepts_chunk_reservation_memory_failure(self):
        errors = [{
            "code": "Neo.TransientError.General.MemoryPoolOutOfMemoryError",
            "message": "db.memory.transaction.max threshold reached",
        }]
        self.assertTrue(is_transaction_memory_error(errors))


if __name__ == "__main__":
    unittest.main()
