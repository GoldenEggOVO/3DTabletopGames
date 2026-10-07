"""Check the approved 32x48 head-skin export without network access."""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "resource-pack/textures/native-playing-cards"

class NativeCardHeadsTest(unittest.TestCase):
    def setUp(self):
        temporary = ROOT / "target/head-tests"
        temporary.mkdir(parents=True, exist_ok=True)
        self.workspace = tempfile.TemporaryDirectory(dir=temporary)
        self.addCleanup(self.workspace.cleanup)
        self.output = Path(self.workspace.name) / "heads"

    def command(self, cards=None):
        command = [sys.executable, str(ROOT / "tools/native_heads/build-native-card-heads.py"),
                   "--output", str(self.output)]
        if cards is not None:
            command += ["--cards", str(cards)]
        return subprocess.run(command, capture_output=True, text=True)

    def export(self, cards=None):
        result = self.command(cards)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads((self.output / "manifest.json").read_text())

    def test_54_faces_reconstruct_exactly_in_a_four_by_six_head_grid(self):
        manifest = self.export()
        self.assertEqual((32, 48), (manifest["width"], manifest["height"]))
        self.assertEqual(54, len(manifest["faces"]))
        self.assertNotIn("back", manifest["faces"])
        for face, tiles in manifest["faces"].items():
            reconstructed = Image.new("RGBA", (32, 48))
            self.assertEqual(24, len(tiles), face)
            for tile in tiles:
                skin = Image.open(self.output / tile["skin"]).convert("RGBA")
                reconstructed.paste(skin.crop((8, 8, 16, 16)),
                                    (tile["column"] * 8, tile["row"] * 8))
            expected = Image.open(ART / (face + ".png")).convert("RGBA")
            self.assertEqual(expected.tobytes(), reconstructed.tobytes(), face)

    def test_skin_back_is_plain_blue_and_every_hat_face_is_transparent(self):
        manifest = self.export()
        for tiles in manifest["faces"].values():
            for tile in tiles:
                skin = Image.open(self.output / tile["skin"]).convert("RGBA")
                self.assertEqual((64, 64), skin.size)
                self.assertEqual((255, 255), skin.crop((0, 0, 32, 16)).getchannel("A").getextrema())
                self.assertEqual((0, 0), skin.crop((32, 0, 64, 16)).getchannel("A").getextrema())
                self.assertEqual(Image.new("RGBA", (8, 8), (53, 57, 157, 255)).tobytes(),
                                 skin.crop((24, 8, 32, 16)).tobytes())

    def test_monster_faces_and_wither_jokers_are_rotationally_symmetric(self):
        for path in ART.glob("*.png"):
            if path.stem.endswith(("_jack", "_queen", "_king")) or path.stem.startswith("joker_"):
                image = Image.open(path).convert("RGBA")
                self.assertEqual(image.tobytes(), image.transpose(Image.Transpose.ROTATE_180).tobytes(), path.name)

    def test_repeated_art_is_exported_once_without_fake_texture_urls(self):
        cards = Path(self.workspace.name) / "cards"
        cards.mkdir()
        for face in ("first", "second"):
            Image.new("RGBA", (32, 48), (249, 255, 255, 255)).save(cards / (face + ".png"))
        manifest = self.export(cards)
        skins = list((self.output / "skins").glob("*.png"))
        self.assertEqual(1, len(skins))
        self.assertEqual(manifest["faces"]["first"], manifest["faces"]["second"])
        self.assertEqual(hashlib.sha256(skins[0].read_bytes()).hexdigest(), skins[0].stem)
        self.assertNotIn("textures.minecraft.net", json.dumps(manifest))

    def test_invalid_dimensions_and_transparency_are_rejected(self):
        cards = Path(self.workspace.name) / "cards"
        cards.mkdir()
        for size, color, expected in (((16, 24), (255, 255, 255, 255), "32x48"),
                                      ((32, 48), (255, 255, 255, 0), "opaque")):
            Image.new("RGBA", size, color).save(cards / "invalid.png")
            result = self.command(cards)
            self.assertNotEqual(0, result.returncode)
            self.assertIn(expected, result.stderr)

if __name__ == "__main__":
    unittest.main()
