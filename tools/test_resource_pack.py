"""Asset contract tests; run after build-resource-pack.py."""
import json
import math
import io
import unittest
import zipfile
from pathlib import Path
from PIL import Image

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

    def test_mahjong_has_colored_backs_on_every_physical_tile(self):
        for name in ("mahjong_m1", "mahjong_p0", "mahjong_s1", "mahjong_z1", "mahjong_f1", "mahjong_back"):
            model = json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            self.assertIn("back", model["textures"], name)
            for element in model["elements"]:
                self.assertEqual("#back", element["faces"]["north"]["texture"])
            if name == "mahjong_back":
                front = "assets/" + model["textures"]["face"].replace(":", "/textures/") + ".png"
                back = "assets/" + model["textures"]["back"].replace(":", "/textures/") + ".png"
                self.assertEqual(self.archive.read(front), self.archive.read(back))

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

    def test_tile_and_card_faces_point_to_owner_and_up_when_flat(self):
        # Minecraft 26.2 ignores display.none; ItemTransforms reads fixed.
        # ItemDisplay adds Y180, then the Java -90 X lays the face upward.
        for name in ("mahjong_m1", "mahjong_z1", "card_r3", "card_wild", "button_b"):
            model = json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            self.assertNotIn("none", model.get("display", {}), "NONE cannot carry a model transform")
            rotation = model.get("display", {}).get("fixed", {}).get("rotation", [0, 0, 0])
            self.assertEqual(0, rotation[0])
            self.assertEqual(0, rotation[2])
            effective_y = math.radians(rotation[1] + 180)
            front_z = math.cos(effective_y)
            self.assertGreater(front_z, .999, name + " must face the owner, not the back")
            flat_y = -math.sin(-math.pi / 2) * front_z
            self.assertGreater(flat_y, .999, name + " must face upward after laying flat")

    def test_ring_arrows_have_smooth_alpha_edges_and_no_detached_fragments(self):
        for name in ("ring_1", "ring_-1"):
            im = Image.open(io.BytesIO(self.archive.read(
                f"assets/tabletop3d/textures/item/surface/{name}.png"))).convert("RGBA")
            self.assertTrue(any(0 < alpha < 255 for alpha in im.getchannel("A").getdata()),
                            "Curved arrows need filtered edges instead of jagged polygon seams")
            opaque = {point for point in ((x,y) for y in range(im.height) for x in range(im.width))
                      if im.getpixel(point)[3] >= 128}
            components = []
            while opaque:
                pending = [opaque.pop()]; size = 0
                while pending:
                    x,y = pending.pop(); size += 1
                    for neighbor in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                        if neighbor in opaque:opaque.remove(neighbor);pending.append(neighbor)
                components.append(size)
            self.assertEqual(4, len(components), "Four continuous arrows, without stray fragments")

    def test_forward_ring_arrowheads_follow_increasing_seat_order(self):
        # Seats advance from +Z toward +X: counterclockwise in a +X/+Z UV plane.
        # These samples lie on arrowhead wings, outside the circular arc itself.
        for direction, name in ((1, "ring_1"), (-1, "ring_-1")):
            image = Image.open(io.BytesIO(self.archive.read(
                f"assets/tabletop3d/textures/item/surface/{name}.png"))).convert("RGBA")
            point = (484, 159) if direction == 1 else (484, 353)
            self.assertGreater(image.getpixel(point)[3], 0, name + " arrowhead is reversed")

    def test_card_art_is_crisp_pixel_art(self):
        for name in ("card_r3", "card_b6", "card_y9", "card_p4", "card_wild", "card_rdraw", "card_breverse", "card_yskip"):
            image = Image.open(io.BytesIO(self.archive.read(
                f"assets/tabletop3d/textures/item/face/{name}.png"))).convert("RGB")
            self.assertLessEqual(len(image.getcolors(image.width * image.height)), 20, name)

    def test_round_table_has_a_continuous_polygon_wall_instead_of_parallel_strips(self):
        model = json.loads(self.archive.read("assets/tabletop3d/models/item/card_table.json"))
        rotations = {round(element.get("rotation", {}).get("angle", 0), 4)
                     for element in model["elements"] if "south" in element["faces"]}
        self.assertGreaterEqual(len(rotations), 64, "Round perimeter must have enough distinct side normals")
        self.assertLessEqual(sum(len(e["faces"]) for e in model["elements"]), 400,
                             "A smoother silhouette must not multiply hidden interior faces")


if __name__ == "__main__":
    unittest.main()
