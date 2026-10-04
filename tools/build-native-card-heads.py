"""Export approved 32x48 faces as 24 thin head skins; this command never uploads."""
import argparse
import hashlib
import io
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
WHITE = (249, 255, 255, 255)
BLUE = (53, 57, 157, 255)


def skin(front):
    image = Image.new("RGBA", (64, 64), WHITE)
    image.paste((0, 0, 0, 0), (32, 0, 64, 16))
    image.paste(front, (8, 8))
    image.paste(BLUE, (24, 8, 32, 16))
    return image


def export(source, output, width=32, height=48):
    (output / "skins").mkdir(parents=True, exist_ok=True)
    (output / "cards").mkdir(parents=True, exist_ok=True)
    faces, cards, unique = {}, {}, set()
    digest = hashlib.sha256()
    for path in sorted(source.glob("*.png")):
        card = Image.open(path).convert("RGBA")
        if card.size != (width, height):
            raise ValueError(f"Card must be {width}x{height} pixels: {path.name}")
        if card.getextrema()[3] != (255, 255):
            raise ValueError(f"Card must be opaque: {path.name}")
        digest.update(path.name.encode())
        digest.update(card.tobytes())
        cards[path.stem] = card
        card.save(output / "cards" / path.name)
        tiles = []
        for row in range(height // 8):
            for column in range(width // 8):
                front = card.crop((column * 8, row * 8, column * 8 + 8, row * 8 + 8))
                buffer = io.BytesIO()
                skin(front).save(buffer, format="PNG")
                contents = buffer.getvalue()
                fingerprint = hashlib.sha256(contents).hexdigest()
                target = "skins/" + fingerprint + ".png"
                if fingerprint not in unique:
                    (output / target).write_bytes(contents)
                    unique.add(fingerprint)
                tiles.append({"row": row, "column": column, "skin": target})
        faces[path.stem] = tiles
    if not faces:
        raise ValueError("No card PNG files found")
    manifest = {"width": width, "height": height, "faces": faces,
                "unique_skins": len(unique), "source_sha256": digest.hexdigest()}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return cards, manifest


def preview(cards, output):
    names = ("hearts_ace", "diamonds_7", "spades_10", "clubs_jack", "diamonds_queen", "spades_king", "joker_small", "joker_big")
    sheet = Image.new("RGB", (1120, 700), (24, 61, 52))
    draw = ImageDraw.Draw(sheet)
    draw.text((20, 15), "32x48 NATIVE CARD FACES / 24 HEADS / LOCAL ART PREVIEW", fill="white")
    for index, name in enumerate(names):
        if name not in cards:
            continue
        x, y = 20 + index % 4 * 280, 45 + index // 4 * 325
        sheet.paste(cards[name].convert("RGB").resize((160, 240), Image.Resampling.NEAREST), (x, y))
        draw.text((x, y + 253), name, fill="white")
    sheet.save(output / "preview.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cards", type=Path, default=ROOT / "tools/assets/playing-cards")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cards, manifest = export(args.cards, args.output)
    preview(cards, args.output)
    print(f"Prepared {len(cards)} faces, 24 heads each; {manifest['unique_skins']} unique skins. No uploads made.")


if __name__ == "__main__":
    main()
