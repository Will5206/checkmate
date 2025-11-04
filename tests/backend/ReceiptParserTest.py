import unittest
from unittest.mock import patch
import sys
import os

# Add scripts directory to path so we can import receipt_parser
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))
import receipt_parser_local as rp

class TestReceiptParser(unittest.TestCase):

    def test_validate_receipt_ok(self):
        data = {"items": [{"name": "A", "qty": 1, "price": 5.0}]}
        rp.validate_receipt(data)  # should not raise

    def test_validate_receipt_qty_zero_raises(self):
        data = {"items": [{"name": "A", "qty": 0, "price": 5.0}]}
        with self.assertRaises(ValueError):
            rp.validate_receipt(data)

    def test_normalize_numbers_converts_strings(self):
        data = {
            "subtotal": "10.5",
            "tax": "1.5",
            "total": "12.0",
            "items": [{"name": "A", "qty": "2", "price": "5.25"}],
        }
        rp.normalize_numbers(data)
        self.assertIsInstance(data["subtotal"], float)
        self.assertIsInstance(data["items"][0]["qty"], float)

    def test_normalize_numbers_bad_value_raises(self):
        data = {
            "subtotal": "abc",
            "tax": 0,
            "total": 0,
            "items": [],
        }
        with self.assertRaises(ValueError):
            rp.normalize_numbers(data)

    @patch("builtins.print")
    def test_check_totals_no_warning_when_matching(self, mock_print):
        # Note: check_totals sums item["price"] directly, so price should equal subtotal
        data = {
            "subtotal": 10.0,  # items sum = 10.0
            "tax": 1.0,
            "total": 11.0,  # 10.0 + 1.0 = 11.0
            "items": [{"name": "A", "qty": 2.0, "price": 10.0}],  # price sums to subtotal
        }
        rp.check_totals(data)
        mock_print.assert_not_called()

    def test_is_per_price_correct_sums_items(self):
        item_dict = {
            "A": {"qty": 2, "price_per": 5.0},
            "B": {"qty": 1, "price_per": 3.0},
        }
        total = rp.is_per_price_correct(item_dict)
        self.assertEqual(total, 2 * 5.0 + 1 * 3.0)

    def test_refactor_price_per_uses_line_total(self):
        data = {
            "items": [
                {"name": "A", "qty": 2.0, "price": 10.0},  # line total
            ]
        }
        item_dict = {}
        result = rp.refactor_price_per(item_dict, data)
        self.assertEqual(result["A"]["price_per"], 5.0)  # 10 / 2

    def test_create_item_dict_per_unit_price_case(self):
        # model returns per-unit price
        data = {
            "items": [
                {"name": "A", "qty": 2.0, "price": 5.0},
            ]
        }
        total = 11.0
        tax = 1.0
        item_dict = {}
        final = rp.create_item_dict(item_dict, data, total, tax)
        self.assertIsInstance(final, dict)
        self.assertEqual(final["A"]["price_per"], 5.0)
        self.assertEqual(
            round(rp.is_per_price_correct(final) + tax, 2),
            round(total, 2),
        )

    def test_create_item_dict_line_total_case(self):
        # model returns line total
        data = {
            "items": [
                {"name": "A", "qty": 2.0, "price": 10.0},
            ]
        }
        total = 11.0
        tax = 1.0
        item_dict = {}
        final = rp.create_item_dict(item_dict, data, total, tax)
        self.assertIsInstance(final, dict)
        self.assertEqual(final["A"]["price_per"], 5.0)  # 10 / 2
        self.assertEqual(
            round(rp.is_per_price_correct(final) + tax, 2),
            round(total, 2),
        )

    def test_create_item_dict_returns_mismatch_number_when_impossible(self):
        data = {
            "items": [
                {"name": "A", "qty": 1.0, "price": 5.0},
            ]
        }
        total = 999.0
        tax = 0.0
        item_dict = {}
        final = rp.create_item_dict(item_dict, data, total, tax)
        self.assertIsInstance(final, (int, float))
        self.assertNotEqual(round(final + tax, 2), round(total, 2))

if __name__ == "__main__":
    unittest.main()


