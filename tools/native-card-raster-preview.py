"""Render the Casino source and the four uniform-font planes for local visual review."""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw

COLORS = {
    "WHITE_CONCRETE": (249, 255, 255), "BLACK_CONCRETE": (29, 29, 33),
    "RED_CONCRETE": (176, 46, 38), "BLUE_CONCRETE": (53, 57, 157),
    "LIGHT_BLUE_CONCRETE": (58, 179, 218),
}


def raster(boxes, width, height):
    image = Image.new("RGB", (width, height), COLORS["WHITE_CONCRETE"])
    for y in range(height):
        py = 8 / 3 - (y + .5) * (16 / 3) / height
        for x in range(width):
            px = -2 + (x + .5) * 4 / width
            for box in boxes:
                if box["from"][0] <= px < box["to"][0] and box["from"][1] <= py < box["to"][1]:
                    image.putpixel((x, y), COLORS[box["material"]])
    return image


def planes(pixels):
    image = Image.new("RGB", (320, 860), (255, 0, 255))
    draw = ImageDraw.Draw(image)
    for dx, dy in ((0, 0), (1, 0), (0, 2), (1, 2)):
        for y in range(86):
            for x in range(64):
                draw.rectangle((x * 5 + dx, y * 10 + dy, x * 5 + dx + 3, y * 10 + dy + 7),
                               fill=pixels.getpixel((x, y)))
    assert all(color != (255, 0, 255) for color in image.get_flattened_data()), "A font gap remains"
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    models = json.loads((root / "src/main/resources/playing-card-art.json").read_text())
    faces = ("hearts_ace", "spades_king", "back")
    sheet = Image.new("RGB", (1050, 515), (225, 225, 225))
    label = ImageDraw.Draw(sheet)
    for index, face in enumerate(faces):
        source = raster(models[face], 256, 342)
        reconstructed = planes(raster(models[face], 64, 86))
        for column, image in enumerate((source, reconstructed)):
            image = image.resize((168, 224), Image.Resampling.NEAREST)
            sheet.paste(image, (index * 350 + column * 175, 35))
        label.text((index * 350, 10), face + " source / native glyph reconstruction", fill=(0, 0, 0))
        crop = reconstructed.crop((0, 0, 60, 120)).resize((168, 224), Image.Resampling.NEAREST)
        sheet.paste(crop, (index * 350 + 175, 280))
        label.text((index * 350, 280), "Top-left magnified:\n4 planes; no magenta gaps", fill=(0, 0, 0))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)


if __name__ == "__main__":
    main()
