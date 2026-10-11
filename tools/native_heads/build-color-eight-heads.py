"""Slice the existing B-style Color Eight art into twenty-four heads per face; never upload."""
import argparse
import importlib.util
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def build(output):
    exporter = module("head_export", "native_heads/build-native-card-heads.py")
    source = ROOT / "resource-pack/native-cards/color-eight"
    _, manifest = exporter.export(source, output, 32, 48)
    cards = output / "cards"
    sheet = Image.new("RGB", (1100, 1300), "#193d34")
    draw = ImageDraw.Draw(sheet)
    draw.text((20, 14), "COLOR EIGHT / EXISTING B ART / 32x48 / 24 HEADS", fill="white")
    for index, face in enumerate(manifest["faces"]):
        x, y = 20 + index % 10 * 108, 42 + index // 10 * 206
        sheet.paste(Image.open(cards / (face + ".png")).resize((96, 144), Image.Resampling.NEAREST), (x, y))
        draw.text((x, y + 152), face, fill="white")
    sheet.save(output / "preview.png")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    result = build(parser.parse_args().output)
    print(f"Prepared {len(result['faces'])} Color Eight faces, 24 heads each; {result['unique_skins']} unique skins.")
