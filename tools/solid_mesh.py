"""Closed, opaque extrusions with shared boundaries and mapped top UVs."""
import math


def rounded_width(radius, corner, position):
    if abs(position) <= radius - corner:
        return radius
    offset = abs(position) - radius + corner
    return radius - corner + math.sqrt(max(0, corner * corner - offset * offset))


def extrusion(radius, y, height, side, top, bands=96, corner=None, squash=1,
              inner_radius=None, inner_corner=None):
    """Use solid strips so raster alpha can never uncover a perimeter seam."""
    parts = []
    depth = radius * squash
    for index in range(bands):
        z0 = -depth + 2 * depth * index / bands
        z1 = -depth + 2 * depth * (index + 1) / bands
        z = (z0 + z1) / (2 * squash)
        half = (rounded_width(radius, corner, z) if corner is not None else
                math.sqrt(max(0, radius * radius - z * z)))
        intervals = [(-half, half)]
        if inner_radius is not None and abs(z) < inner_radius:
            inner = rounded_width(inner_radius, inner_corner, z)
            intervals = [(-half, -inner), (inner, half)]
        for x0, x1 in intervals:
            faces = {direction: {"uv": [0, 0, 16, 16], "texture": "#" + side}
                     for direction in ("north", "south", "west", "east", "down")}
            faces["up"] = {"uv": [8 + x0 / radius * 8, 8 + z0 / depth * 8,
                                   8 + x1 / radius * 8, 8 + z1 / depth * 8],
                           "texture": "#" + top}
            parts.append({"from": [8 + x0 * 16, 8 + y * 16, 8 + z0 * 16],
                          "to": [8 + x1 * 16, 8 + (y + height) * 16, 8 + z1 * 16],
                          "faces": faces})
    return parts
