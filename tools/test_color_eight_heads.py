"""Reconstruct all Color Eight head skins and compare with the approved B-style artwork."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


class ColorEightHeadsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("color_heads", ROOT / "tools/build-color-eight-heads.py")
        cls.builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.builder)
        directory = ROOT / "target/color-head-tests"
        directory.mkdir(parents=True, exist_ok=True)
        cls.workspace = tempfile.TemporaryDirectory(dir=directory)
        cls.output = Path(cls.workspace.name)
        cls.manifest = cls.builder.build(cls.output)

    @classmethod
    def tearDownClass(cls):
        cls.workspace.cleanup()

    def test_playable_and_colored_wild_faces_reconstruct_exactly_from_twenty_four_heads(self):
        self.assertEqual((32, 48), (self.manifest["width"], self.manifest["height"]))
        self.assertEqual(54, len(self.manifest["faces"]))
        self.assertNotIn("back", self.manifest["faces"])
        for face, tiles in self.manifest["faces"].items():
            self.assertEqual(24, len(tiles), face)
            actual = Image.new("RGBA", (32, 48))
            for tile in tiles:
                skin = Image.open(self.output / tile["skin"]).convert("RGBA")
                actual.paste(skin.crop((8, 8, 16, 16)), (tile["column"] * 8, tile["row"] * 8))
            expected = Image.open(self.output / "source-cards" / (face + ".png")).convert("RGBA")
            self.assertEqual(expected.tobytes(), actual.tobytes(), face)

    def test_palette_and_pixels_come_from_the_existing_b_pack_art(self):
        art = self.builder.module("pack_reference", "build-resource-pack.py")
        for face in self.manifest["faces"]:
            rank = face if face in ("wild", "swap") else face[1:]
            rank = {"Draw1": "draw", "Skip": "skip", "Reverse": "reverse"}.get(rank, rank)
            color = None if face in ("wild", "swap") else face[0]
            expected = art.card_image(rank, color).resize((32, 48), Image.Resampling.NEAREST).convert("RGBA")
            actual = Image.open(self.output / "cards" / (face + ".png")).convert("RGBA")
            self.assertEqual(expected.tobytes(), actual.tobytes(), face)

    def test_heads_are_opaque_with_an_empty_hat_layer(self):
        for path in (self.output / "skins").glob("*.png"):
            skin = Image.open(path).convert("RGBA")
            self.assertEqual((255, 255), skin.crop((0, 0, 32, 16)).getchannel("A").getextrema())
            self.assertEqual((0, 0), skin.crop((32, 0, 64, 16)).getchannel("A").getextrema())


if __name__ == "__main__":
    unittest.main()
