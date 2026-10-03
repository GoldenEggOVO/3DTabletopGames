"""Check the head-skin export without MineSkin or network access."""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]


class NativeCardHeadsTest(unittest.TestCase):
    def setUp(self):
        temporary = ROOT / "target/head-tests"
        temporary.mkdir(parents=True, exist_ok=True)
        self.workspace = tempfile.TemporaryDirectory(dir=temporary)
        self.addCleanup(self.workspace.cleanup)
        self.output = Path(self.workspace.name) / "heads"

    def export(self, source=None):
        command = [sys.executable, str(ROOT / "tools/build-native-card-heads.py"),
                   "--output", str(self.output)]
        if source is not None:
            path = Path(self.workspace.name) / "source.json"
            path.write_text(json.dumps(source), encoding="utf-8")
            command += ["--source", str(path)]
        result = subprocess.run(command, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads((self.output / "manifest.json").read_text(encoding="utf-8"))

    def test_card_can_be_reconstructed_without_gaps_or_swapped_tiles(self):
        manifest = self.export()
        self.assertEqual(53, len(manifest["faces"]))
        for face, tiles in manifest["faces"].items():
            reconstructed = Image.new("RGBA", (16, 24))
            self.assertEqual(6, len(tiles), face)
            for tile in tiles:
                skin = Image.open(self.output / tile["skin"]).convert("RGBA")
                reconstructed.paste(skin.crop((8, 8, 16, 16)),
                                    (tile["column"] * 8, tile["row"] * 8))
            expected = Image.open(self.output / "cards" / (face + ".png")).convert("RGBA")
            self.assertEqual(expected.tobytes(), reconstructed.tobytes(), face)
            self.assertEqual((255, 255), reconstructed.getchannel("A").getextrema(), face)

    def test_source_coordinates_preserve_top_left_and_bottom_right(self):
        body = {"from": [-2, -8 / 3, 0], "to": [2, 8 / 3, .1], "material": "WHITE_CONCRETE"}
        top_left = {"from": [-2, 0, .1], "to": [0, 8 / 3, .2], "material": "RED_CONCRETE"}
        bottom_right = {"from": [0, -8 / 3, .1], "to": [2, 0, .2], "material": "BLACK_CONCRETE"}
        self.export({"back": [body], "asymmetric": [body, top_left, bottom_right]})
        card = Image.open(self.output / "cards/asymmetric.png").convert("RGB")
        self.assertEqual((176, 46, 38), card.getpixel((0, 0)))
        self.assertEqual((249, 255, 255), card.getpixel((15, 0)))
        self.assertEqual((249, 255, 255), card.getpixel((0, 23)))
        self.assertEqual((29, 29, 33), card.getpixel((15, 23)))

    def test_ten_corner_remains_readable_at_head_resolution(self):
        self.export()
        card = Image.open(self.output / "cards/spades_10.png").convert("RGB")
        black = (29, 29, 33)
        actual = ["".join("#" if card.getpixel((x, y)) == black else "." for x in range(1, 6))
                  for y in range(1, 6)]
        self.assertEqual(["#.###", "#.#.#", "#.#.#", "#.#.#", "#.###"], actual)
        self.assertEqual(card.crop((1, 1, 6, 6)).tobytes(),
                         card.crop((10, 18, 15, 23)).transpose(Image.Transpose.ROTATE_180).tobytes())

    def test_head_back_is_readable_from_the_opposite_side_and_hat_is_empty(self):
        manifest = self.export()
        back = Image.open(self.output / "cards/back.png").convert("RGBA")
        for face, tiles in manifest["faces"].items():
            reconstructed = Image.new("RGBA", (16, 24))
            for tile in tiles:
                skin = Image.open(self.output / tile["skin"]).convert("RGBA")
                self.assertEqual((64, 64), skin.size)
                self.assertEqual((255, 255), skin.crop((0, 0, 32, 16)).getchannel("A").getextrema())
                self.assertEqual((0, 0), skin.crop((32, 0, 64, 16)).getchannel("A").getextrema())
                reconstructed.paste(skin.crop((24, 8, 32, 16)),
                                    ((1 - tile["column"]) * 8, tile["row"] * 8))
            self.assertEqual(back.tobytes(), reconstructed.tobytes(), face)

    def test_repeated_art_is_exported_once_and_manifest_has_no_fake_texture_urls(self):
        body = {"from": [-2, -8 / 3, 0], "to": [2, 8 / 3, .1], "material": "WHITE_CONCRETE"}
        manifest = self.export({"back": [body], "first": [body], "second": [body]})
        skins = list((self.output / "skins").glob("*.png"))
        self.assertEqual(1, len(skins))
        self.assertEqual(manifest["faces"]["first"], manifest["faces"]["second"])
        self.assertEqual(hashlib.sha256(skins[0].read_bytes()).hexdigest(), skins[0].stem)
        self.assertNotIn("textures.minecraft.net", json.dumps(manifest))


if __name__ == "__main__":
    unittest.main()
