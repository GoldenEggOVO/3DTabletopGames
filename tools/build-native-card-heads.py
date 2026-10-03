"""Prepare six head skins per Casino card; this command never uploads anything."""
import argparse
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
COLORS = {
    "WHITE_CONCRETE": (249, 255, 255, 255), "BLACK_CONCRETE": (29, 29, 33, 255),
    "RED_CONCRETE": (176, 46, 38, 255), "BLUE_CONCRETE": (53, 57, 157, 255),
    "LIGHT_BLUE_CONCRETE": (58, 179, 218, 255),
}
RANKS = {
    "ace": (".#.", "#.#", "###", "#.#", "#.#"),
    "2": ("##.", "..#", ".#.", "#..", "###"),
    "3": ("##.", "..#", ".#.", "..#", "##."),
    "4": ("#.#", "#.#", "###", "..#", "..#"),
    "5": ("###", "#..", "##.", "..#", "##."),
    "6": (".##", "#..", "###", "#.#", "###"),
    "7": ("###", "..#", ".#.", ".#.", ".#."),
    "8": ("###", "#.#", "###", "#.#", "###"),
    "9": ("###", "#.#", "###", "..#", "##."),
    "10": ("#.###", "#.#.#", "#.#.#", "#.#.#", "#.###"),
    "jack": ("..#", "..#", "..#", "#.#", ".#."),
    "queen": (".#.", "#.#", "#.#", ".#.", "..#"),
    "king": ("#.#", "#.#", "##.", "#.#", "#.#"),
}
SUITS = {
    "hearts": ("#.#", "###", ".#."), "diamonds": (".#.", "###", ".#."),
    "spades": (".#.", "###", ".#."), "clubs": (".#.", "#.#", ".#."),
}
LARGE_SUITS = {
    "hearts": (".#.#.", "#####", "#####", ".###.", "..#.."),
    "diamonds": ("..#..", ".###.", "#####", ".###.", "..#.."),
    "spades": ("..#..", ".###.", "#####", "..#..", ".###."),
    "clubs": ("..#..", ".###.", "#.#.#", "#####", "..#.."),
}


def symbol(image, pattern, x, y, color, scale=1):
    draw = ImageDraw.Draw(image)
    for row, line in enumerate(pattern):
        for column, pixel in enumerate(line):
            if pixel == "#":
                left, top = x + column * scale, y + row * scale
                draw.rectangle((left, top, left + scale - 1, top + scale - 1), fill=color)


def card_image(face, boxes):
    suit, _, rank = face.partition("_")
    if suit not in SUITS or rank not in RANKS:
        return raster(boxes, 16, 24)
    image = Image.new("RGBA", (16, 24), COLORS["WHITE_CONCRETE"])
    color = COLORS["RED_CONCRETE" if suit in ("hearts", "diamonds") else "BLACK_CONCRETE"]
    corner = Image.new("RGBA", (5, 9))
    symbol(corner, RANKS[rank], 0, 0, color)
    symbol(corner, SUITS[suit], 0, 6, color)
    image.alpha_composite(corner, (1, 1))
    image.alpha_composite(corner.transpose(Image.Transpose.ROTATE_180), (10, 14))
    if rank == "ace":
        symbol(image, LARGE_SUITS[suit], 3, 7, color, 2)
    else:
        # A legible centre rank avoids losing small Casino pips to resampling.
        width = len(RANKS[rank][0]) * 2
        symbol(image, RANKS[rank], (16 - width) // 2, 7, color, 2)
        symbol(image, SUITS[suit], 6, 18, color)
    return image


def raster(boxes, width, height):
    image = Image.new("RGBA", (width, height), COLORS["WHITE_CONCRETE"])
    for y in range(height):
        py = 8 / 3 - (y + .5) * (16 / 3) / height
        for x in range(width):
            px = -2 + (x + .5) * 4 / width
            for box in boxes:
                if box["from"][0] <= px < box["to"][0] and box["from"][1] <= py < box["to"][1]:
                    image.putpixel((x, y), COLORS[box["material"]])
    return image


def skin(front, back):
    image = Image.new("RGBA", (64, 64), COLORS["WHITE_CONCRETE"])
    # Keep the hat transparent, including the bottom hat face at x48..56.
    image.paste((0, 0, 0, 0), (32, 0, 64, 16))
    image.paste(front, (8, 8))
    image.paste(back, (24, 8))
    return image


def export(models, output):
    (output / "skins").mkdir(parents=True, exist_ok=True)
    (output / "cards").mkdir(parents=True, exist_ok=True)
    cards = {face: card_image(face, boxes) for face, boxes in models.items()}
    faces = {}
    unique = set()
    for face, card in cards.items():
        card.save(output / "cards" / (face + ".png"))
        tiles = []
        for row in range(3):
            for column in range(2):
                front = card.crop((column * 8, row * 8, (column + 1) * 8, (row + 1) * 8))
                # The opposite viewer sees the columns in reverse order.
                back = cards["back"].crop(((1 - column) * 8, row * 8,
                                           (2 - column) * 8, (row + 1) * 8))
                image = skin(front, back)
                buffer = io.BytesIO()
                image.save(buffer, format="PNG")
                contents = buffer.getvalue()
                digest = hashlib.sha256(contents).hexdigest()
                path = "skins/" + digest + ".png"
                if digest not in unique:
                    (output / path).write_bytes(contents)
                    unique.add(digest)
                tiles.append({"row": row, "column": column, "skin": path})
        faces[face] = tiles
    manifest = {"width": 16, "height": 24, "faces": faces,
                "unique_skins": len(unique), "source_sha256": hashlib.sha256(
                    json.dumps(models, sort_keys=True, separators=(",", ":")).encode()).hexdigest()}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return cards, manifest


def preview(models, cards, output):
    faces = ("hearts_ace", "spades_10", "clubs_jack", "diamonds_queen", "spades_king", "back")
    sheet = Image.new("RGB", (1160, 580), (225, 225, 225))
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 10), "CASINO SOURCE / SIMPLIFIED SIX HEADS (16x24 PIXELS) - LOCAL PREVIEW, NOT IN-GAME", fill=(0, 0, 0))
    for index, face in enumerate(faces):
        x, y = (index % 3) * 385 + 10, (index // 3) * 280 + 40
        draw.text((x, y), face + "   source / head reconstruction", fill=(0, 0, 0))
        source = raster(models[face], 192, 256).resize((168, 224), Image.Resampling.NEAREST)
        assembled = cards[face].resize((168, 224), Image.Resampling.NEAREST)
        sheet.paste(source, (x, y + 18))
        sheet.paste(assembled, (x + 182, y + 18))
    sheet.save(output / "preview.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "tools/assets/playing-card-art.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    models = json.loads(args.source.read_text(encoding="utf-8"))
    cards, manifest = export(models, args.output)
    if all(face in cards for face in ("hearts_ace", "spades_10", "clubs_jack", "diamonds_queen", "spades_king")):
        preview(models, cards, args.output)
    print(f"Prepared {len(cards)} card faces, six heads each; {manifest['unique_skins']} unique skins. No uploads made.")


if __name__ == "__main__":
    main()
