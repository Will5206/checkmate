import base64
from openai import OpenAI
import os
import json

client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

def encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode("utf-8")

image_path = "example_rec_5.jpeg"
#image_path = "IMG_1957.jpeg"
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

response = client.responses.create(
    model="gpt-4.1",
    input=[
        {
            "role": "user",
            "content": [
                {"type": "input_text", "text": prompt},
                {
                    "type": "input_image",
                    "image_url": f"data:image/png;base64,{base64_image}",
                },
            ],
        }
    ],
)

raw = response.output_text.strip()

if raw.startswith("```"):
    raw = raw.strip("`")
    raw = raw.replace("json", "", 1).strip()

data = json.loads(raw)
def display_receipt(dict, data, tax, total):
    print("Merchant:", data.get("merchant"))

    # prints each item, returns the dict
    display_items(dict)

    print("Subtotal:", data.get("subtotal"))
    print("Tax:", tax)
    print("Total:", total)

    return total

def display_items(dict):
    qty = "qty"
    price_per = "price_per"
    for key in dict:
        print(f"{dict[key][qty]}x   {key}\n      ${dict[key][price_per]} each")
    return dict

def is_per_price_correct(dict):
    qty = "qty"
    price_per = "price_per"
    new_per_price = 0
    for key in dict:
        new_per_price += ( dict[key][qty] * dict[key][price_per] )
    return new_per_price

def refactor_price_per(item_dict):
    new_per = 0
    for item in data.get("items", []):
        item_dict.update({item["name"]: {"qty": item["qty"], "price_per": ( item["price"] / item["qty"]) }})
    print(f"refactor_price_per:\n {item_dict}")
    print("--------")
    return item_dict

def create_item_dict(item_dict, total, tax):
    for item in data.get("items", []):
        # makes a dictionary of each item: {qty: __, price_per: __}
        item_dict.update({item["name"]: {"qty": item["qty"], "price_per": item["price"]}})

    if round((is_per_price_correct(item_dict) + tax), 2) != round(total,2):
        # if price per item * qty is not = to total then there may be a problem with price_per vs price total
        # calls the refactor functino to take each price per and divide by qty
        item_dict = refactor_price_per(item_dict)
        new_per_price = is_per_price_correct(item_dict)
        # item dict now has fixed prices

    if round((new_per_price + tax), 2) == round(total,2):
        return item_dict
    else:
        return new_per_price

total = data.get("total")
tax = data.get("tax")
item_dict = {}

final_dict = create_item_dict(item_dict, total, tax)

if type(final_dict) != dict:
    print(f"target total: {total} and tax: {tax}")
    print(f"RECIEPT READ WRONG, total read = {final_dict + tax}")
else:
    display_receipt(item_dict, data, tax, total)

