"""Resource-pack face artwork; native head textures remain independent."""
from PIL import Image, ImageOps

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


def playing_texture(image):
    canvas = Image.new("RGB", (60, 90), "#ffc7ad")
    canvas.paste(image, (0, 3))
    return canvas.resize((120, 180), Image.Resampling.NEAREST)


def playing_face(root, kind, rank):
    source = root / f"resource-pack/source/playing-cards/{kind}_{rank}.png"
    return playing_texture(Image.open(source).convert("RGB"))


def joker_face(root, size):
    image = Image.open(root / "resource-pack/source/playing-cards/joker.png").convert("RGB")
    if size == "big":
        for y in range(3, 39):
            for x in range(3, 12):
                for point in ((x, y), (59-x, 83-y)):
                    if image.getpixel(point) == (51, 51, 51):
                        image.putpixel(point, (255, 68, 99))
    return playing_texture(image)


def playing_back(root):
    source = root / "resource-pack/source/playing-cards/back_blue_basic.png"
    return playing_texture(Image.open(source).convert("RGB"))
