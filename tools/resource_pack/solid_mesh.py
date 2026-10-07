"""Exterior-only polygon shells and masked caps for vanilla item models."""
import math


def circle(radius, segments=32, squash=1, center=(0, 0)):
    return [(center[0] + radius * math.cos(i * math.tau / segments),
             center[1] + radius * squash * math.sin(i * math.tau / segments))
            for i in range(segments)]


def rounded_outline(radius, corner, squash=1, steps=6):
    points = []
    for quadrant, (x, z) in enumerate(((1, 1), (-1, 1), (-1, -1), (1, -1))):
        for step in range(steps + 1):
            angle = (quadrant + step / steps) * math.pi / 2
            points.append((x * (radius - corner) + corner * math.cos(angle),
                           (z * (radius - corner) + corner * math.sin(angle)) * squash))
    return points


def shell(outline, y, height, side, top, holes=()):
    """A cap pair plus one outward quad per contour edge; no interior slice walls."""
    xs, zs = zip(*outline)
    left, right, near, far = min(xs), max(xs), min(zs), max(zs)
    mask = {"outline": outline, "holes": holes, "bounds": (left, near, right, far)}
    parts = [{"from": [8 + left * 16, 8 + y * 16, 8 + near * 16],
              "to": [8 + right * 16, 8 + (y + height) * 16, 8 + far * 16],
              "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#" + top, "_mask": mask},
                        "down": {"uv": [0, 16, 16, 0], "texture": "#" + side, "_mask": mask}}}]
    for contour in [outline] + [list(reversed(hole)) for hole in holes]:
        for start, end in zip(contour, contour[1:] + contour[:1]):
            dx, dz = end[0] - start[0], end[1] - start[1]
            length = math.hypot(dx, dz) * 16
            x, z = 8 + (start[0] + end[0]) * 8, 8 + (start[1] + end[1]) * 8
            along_x = abs(dx) >= abs(dz)
            parts.append({"from": [x - length / 2 if along_x else x, 8 + y * 16,
                                   z if along_x else z - length / 2],
                          "to": [x + length / 2 if along_x else x, 8 + (y + height) * 16,
                                 z if along_x else z + length / 2],
                          "rotation": {"origin": [x, 8, z], "axis": "y",
                                       "angle": math.degrees(math.atan2(-dz, dx) if along_x else math.atan2(dx, dz))},
                          "faces": {"north" if along_x else "east":
                                    {"uv": [0, 0, 16, 16], "texture": "#" + side}}})
    return parts


def extrusion(radius, y, height, side, top, bands=32, corner=None, squash=1,
              inner_radius=None, inner_corner=None):
    outline = (rounded_outline(radius, corner, squash) if corner is not None else
               circle(radius, min(48, max(16, bands)), squash))
    holes = [] if inner_radius is None else [rounded_outline(inner_radius, inner_corner, squash)]
    return shell(outline, y, height, side, top, holes)


def upright(parts, lift=0):
    """Exchange Y/Z, including the reflected rotation and cap UV orientation."""
    for part in parts:
        for key in ("from", "to"):
            x, y, z = part[key]
            part[key] = [x, z + lift, y]
        part["faces"] = {{"up": "south", "down": "north", "north": "down", "south": "up"}.get(k, k): v
                         for k, v in part["faces"].items()}
        for face in part["faces"].values():
            if "_mask" in face:
                face["uv"] = [0, 16, 16, 0]
        if "rotation" in part:
            rotation = part["rotation"]
            x, y, z = rotation["origin"]
            rotation.update(origin=[x, z + lift, y], axis="z", angle=-rotation["angle"])
    return parts


def rescale(parts, scale, offset=(0, 0, 0)):
    for part in parts:
        for key in ("from", "to"):
            part[key] = [8 + (value - 8) * scale + offset[axis] for axis, value in enumerate(part[key])]
        if "rotation" in part:
            part["rotation"]["origin"] = [8 + (value - 8) * scale + offset[axis]
                                           for axis, value in enumerate(part["rotation"]["origin"])]
    return parts


def bake_caps(textures, parts, assets, save_texture):
    """Bake contour masks into a shared sprite per source texture and outline."""
    import hashlib
    import json
    from PIL import Image, ImageChops, ImageDraw, ImageFilter

    textures = dict(textures)
    for part in parts:
        for face in part["faces"].values():
            mask = face.pop("_mask", None)
            if mask is None:
                continue
            source = textures[face["texture"][1:]]
            left, near, right, far = mask["bounds"]
            normalized = {key: [[round((x - left) / (right - left), 8),
                                  round((z - near) / (far - near), 8)] for x, z in mask[key]]
                          for key in ("outline",)}
            normalized["holes"] = [[[round((x - left) / (right - left), 8),
                                      round((z - near) / (far - near), 8)] for x, z in hole]
                                    for hole in mask["holes"]]
            identity = hashlib.sha256((source + json.dumps(normalized, sort_keys=True)).encode()).hexdigest()[:16]
            alias = "cap_" + face["texture"][1:] + "_" + identity
            if alias not in textures:
                image = Image.open(assets / ("textures/" + source.split(":", 1)[1] + ".png")).convert("RGBA")
                minimum = 512 if mask["holes"] else 256
                image = image.resize((max(minimum, image.width), max(minimum, image.height)), Image.Resampling.NEAREST)
                alpha = Image.new("L", image.size)
                draw = ImageDraw.Draw(alpha)
                def points(contour):
                    return [((x - left) / (right - left) * image.width,
                             (z - near) / (far - near) * image.height) for x, z in contour]
                draw.polygon(points(mask["outline"]), fill=255)
                for hole in mask["holes"]:
                    draw.polygon(points(hole), fill=0)
                # Cover boundary texels conservatively so the cap meets the exact polygon wall.
                alpha = alpha.filter(ImageFilter.MaxFilter(3))
                image.putalpha(ImageChops.multiply(image.getchannel("A"), alpha))
                textures[alias] = save_texture("surface/cap-" + identity, image)
            face["texture"] = "#" + alias
    used = {face["texture"][1:] for part in parts for face in part["faces"].values()}
    return {key: value for key, value in textures.items() if key in used}
