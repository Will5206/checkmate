# this takes about 15 seconds right now
import base64
import sys
import os
import json
import re
import tempfile
from pathlib import Path

from openai import OpenAI
from dotenv import load_dotenv
from PIL import Image
from pillow_heif import register_heif_opener

register_heif_opener()

# Load environment variables from .env.openai_key file
load_dotenv("openai_key.env")

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
if not OPENAI_API_KEY:
    raise ValueError(
        "OPENAI_API_KEY environment variable must be set. "
        "Create an openai_key.env file with your API key."
    )

client = OpenAI(api_key=OPENAI_API_KEY)


def encode_image(image_path: str) -> str:
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode("utf-8")


if len(sys.argv) < 2:
    print(json.dumps({"error": "Please provide an image path as an argument"}))
    sys.exit(1)


def ensure_jpeg(input_path: str) -> str:
    """Open whatever the phone sent (HEIC, JPEG, etc.) and re-save as JPEG."""
    with Image.open(input_path) as img:
        rgb = img.convert("RGB")
        tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
        rgb.save(tmp.name, format="JPEG")
        return tmp.name


# Image path from Java ReceiptController
original_path = sys.argv[1]
jpeg_path = ensure_jpeg(original_path)
base64_image = encode_image(jpeg_path)

prompt = """
Extract the receipt information from the image.

Return ONLY valid JSON, no extra text, in this format:

{
  "merchant": "string",
  "date": "string",
  "items": [
    {
      "raw_line": "string",   // exact text of the line from the receipt
      "name": "string",
      "qty": number,
      "price": number,        // line total taken from the final amount in raw_line
      "needs_manual_price": boolean // optional, true if price could not be read confidently
    }
  ],
  "subtotal": number,
  "tax": number,
  "tip": number,
  "total": number
}

Rules:
- For each item, raw_line must be the exact text of that line on the receipt.
- price MUST be the numeric amount (including its sign) at the end of raw_line for that same line.
- Negative prices ARE allowed (discounts, happy hour, comps) and must stay negative.
- Lines that correspond to tip, gratuity, or service charge (e.g. "Tip", "Gratuity", "Service Charge", "Serv Chg")
  should be used ONLY to set "tip" and must NOT be included in the items array.
- If there is no tip/gratuity/service-charge line or you are not sure, set tip to 0.
- If you cannot confidently read the price on a line, set "price" to 0 and add "needs_manual_price": true
  for that item.
"""

# Call vision model
response = client.chat.completions.create(
    model="gpt-4o",
    messages=[
        {
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {
                    "type": "image_url",
                    "image_url": {
                        "url": f"data:image/jpeg;base64,{base64_image}"
                    },
                },
            ],
        }
    ],
)

raw = response.choices[0].message.content.strip()

# Clean up JSON if wrapped in code blocks
if raw.startswith("```"):
    lines = raw.split("\n")
    if lines[0].strip().startswith("```json"):
        raw = "\n".join(lines[1:])
    elif lines[0].strip().startswith("```"):
        raw = "\n".join(lines[1:])
    if raw.rstrip().endswith("```"):
        raw = raw.rstrip().rstrip("`").strip()
    raw = raw.strip()


def validate_receipt(data: dict) -> dict:
    for item in data.get("items", []):
        if item.get("qty", 0) <= 0:
            raise ValueError(
                f"Invalid qty from model for item {item.get('name')}: {item.get('qty')}"
            )
    return data


def normalize_numbers(data: dict) -> dict:
    def to_float(x, field):
        try:
            return float(x)
        except (TypeError, ValueError):
            raise ValueError(f"Invalid numeric value for {field}: {x}")

    data["subtotal"] = to_float(data.get("subtotal", 0), "subtotal")
    data["tax"] = to_float(data.get("tax", 0), "tax")
    data["total"] = to_float(data.get("total", 0), "total")
    data["tip"] = to_float(data.get("tip", 0), "tip")

    for item in data.get("items", []):
        item["qty"] = to_float(item.get("qty", 0), f"qty for {item.get('name')}")
        item["price"] = to_float(item.get("price", 0), f"price for {item.get('name')}")
    return data


def check_totals(data: dict) -> None:
    items_sum = sum(item.get("price", 0.0) for item in data.get("items", []))
    if round(items_sum, 2) != round(data["subtotal"], 2):
        print("Warning: subtotal mismatch", items_sum, "vs", data["subtotal"], file=sys.stderr)
    if round(data["subtotal"] + data["tax"] + data.get("tip", 0.0), 2) != round(
        data["total"], 2
    ):
        print(
            "Warning: total mismatch",
            data["subtotal"] + data["tax"] + data.get("tip", 0.0),
            "vs",
            data["total"],
            file=sys.stderr,
        )


def override_price_from_raw_line(data: dict) -> dict:
    """Force price to match the trailing amount in raw_line, including sign, when present."""
    for item in data.get("items", []):
        line = (item.get("raw_line") or "").strip()
        if not line:
            continue
        m = re.search(r"(-?\d+\.\d{2})\s*$", line)
        if m:
            item["price"] = m.group(1)
    return data


def apply_discounts_and_strip_tip(data: dict) -> dict:
    """Apply negative lines as discounts and strip tip/gratuity lines from items."""
    items = data.get("items", [])
    new_items = []
    last_pay_item = None

    for item in items:
        name = (item.get("name") or "").lower()
        price = item.get("price", 0.0)

        # Tip/gratuity/service-charge lines
        is_tip_line = any(
            key in name
            for key in ["tip", "gratuity", "service charge", "serv chg", "grat&srv"]
        )

        if is_tip_line:
            if not data.get("tip"):
                data["tip"] = price
            # do not include this in items to avoid double counting
            continue

        # Discount-like lines
        is_discount = price < 0 or any(
            key in name for key in ["discount", "happy hour", "happy-hour", "comp", "promo"]
        )

        if is_discount:
            if last_pay_item is not None:
                last_pay_item["price"] = max(
                    0.0, last_pay_item["price"] + price
                )
        else:
            new_items.append(item)
            last_pay_item = item

    data["items"] = new_items
    return data


try:
    data = json.loads(raw)
except json.JSONDecodeError:
    print("Failed to parse model output as JSON", file=sys.stderr)
    print(raw, file=sys.stderr)
    raise

# Optional debug:
# for item in data.get("items", []):
#     print("RAW:", item.get("raw_line"), "PRICE:", item.get("price"))

# Items needing manual price (you can surface this to the UI if you want)
unknown_items = [
    item for item in data.get("items", []) if item.get("needs_manual_price")
]
if unknown_items:
    print("Items needing manual price:", unknown_items, file=sys.stderr)

# Post-processing pipeline
override_price_from_raw_line(data)
validate_receipt(data)
normalize_numbers(data)
apply_discounts_and_strip_tip(data)
check_totals(data)

# Final output for Java side: clean JSON only
print(json.dumps(data))
