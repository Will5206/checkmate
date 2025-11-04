import base64
from openai import OpenAI
import os
import json
from pathlib import Path

# Shared OpenAI API key for the team
OPENAI_API_KEY = "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"

client = OpenAI(api_key=OPENAI_API_KEY)

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode("utf-8")

# Get the directory where this script is located
script_dir = Path(__file__).parent
# Image file should be in the receipts/ folder
image_path = str(script_dir / "receipts" / "example_rec_5.jpeg")
base64_image = encode_image(image_path)

prompt = """
Extract the receipt information from the image.
Return ONLY valid JSON, no extra text, in this format:

{
  "merchant": "string",
  "date": "string",
  "items": [
    {"name": "string", "qty": number, "price": number}
  ],
  "subtotal": number,
  "tax": number,
  "total": number
}
"""

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
    # Remove opening ``` and optional json label
    lines = raw.split('\n')
    if lines[0].strip().startswith("```json"):
        raw = '\n'.join(lines[1:])
    elif lines[0].strip().startswith("```"):
        raw = '\n'.join(lines[1:])
    
    # Remove closing ```
    if raw.rstrip().endswith("```"):
        raw = raw.rstrip().rstrip("`").strip()
    
    raw = raw.strip()

def validate_receipt(data):
    for item in data.get("items", []):
        if item["qty"] <= 0:
            raise ValueError(
                f"Invalid qty from model for item {item.get('name')}: {item['qty']}"
            )
    return data

def normalize_numbers(data):
    def to_float(x, field):
        try:
            return float(x)
        except (TypeError, ValueError):
            raise ValueError(f"Invalid numeric value for {field}: {x}")

    data["subtotal"] = to_float(data.get("subtotal", 0), "subtotal")
    data["tax"] = to_float(data.get("tax", 0), "tax")
    data["total"] = to_float(data.get("total", 0), "total")

    for item in data.get("items", []):
        item["qty"] = to_float(item["qty"], f"qty for {item.get('name')}")
        item["price"] = to_float(item["price"], f"price for {item.get('name')}")

    return data

def check_totals(data):
    items_sum = sum(item["price"] for item in data.get("items", []))
    if round(items_sum, 2) != round(data["subtotal"], 2):
        print("Warning: subtotal mismatch",
              items_sum, "vs", data["subtotal"])
    if round(data["subtotal"] + data["tax"], 2) != round(data["total"], 2):
        print("Warning: total mismatch",
              data["subtotal"] + data["tax"], "vs", data["total"])

try:
    data = json.loads(raw)
except json.JSONDecodeError:
    print("Failed to parse model output as JSON")
    print(raw)
    raise

validate_receipt(data)
normalize_numbers(data)
check_totals(data)

def display_receipt(dict, data, tax, total):
    print("Merchant:", data.get("merchant"))

    # prints each item, returns the dict
    display_items(dict)

    print("Subtotal:", data.get("subtotal"))
    print("Tax:", tax)
    print("Total:", total)

    return total

def display_items(item_dict):
    qty = "qty"
    price_per = "price_per"
    for key in item_dict:
        print(f"{item_dict[key][qty]}x   {key}\n      ${item_dict[key][price_per]} each")
    return item_dict

def is_per_price_correct(item_dict):
    qty = "qty"
    price_per = "price_per"
    new_per_price = 0
    for key in item_dict:
        new_per_price += ( item_dict[key][qty] * item_dict[key][price_per] )
    return new_per_price

def refactor_price_per(item_dict):
    new_per = 0
    for item in data.get("items", []):
        item_dict.update({item["name"]: {"qty": item["qty"], "price_per": ( item["price"] / item["qty"]) }})
    print(f"refactor_price_per:\n {item_dict}")
    print("--------")
    return item_dict


def create_item_dict(item_dict, total, tax):
    # first assumption: item["price"] is the line total
    for item in data.get("items", []):
        item_dict[item["name"]] = {
            "qty": item["qty"],
            "price_per": item["price"],
        }

    base_sum = is_per_price_correct(item_dict)

    # if already matches, just return
    if round(base_sum + tax, 2) == round(total, 2):
        return item_dict

    # otherwise, try interpreting price as line total and deriving per-unit
    item_dict = refactor_price_per(item_dict)
    new_sum = is_per_price_correct(item_dict)

    if round(new_sum + tax, 2) == round(total, 2):
        return item_dict
    else:
        return new_sum


total = data.get("total")
tax = data.get("tax")
item_dict = {}

final_dict = create_item_dict(item_dict, total, tax)

if type(final_dict) != dict:
    print(f"target total: {total} and tax: {tax}")
    print(f"RECIEPT READ WRONG, total read = {final_dict + tax}")
else:
    display_receipt(item_dict, data, tax, total)

