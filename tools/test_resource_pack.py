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
    def test_landlord_cloth_has_the_bottom_card_marks_at_the_actual_centre(self):
        cloth = Image.open(io.BytesIO(self.archive.read("assets/tabletop3d/textures/item/surface/doudizhu_table.png"))).convert("RGB")
        for x in (210, 256, 302):
            self.assertEqual((36, 90, 64), cloth.getpixel((x, 256)))
        self.assertEqual((40, 99, 70), cloth.getpixel((256, 190)))

    def test_every_landlord_combination_and_liars_result_has_a_sound_event(self):
        sounds = json.loads(self.archive.read("assets/tabletop3d/sounds.json"))
        combinations = ("single", "pair", "triple", "triple-single", "triple-pair", "straight", "pair-straight",
                        "triple-straight", "airplane-single", "airplane-pair", "four-single", "four-pair", "bomb", "rocket")
        payloads = []
        for event in ["doudizhu." + name for name in combinations] + ["doudizhu.pass", "doudizhu.no-beat", "liars-bar.challenge", "liars-bar.shot"]:
            name = sounds[event]["sounds"][0]["name"]
            payloads.append(self.archive.read("assets/" + name.replace(":", "/sounds/") + ".ogg"))
        self.assertEqual(len(payloads), len(set(payloads)))

    def test_yacht_die_has_six_opposing_faces_and_the_table_has_five_slots(self):
        die = json.loads(self.archive.read("assets/tabletop3d/models/item/yacht_die.json"))
        self.assertEqual(1, len(die["elements"]))
        self.assertEqual({"up":"#one", "east":"#two", "south":"#three", "north":"#four", "west":"#five", "down":"#six"},
                         {side:face["texture"] for side,face in die["elements"][0]["faces"].items()})
        table = json.loads(self.archive.read("assets/tabletop3d/models/item/yacht_table.json"))
        slots = [e for e in table["elements"] if e["faces"]["up"]["texture"] == "#slot"]
        self.assertEqual(5, len(slots))
        self.assertEqual([round((.32+(i-2)*.26)*8+8,5) for i in range(5)],
                         [round((e["from"][0]+e["to"][0])/2,5) for e in slots])

    def test_bundled_checksum_matches_shipped_pack(self):
        import hashlib
        expected=(ROOT/"src/main/resources/resource-pack.sha1").read_text(encoding="ascii").strip()
        self.assertEqual(hashlib.sha1((ROOT/"target/tabletop-resource-pack.zip").read_bytes()).hexdigest(),expected)

    def setUp(self):
        self.archive = zipfile.ZipFile(ROOT / "target/tabletop-resource-pack.zip")
        self.addCleanup(self.archive.close)
        self.names = set(self.archive.namelist())

    def test_catalog_and_namespace(self):
        manifest = json.loads((ROOT / "target/resource-pack-manifest.json").read_text(encoding="utf-8"))
        self.assertEqual(len(manifest["models"]), len(set(manifest["models"])))
        for kind in ("chess", "connectfour", "xiangqi", "gomoku", "go", "go9", "go13", "reversi", "draughts", "checkers", "ludo"):
            self.assertIn("board_" + kind, manifest["models"])
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
        self.assertEqual(29, len(sounds))
        for event in sounds.values():
            name = event["sounds"][0]["name"]
            path = "assets/" + name.replace(":", "/sounds/") + ".ogg"
            self.assertIn(path, self.names)
            self.assertTrue(self.archive.read(path).startswith(b"OggS"))

    def test_standard_playing_cards_have_complete_faces_and_opaque_backs(self):
        ranks = ["ace"] + [str(n) for n in range(2, 11)] + ["jack", "queen", "king"]
        names = ["playing_" + suit + "_" + rank for suit in ("spades", "hearts", "diamonds", "clubs") for rank in ranks]
        names += ["playing_joker_small", "playing_joker_big", "playing_back"]
        for name in names:
            model = json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            self.assertIn("back", model["textures"])
            for element in model["elements"]:
                self.assertEqual("#back", element["faces"]["north"]["texture"])
            back = Image.open(io.BytesIO(self.archive.read("assets/" + model["textures"]["back"].replace(":", "/textures/") + ".png"))).convert("RGBA")
            self.assertEqual((255,255), back.getchannel("A").getextrema())

    def test_connect_four_holes_are_transparent_and_not_an_opaque_picture(self):
        image = Image.open(io.BytesIO(self.archive.read("assets/tabletop3d/textures/item/surface/connectfour-rack.png"))).convert("RGBA")
        self.assertEqual(0,image.getpixel((256,258))[3])
        self.assertTrue(any(e.get("rotation",{}).get("axis")=="z" for e in json.loads(
            self.archive.read("assets/tabletop3d/models/item/board_connectfour.json"))["elements"]),
            "Hole walls need real depth")

    def test_round_piece_caps_are_round_including_vertical_discs(self):
        for name in ("stone_black", "draught_white", "xiangqi_red_general", "chess_black_pawn", "reversi_disc", "connectfour_red", "poker_chips"):
            data=json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            caps=[face for part in data["elements"] for face in part["faces"].values()
                  if face["texture"].endswith("_cap") or face["texture"]=="#engraving"]
            self.assertTrue(caps,name)
            for cap in caps:
                path="assets/"+data["textures"][cap["texture"][1:]].replace(":","/textures/")+".png"
                image=Image.open(io.BytesIO(self.archive.read(path))).convert("RGBA")
                self.assertEqual(0,image.getpixel((0,0))[3],name)
                self.assertEqual(255,image.getpixel((image.width//2,image.height//2))[3],name)

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

    def test_table_edges_are_solid_and_mahjong_rim_leaves_the_cloth_open(self):
        wood = Image.open(io.BytesIO(self.archive.read(
            "assets/tabletop3d/textures/item/surface/wood.png"))).convert("RGB")
        self.assertEqual(1, len(wood.getcolors(wood.width * wood.height)))
        model = json.loads(self.archive.read("assets/tabletop3d/models/item/mahjong_table.json"))
        raised = [e for e in model["elements"] if e["to"][1] > 8.8]
        self.assertTrue(raised, "Mahjong rim must stand above the playing surface")
        top = next(e["faces"]["up"] for e in raised if "up" in e["faces"])
        rim_path = "assets/" + model["textures"][top["texture"][1:]].replace(":", "/textures/") + ".png"
        rim = Image.open(io.BytesIO(self.archive.read(rim_path))).convert("RGBA")
        self.assertEqual(0, rim.getpixel((512, 512))[3], "Raised rim must not cover the playing area")
        self.assertEqual(255, rim.getpixel((512, 10))[3])

    def test_round_table_has_a_continuous_polygon_wall_instead_of_parallel_strips(self):
        model = json.loads(self.archive.read("assets/tabletop3d/models/item/card_table.json"))
        rotations = {round(element.get("rotation", {}).get("angle", 0), 4)
                     for element in model["elements"] if "south" in element["faces"]}
        self.assertGreaterEqual(len(rotations), 64, "Round perimeter must have enough distinct side normals")
        self.assertLessEqual(sum(len(e["faces"]) for e in model["elements"]), 400,
                             "A smoother silhouette must not multiply hidden interior faces")


if __name__ == "__main__":
    unittest.main()
