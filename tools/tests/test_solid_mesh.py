"""Geometric closure checks independent of the exported face budgets."""
import copy
import math
import unittest
from tools.resource_pack.solid_mesh import circle, shell, upright, rescale


def rotate(point, rotation):
    result = [value - origin for value, origin in zip(point, rotation["origin"])]
    angle = math.radians(rotation["angle"])
    cosine, sine = math.cos(angle), math.sin(angle)
    x, y, z = result
    result = ([x * cosine + z * sine, y, -x * sine + z * cosine]
              if rotation["axis"] == "y" else
              [x * cosine - y * sine, x * sine + y * cosine, z])
    return [value + origin for value, origin in zip(result, rotation["origin"])]


class SolidMeshTest(unittest.TestCase):
    def test_perimeter_edges_join_exactly_including_hole_walls(self):
        outline, hole = circle(1.5, 48), circle(.12, 24, center=(.3, -.2))
        parts = shell(outline, -.1, .2, "side", "top", [hole])
        edges = list(zip(outline, outline[1:] + outline[:1]))
        reversed_hole = list(reversed(hole))
        edges += list(zip(reversed_hole, reversed_hole[1:] + reversed_hole[:1]))
        for part, (start, end) in zip(parts[1:], edges):
            a, b = part["from"][:], part["to"][:]
            b[1] = a[1]
            actual = [rotate(a, part["rotation"]), rotate(b, part["rotation"])]
            expected = [[8 + p[0] * 16, a[1], 8 + p[1] * 16] for p in (start, end)]
            for point in actual:
                self.assertLess(min(math.dist(point, target) for target in expected), 1e-10)
            direction = next(iter(part["faces"]))
            normal = [0, 0, -1] if direction == "north" else [1, 0, 0]
            rotation = dict(part["rotation"], origin=[0, 0, 0])
            normal = rotate(normal, rotation)
            outward = [end[1] - start[1], 0, start[0] - end[0]]
            self.assertGreater(sum(a * b for a, b in zip(normal, outward)), 0)

    def test_upright_and_rescale_preserve_transformed_vertices(self):
        original = shell(circle(.5, 24), -.1, .2, "side", "top")
        standing = rescale(upright(copy.deepcopy(original), lift=7), 2, (1, 2, 3))
        for before, after in zip(original[1:], standing[1:]):
            for key in ("from", "to"):
                point = rotate(before[key], before["rotation"])
                point = [point[0], point[2] + 7, point[1]]
                expected = [8 + (v - 8) * 2 + offset for v, offset in zip(point, (1, 2, 3))]
                self.assertLess(math.dist(expected, rotate(after[key], after["rotation"])), 1e-10)


if __name__ == "__main__":
    unittest.main()
