"""Resource-pack face artwork; native head textures remain independent."""
from PIL import Image, ImageDraw, ImageOps

IVORY = "#fff8e8"
NAVY = "#182a3d"
RED = "#bc2831"
GOLD = "#c6a264"


def mahjong_face(root, key):
    image = Image.new("RGB", (160, 240), "#fff9eb")
    if key == "z5":
        return image
    artwork = Image.open(root / f"resource-pack/source/mahjong/tiles/{key}.png").convert("RGBA")
    # Transparent files include the complete arrangement, including its whitespace.
    artwork = ImageOps.contain(artwork, (140, 220), Image.Resampling.LANCZOS)
    image.paste(artwork, ((160-artwork.width)//2, (240-artwork.height)//2), artwork)
    return image


def xiangqi_face(root, side, piece):
    sheet = Image.open(root / f"resource-pack/source/xiangqi/{piece}.png").convert("RGBA")
    size = sheet.height
    column = 0 if side == "red" else 2
    artwork = sheet.crop((column*size, 0, (column+1)*size, size))
    artwork = artwork.resize((256, 256), Image.Resampling.LANCZOS)
    image = Image.new("RGB", (256, 256), "#dfc595")
    image.paste(artwork, (0, 0), artwork)
    return image


def pip(kind, size, color):
    image = Image.new("RGBA", (16, 16))
    draw = ImageDraw.Draw(image)
    if kind == "diamonds":
        draw.polygon([(8, 0), (15, 8), (8, 15), (0, 8)], fill=color)
    elif kind == "hearts":
        draw.polygon([(0, 3), (3, 0), (5, 0), (8, 3), (11, 0), (13, 0),
                      (15, 3), (15, 7), (8, 15), (0, 7)], fill=color)
    elif kind == "spades":
        draw.polygon([(8, 0), (15, 7), (15, 10), (12, 13), (10, 13),
                      (8, 11), (6, 13), (3, 13), (0, 10), (0, 7)], fill=color)
        draw.polygon([(7, 10), (9, 10), (11, 15), (5, 15)], fill=color)
    else:
        for bounds in ((4, 0, 11, 7), (0, 5, 7, 12), (8, 5, 15, 12)):
            draw.ellipse(bounds, fill=color)
        draw.polygon([(7, 8), (9, 8), (11, 15), (5, 15)], fill=color)
    return image.resize((size, size), Image.Resampling.NEAREST)


def paste_pip(image, kind, point, size, color, upside_down=False):
    artwork = pip(kind, size, color)
    if upside_down:
        artwork = artwork.transpose(Image.Transpose.ROTATE_180)
    image.paste(artwork, (point[0]-size//2, point[1]-size//2), artwork)


def card_base():
    image = Image.new("RGB", (128, 192), NAVY)
    draw = ImageDraw.Draw(image)
    draw.polygon([(6, 2), (121, 2), (125, 6), (125, 185), (121, 189),
                  (6, 189), (2, 185), (2, 6)], fill=IVORY)
    draw.rectangle((7, 7, 120, 184), outline=GOLD, width=1)
    for x, y in ((12, 12), (115, 12), (12, 179), (115, 179)):
        draw.rectangle((x-1, y-1, x+1, y+1), fill=GOLD)
    return image


def playing_face(root, kind, rank, pixel_text):
    image = card_base()
    draw = ImageDraw.Draw(image)
    ink = RED if kind in ("hearts", "diamonds") else NAVY
    value = rank[0].upper() if not rank.isdigit() else rank
    pixel_text(draw, (23, 25), value, 3 if value != "10" else 2, ink, False)
    paste_pip(image, kind, (23, 49), 14, ink)
    corner = image.crop((12, 13, 35, 58)).transpose(Image.Transpose.ROTATE_180)
    image.paste(corner, (93, 134))
    if rank in ("jack", "queen", "king"):
        source = Image.open(root / f"tools/assets/playing-cards/spades_{rank}.png").convert("RGB")
        portrait = source.crop((8, 7, 24, 23)).resize((48, 48), Image.Resampling.NEAREST)
        draw.rectangle((38, 39, 89, 152), outline=GOLD)
        image.paste(portrait, (40, 41))
        image.paste(portrait.transpose(Image.Transpose.ROTATE_180), (40, 103))
        draw.line((41, 96, 86, 96), fill=GOLD)
        paste_pip(image, kind, (64, 96), 12, ink)
    elif rank == "ace":
        paste_pip(image, kind, (64, 96), 44, ink)
        draw.line((44, 127, 83, 127), fill=GOLD)
    else:
        count = int(rank)
        layouts = {
            2: [(64, 49), (64, 143)],
            3: [(64, 49), (64, 96), (64, 143)],
            4: [(45, 49), (83, 49), (45, 143), (83, 143)],
            5: [(45, 49), (83, 49), (64, 96), (45, 143), (83, 143)],
            6: [(x, y) for y in (49, 96, 143) for x in (45, 83)],
            7: [(x, y) for y in (49, 96, 143) for x in (45, 83)] + [(64, 73)],
            8: [(x, y) for y in (49, 96, 143) for x in (45, 83)] + [(64, 73), (64, 119)],
            9: [(x, y) for y in (49, 80, 112, 143) for x in (45, 83)] + [(64, 96)],
            10: [(x, y) for y in (49, 80, 112, 143) for x in (45, 83)] + [(64, 65), (64, 127)],
        }
        for point in layouts[count]:
            paste_pip(image, kind, point, 17, ink, point[1] > 96)
    return image


def joker_face(root, size):
    image = card_base()
    draw = ImageDraw.Draw(image)
    ink = RED if size == "big" else NAVY
    letters = {"J": ("001", "001", "001", "101", "111"),
               "O": ("111", "101", "101", "101", "111"),
               "K": ("101", "101", "110", "101", "101"),
               "E": ("111", "100", "110", "100", "111"),
               "R": ("110", "101", "110", "101", "101")}
    for index, letter in enumerate("JOKER"):
        for row, line in enumerate(letters[letter]):
            for column, value in enumerate(line):
                if value == "1":
                    x, y = 17+column*2, 17+index*13+row*2
                    draw.rectangle((x, y, x+1, y+1), fill=ink)
    corner = image.crop((14, 14, 26, 81)).transpose(Image.Transpose.ROTATE_180)
    image.paste(corner, (102, 111))
    source = Image.open(root / f"tools/assets/playing-cards/joker_{size}.png").convert("RGB")
    portrait = source.crop((6, 12, 26, 24)).resize((70, 42), Image.Resampling.NEAREST)
    draw.rectangle((26, 44, 101, 147), outline=ink, width=2)
    image.paste(portrait, (29, 48))
    image.paste(portrait.transpose(Image.Transpose.ROTATE_180), (29, 102))
    draw.line((34, 96, 93, 96), fill=GOLD, width=2)
    return image


def playing_back():
    image = card_base()
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, 117, 181), fill="#243f65", outline=GOLD, width=2)
    draw.rectangle((15, 15, 112, 176), outline="#7793b0")
    for y in range(25, 176, 12):
        for x in range(23, 112, 12):
            draw.polygon([(x, y-3), (x+3, y), (x, y+3), (x-3, y)], fill="#48668a")
    draw.polygon([(64, 61), (91, 96), (64, 131), (37, 96)], fill=NAVY, outline=GOLD)
    paste_pip(image, "spades", (64, 96), 30, "#e4c780")
    return image
