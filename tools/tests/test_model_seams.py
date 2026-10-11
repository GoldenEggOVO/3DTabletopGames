import json
import math
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEMS = {path.stem: path for path in (ROOT / "resource-pack/assets/tabletop3d/items").rglob("*.json")}


def rotate(point, rotation):
    x, y, z = [value - origin for value, origin in zip(point, rotation["origin"])]
    angle = math.radians(rotation["angle"])
    cosine, sine = math.cos(angle), math.sin(angle)
    result = ([x * cosine + z * sine, y, -x * sine + z * cosine]
              if rotation["axis"] == "y" else
              [x * cosine - y * sine, x * sine + y * cosine, z])
    return [value + origin for value, origin in zip(result, rotation["origin"])]


class ModelSeamsTest(unittest.TestCase):
    def check_perimeters(self, name, axis):
        item = json.loads(ITEMS[name].read_text())
        path = ROOT / "resource-pack/assets" / (item["model"]["model"].replace(":", "/models/") + ".json")
        model = json.loads(path.read_text())
        endpoints = Counter()
        for part in model["elements"]:
            if part.get("rotation", {}).get("axis") != axis or len(part["faces"]) != 1:
                continue
            a, b = part["from"][:], part["to"][:]
            b[1 if axis == "y" else 2] = a[1 if axis == "y" else 2]
            for point in (a, b):
                endpoints[tuple(round(value, 7) for value in rotate(point, part["rotation"]))] += 1
        self.assertTrue(endpoints, name)
        self.assertEqual({2}, set(endpoints.values()), name + " has an open contour or duplicate wall")

    def test_horizontal_shell_walls_join_at_the_same_vertices(self):
        for name in ("card_table", "mahjong_table", "stone_black", "chess_black_pawn", "poker_chips"):
            with self.subTest(model=name):
                self.check_perimeters(name, "y")

    def test_card_and_connect_four_hole_walls_are_closed(self):
        for name in ("playing_spades_ace", "card_r1", "board_connectfour"):
            with self.subTest(model=name):
                self.check_perimeters(name, "z")


if __name__ == "__main__":
    unittest.main()
