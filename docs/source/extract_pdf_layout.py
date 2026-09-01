"""Extract the canonical case-study PDF into a reusable vector/text layout source."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pdfplumber
from pypdf import PdfReader, PdfWriter
from pypdf.generic import ContentStream, NameObject


def color_value(value):
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return [float(value)]
    return [float(component) for component in value]


def same_style(left, right) -> bool:
    return (
        left["fontname"] == right["fontname"]
        and abs(float(left["size"]) - float(right["size"])) < 0.05
        and color_value(left.get("non_stroking_color"))
        == color_value(right.get("non_stroking_color"))
        and abs(float(left["matrix"][5]) - float(right["matrix"][5])) < 0.05
    )


def text_runs(chars):
    runs = []
    current = None

    for char in chars:
        if not char.get("upright", True):
            continue

        origin_x = float(char["matrix"][4])
        baseline = float(char["matrix"][5])
        advance_end = origin_x + float(char["adv"]) * float(char["matrix"][0])
        can_append = (
            current is not None
            and same_style(current["sample"], char)
            and origin_x - current["end_x"] < max(float(char["size"]), 3.0) * 1.5
            and origin_x >= current["x"] - 0.1
        )

        if not can_append:
            if current is not None:
                current.pop("sample")
                runs.append(current)
            current = {
                "text": char["text"],
                "x": origin_x,
                "baseline": baseline,
                "end_x": advance_end,
                "font": char["fontname"],
                "size": float(char["size"]),
                "color": color_value(char.get("non_stroking_color")),
                "sample": char,
            }
        else:
            current["text"] += char["text"]
            current["end_x"] = max(current["end_x"], advance_end)

    if current is not None:
        current.pop("sample")
        runs.append(current)

    return runs


def extract_page(page):
    return {
        "width": float(page.width),
        "height": float(page.height),
        "text_runs": text_runs(page.chars),
    }


def extract_artwork(input_pdf: Path, output_artwork: Path):
    reader = PdfReader(input_pdf)
    writer = PdfWriter()

    for source_page in reader.pages:
        content = ContentStream(source_page.get_contents(), reader)
        filtered = []
        inside_text = False
        for operands, operator in content.operations:
            if operator == b"BT":
                inside_text = True
                continue
            if operator == b"ET":
                inside_text = False
                continue
            if not inside_text:
                filtered.append((operands, operator))
        content.operations = filtered
        source_page[NameObject("/Contents")] = content
        writer.add_page(source_page)

    output_artwork.parent.mkdir(parents=True, exist_ok=True)
    with output_artwork.open("wb") as stream:
        writer.write(stream)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_pdf", type=Path)
    parser.add_argument("output_json", type=Path)
    parser.add_argument("output_artwork", type=Path)
    args = parser.parse_args()

    with pdfplumber.open(args.input_pdf) as document:
        source = {
            "title": "End-to-End Claims Processing Solution",
            "author": "Sethu Budaza",
            "pages": [extract_page(page) for page in document.pages],
        }

    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(source, separators=(",", ":")), encoding="utf-8")
    extract_artwork(args.input_pdf, args.output_artwork)


if __name__ == "__main__":
    main()
