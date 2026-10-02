"""Asset contract tests; run after build-resource-pack.py."""
import json
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ResourcePackTest(unittest.TestCase):
    def setUp(self):
        self.archive = zipfile.ZipFile(ROOT / "target/tabletop-resource-pack.zip")
        self.addCleanup(self.archive.close)
        self.names = set(self.archive.namelist())

    def test_catalog_and_namespace(self):
        manifest = json.loads((ROOT / "target/resource-pack-manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(111, len(manifest["models"]))
        for model in manifest["models"]:
            item = json.loads(self.archive.read(f"assets/tabletop3d/items/{model}.json"))
            self.assertEqual("minecraft:model", item["model"]["type"])
            self.assertEqual(f"tabletop3d:item/{model}", item["model"]["model"])
        self.assertFalse(any(n.startswith("assets/") and not n.startswith("assets/tabletop3d/") for n in self.names))
        self.assertEqual(88, json.loads(self.archive.read("pack.mcmeta"))["pack"]["pack_format"])

    def test_geometry_and_all_texture_references(self):
        for path in self.names:
            if not path.startswith("assets/tabletop3d/models/"):
                continue
            model = json.loads(self.archive.read(path))
            for texture in model["textures"].values():
                self.assertTrue(texture.startswith("tabletop3d:item/"), "Item atlas must discover every texture")
                self.assertIn("assets/" + texture.replace(":", "/textures/") + ".png", self.names)
            for element in model["elements"]:
                self.assertTrue(all(-16 <= value <= 32 for value in element["from"] + element["to"]), path)
                self.assertTrue(all(a <= b for a, b in zip(element["from"], element["to"])), path)
                for face in element["faces"].values():
                    self.assertIn(face["texture"][1:], model["textures"])
                    self.assertTrue(all(0 <= v <= 16 for v in face["uv"]), path)

    def test_sounds_have_real_ogg_payloads(self):
        sounds = json.loads(self.archive.read("assets/tabletop3d/sounds.json"))
        self.assertEqual(9, len(sounds))
        for event in sounds.values():
            name = event["sounds"][0]["name"]
            path = "assets/" + name.replace(":", "/sounds/") + ".ogg"
            self.assertIn(path, self.names)
            self.assertTrue(self.archive.read(path).startswith(b"OggS"))


if __name__ == "__main__":
    unittest.main()
