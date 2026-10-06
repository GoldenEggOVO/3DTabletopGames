"""Resource-pack face artwork; native head textures remain independent."""
from PIL import Image

def mahjong_face(root, key):
    image = Image.new("RGB", (160, 240), "#fff9eb")
    if key == "z5":
        return image
    artwork = Image.open(root / f"resource-pack/textures/mahjong/tiles/{key}.png").convert("RGBA")
    scale = min(140 / artwork.width, 220 / artwork.height) * 1.3
    artwork = artwork.crop(artwork.getbbox())
    scale = min(scale, 144 / artwork.width, 216 / artwork.height)
    artwork = artwork.resize((round(artwork.width * scale), round(artwork.height * scale)), Image.Resampling.LANCZOS)
    image.paste(artwork, ((160-artwork.width)//2, (240-artwork.height)//2), artwork)
    return image


def xiangqi_face(root, side, piece):
    sheet = Image.open(root / f"resource-pack/textures/xiangqi/{piece}.png").convert("RGBA")
    size = sheet.height
    column = 0 if side == "red" else 2
    artwork = sheet.crop((column*size, 0, (column+1)*size, size))
    artwork = artwork.resize((256, 256), Image.Resampling.LANCZOS)
    image = Image.new("RGB", (256, 256), "#dfc595")
    image.paste(artwork, (0, 0), artwork)
    return image


def playing_texture(image):
    return image.resize((120, 168), Image.Resampling.NEAREST)


def playing_face(root, kind, rank):
    source = root / f"resource-pack/textures/playing-cards/{kind}_{rank}.png"
    return playing_texture(Image.open(source).convert("RGB"))


def joker_face(root, size):
    image = Image.open(root / "resource-pack/textures/playing-cards/joker.png").convert("RGB")
    if size == "big":
        for y in range(3, 39):
            for x in range(3, 12):
                for point in ((x, y), (59-x, 83-y)):
                    if image.getpixel(point) == (51, 51, 51):
                        image.putpixel(point, (255, 68, 99))
    return playing_texture(image)


def playing_back(root):
    source = root / "resource-pack/textures/playing-cards/back_blue_basic.png"
    return playing_texture(Image.open(source).convert("RGB"))
