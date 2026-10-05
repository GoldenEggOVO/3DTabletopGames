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
    def test_hole_mask_does_not_remove_pixels_from_the_solid_frame(self):
        from solid_mesh import circle
        model = json.loads(self.archive.read("assets/tabletop3d/models/item/board_connectfour.json"))
        cap = next(e for e in model["elements"] if e["faces"].get("south", {}).get("texture", "").startswith("#cap_"))
        image = self.face_image(model, cap["faces"]["south"])
        hole = circle(.119 / 2, 24, center=(-3 * .14, (.17 - .88) / 2))
        for px in range(int(.01 * image.width), int(.15 * image.width)):
            for py in range(int(.01 * image.height), int(.15 * image.height)):
                x, z = (px + .5) / image.width - .5, (py + .5) / image.height * .86 - .43
                inside = all((b[0] - a[0]) * (z - a[1]) - (b[1] - a[1]) * (x - a[0]) >= 0
                             for a, b in zip(hole, hole[1:] + hole[:1]))
                if not inside:
                    self.assertEqual(255, image.getpixel((px, py))[3], "Cap must reach the exact hole wall")

    def test_curved_models_only_spend_faces_on_the_exterior(self):
        budgets = {"card_table": 150, "mahjong_table": 150, "board_connectfour": 1500,
                   "board_chess": 110, "chess_black_pawn": 120, "stone_black": 40,
                   "poker_chips": 24, "card_r3": 30}
        for name, maximum in budgets.items():
            data = json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            self.assertLessEqual(sum(len(e["faces"]) for e in data["elements"]), maximum,
                                 name + " must not draw the internal faces of solid slices")

    def face_image(self, model, face):
        path = "assets/" + model["textures"][face["texture"][1:]].replace(":", "/textures/") + ".png"
        return Image.open(io.BytesIO(self.archive.read(path))).convert("RGBA")

    def test_curved_shells_have_valid_exterior_quads_and_opaque_walls(self):
        for name in ("card_table", "doudizhu_table", "liars_bar_table", "texas_holdem_table",
                     "stone_black", "chess_black_pawn", "poker_chips", "board_connectfour"):
            data = json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            for element in data["elements"]:
                spans = [high - low for low, high in zip(element["from"], element["to"])]
                for direction, face in element["faces"].items():
                    axis = {"north": 2, "south": 2, "east": 0, "west": 0, "up": 1, "down": 1}[direction]
                    self.assertTrue(all(span > 0 for i, span in enumerate(spans) if i != axis), name)
                    image = self.face_image(data, face)
                    if face["texture"].startswith("#cap_"):
                        self.assertEqual(255, image.getchannel("A").getextrema()[1], name)
                    else:
                        self.assertEqual((255,255), image.getchannel("A").getextrema(), name)
                    self.assertNotIn("_mask", face)

    def test_board_playing_surface_has_rounded_geometry_inside_the_table_border(self):
        for name in ("chess", "xiangqi", "gomoku", "go", "ludo", "checkers"):
            data = json.loads(self.archive.read(f"assets/tabletop3d/models/item/board_{name}.json"))
            tops = [element for element in data["elements"] if
                    element["faces"].get("up",{}).get("texture", "").startswith("#cap_board_")]
            self.assertEqual(1, len(tops), name)
            self.assertTrue(all(max(element["to"][0], element["to"][2]) <= 24 for element in tops))
            self.assertTrue(all(min(element["from"][0], element["from"][2]) >= -8 for element in tops))
            image = self.face_image(data, tops[0]["faces"]["up"])
            self.assertEqual(0, image.getpixel((0,0))[3], name + " corner must follow the rounded border")
            self.assertEqual(255, image.getpixel((image.width//2,image.height//2))[3], name)

    def test_landlord_cloth_has_the_bottom_card_marks_at_the_actual_centre(self):
        cloth = Image.open(io.BytesIO(self.archive.read("assets/tabletop3d/textures/item/surface/doudizhu_table.png"))).convert("RGB")
        for x in (210, 256, 302):
            self.assertEqual((36, 90, 64), cloth.getpixel((x, 256)))
        self.assertEqual((40, 99, 70), cloth.getpixel((256, 190)))

    def test_retired_card_audio_is_absent_and_one_shared_play_sample_is_registered(self):
        sounds = json.loads(self.archive.read("assets/tabletop3d/sounds.json"))
        self.assertFalse(any(event.startswith("doudizhu.") for event in sounds))
        self.assertFalse(any(name.startswith("assets/tabletop3d/sounds/doudizhu/") for name in self.names))
        self.assertFalse(any(event.startswith("liars-bar.") for event in sounds))
        self.assertFalse(any(name.startswith("assets/tabletop3d/sounds/liars-bar/") for name in self.names))
        self.assertEqual({"sounds": [{"name": "tabletop3d:cards/play", "stream": False}]}, sounds["cards.play"])

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
        rails = [e for e in table["elements"] if e["faces"]["up"]["texture"] == "#wood"
                 and abs((e["to"][1] - e["from"][1]) / 8 - .10) < 1e-6]
        self.assertEqual(4, len(rails))
        self.assertTrue(all((e["from"][0] + e["to"][0] - 16) / 16 > -.8 for e in rails))

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

    def test_physical_and_interface_samples_include_variations_and_their_cc0_licenses(self):
        sounds = json.loads(self.archive.read("assets/tabletop3d/sounds.json"))
        for event in ("board.wood", "board.stone", "board.drop", "board.flip", "board.capture",
                      "dice.roll", "dice.single", "dice.hold", "cards.draw", "chips.bet",
                      "table.select", "table.pass", "table.confirm", "table.start", "table.win",
                      "table.draw", "table.turn", "table.home"):
            self.assertIn(event, sounds)
        self.assertGreaterEqual(len(sounds["dice.roll"]["sounds"]), 3)
        self.assertGreaterEqual(len(sounds["chips.bet"]["sounds"]), 3)
        for pack in ("casino", "interface", "impact"):
            self.assertIn(b"CC0", self.archive.read(f"licenses/kenney-{pack}.txt"))

    def test_sounds_have_real_ogg_payloads(self):
        sounds = json.loads(self.archive.read("assets/tabletop3d/sounds.json"))
        self.assertEqual(28, len(sounds))
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
            back_face = model["elements"][0]["faces"]["north"]
            for element in model["elements"]:
                if "north" in element["faces"]:
                    self.assertTrue(element["faces"]["north"]["texture"].startswith("#cap_back_"))
            back = self.face_image(model, back_face)
            self.assertEqual(255, back.getpixel((back.width // 2, back.height // 2))[3])
            self.assertEqual(0, back.getpixel((0, 0))[3])

    def test_shell_textures_fit_the_atlas_budget_without_unused_aliases(self):
        paths = set()
        for name in self.names:
            if name.startswith("assets/tabletop3d/models/item/"):
                model = json.loads(self.archive.read(name))
                used = {face["texture"][1:] for element in model["elements"] for face in element["faces"].values()}
                self.assertEqual(used, set(model["textures"]), name)
                paths.update(model["textures"].values())
        pixels = 0
        for path in paths:
            image = Image.open(io.BytesIO(self.archive.read("assets/" + path.replace(":", "/textures/") + ".png")))
            pixels += image.width * image.height
        self.assertLess(pixels, 40_000_000, "Geometry savings must not inflate the texture atlas")

    def test_connect_four_has_real_open_holes_and_a_sealed_frame(self):
        data=json.loads(self.archive.read("assets/tabletop3d/models/item/board_connectfour.json"))
        front = data["elements"][0]
        image = self.face_image(data, front["faces"]["south"])
        def opaque(x,y):
            u=(x-front["from"][0])/(front["to"][0]-front["from"][0])
            v=1-(y-front["from"][1])/(front["to"][1]-front["from"][1])
            uv=front["faces"]["south"]["uv"]
            u=(uv[0]+u*(uv[2]-uv[0]))/16
            v=(uv[1]+v*(uv[3]-uv[1]))/16
            return image.getpixel((int(u*image.width),int(v*image.height)))[3]>0
        for row in range(6):
            for col in range(7):
                self.assertFalse(opaque(8+(col-3)*.28*8,8+(.17+row*.28)*8))
        for row in range(6):
            for col in range(6):
                self.assertTrue(opaque(8+(col-2.5)*.28*8,8+(.17+row*.28)*8))
        self.assertEqual(42*24+28, sum("rotation" in e for e in data["elements"][:1037]))

    def test_round_pieces_have_geometric_silhouettes_including_vertical_discs(self):
        for name in ("stone_black", "draught_white", "xiangqi_red_general", "chess_black_pawn", "reversi_disc", "connectfour_red", "poker_chips"):
            data=json.loads(self.archive.read(f"assets/tabletop3d/models/item/{name}.json"))
            self.assertTrue(any("rotation" in e for e in data["elements"]), name)
            first=data["elements"][0]
            face=first["faces"]["south" if name.startswith("connectfour_") else "up"]
            image=self.face_image(data,face)
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
        cap=next(e["faces"]["up"] for e in raised if "up" in e["faces"])
        image=self.face_image(model,cap)
        self.assertEqual(0,image.getpixel((image.width//2,image.height//2))[3],
                         "Raised rim must leave a real open playing area")

    def test_round_table_is_sealed_with_a_fine_silhouette_and_bounded_geometry(self):
        data=json.loads(self.archive.read("assets/tabletop3d/models/item/card_table.json"))
        body=[element for element in data["elements"] if abs(element["to"][1]-8)<1e-6]
        self.assertEqual(49,len(body))
        image=self.face_image(data,body[0]["faces"]["up"])
        self.assertEqual(0,image.getpixel((0,0))[3])
        self.assertEqual(255,image.getpixel((image.width//2,image.height//2))[3])
        self.assertLessEqual(1.5*(1-math.cos(math.pi/48)),.0033,
                             "The exterior silhouette must stay close to the original radius")


if __name__ == "__main__":
    unittest.main()
