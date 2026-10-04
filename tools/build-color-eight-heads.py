"""Slice the existing B-style Color Eight art into twelve heads per face; never upload."""
import argparse
import importlib.util
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def build(output):
    art = module("pack_art", "build-resource-pack.py")
    exporter = module("head_export", "build-native-card-heads.py")
    cards = output / "source-cards"
    cards.mkdir(parents=True, exist_ok=True)
    names = []
    for color in art.COLORS:
        for rank in art.RANKS:
            face = color + {"draw": "Draw1", "skip": "Skip", "reverse": "Reverse"}.get(rank, rank)
            art.card_image(rank, color).resize((24, 32), Image.Resampling.NEAREST).save(cards / (face + ".png"))
            names.append(face)
    for face in ("wild", "swap"):
        art.card_image(face).resize((24, 32), Image.Resampling.NEAREST).save(cards / (face + ".png"))
        names.append(face)
    _, manifest = exporter.export(cards, output, 24, 32)
    sheet = Image.new("RGB", (1100, 1300), "#193d34")
    draw = ImageDraw.Draw(sheet)
    draw.text((20, 14), "COLOR EIGHT / EXISTING B ART / 24x32 / 12 HEADS", fill="white")
    for index, face in enumerate(names):
        x, y = 20 + index % 10 * 108, 42 + index // 10 * 206
        sheet.paste(Image.open(cards / (face + ".png")).resize((96, 128), Image.Resampling.NEAREST), (x, y))
        draw.text((x, y + 136), face, fill="white")
    sheet.save(output / "preview.png")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    result = build(parser.parse_args().output)
    print(f"Prepared {len(result['faces'])} Color Eight faces, 12 heads each; {result['unique_skins']} unique skins.")
