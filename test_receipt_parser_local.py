# test_receipt_parser_local.py
import pytest
import receipt_parser_local as r


def test_validate_receipt_rejects_non_positive_qty():
    data = {
        "items": [
            {"name": "Burger", "qty": 1},
            {"name": "Bad item", "qty": 0},
        ]
    }

    with pytest.raises(ValueError):
        r.validate_receipt(data)


def test_normalize_numbers_converts_strings_to_floats():
    data = {
        "subtotal": "10.50",
        "tax": "1.25",
        "tip": "0",
        "total": "11.75",
        "items": [
            {"name": "Burger", "qty": "2", "price": "6.00"},
        ],
    }

    out = r.normalize_numbers(data)

    assert out["subtotal"] == pytest.approx(10.50)
    assert out["tax"] == pytest.approx(1.25)
    assert out["tip"] == pytest.approx(0.0)
    assert out["total"] == pytest.approx(11.75)
    assert out["items"][0]["qty"] == pytest.approx(2.0)
    assert out["items"][0]["price"] == pytest.approx(6.00)


def test_override_price_from_raw_line_uses_trailing_amount_when_present():
    data = {
        "items": [
            {
                "name": "Burger",
                "raw_line": "Burger 2 10.50",
                "price": 0,
            },
            {
                "name": "No amount",
                "raw_line": "Just text",
                "price": 123,
            },
        ]
    }

    r.override_price_from_raw_line(data)
    items = data["items"]

    assert items[0]["price"] == "10.50"
    # unchanged when there is no trailing numeric amount
    assert items[1]["price"] == 123


def test_apply_discounts_and_strip_tip_applies_discount_and_sets_tip():
    data = {
        "items": [
            {"name": "Burger", "qty": 1.0, "price": 10.0},
            {"name": "Discount", "qty": 1.0, "price": -3.0},
            {"name": "Tip", "qty": 1.0, "price": 2.0},
        ],
        "tip": 0.0,
    }

    out = r.apply_discounts_and_strip_tip(data)
    items = out["items"]

    # discount applied to previous item
    assert len(items) == 1
    assert items[0]["name"].lower() == "burger"
    assert items[0]["price"] == pytest.approx(7.0)

    assert out["tip"] == pytest.approx(2.0)
