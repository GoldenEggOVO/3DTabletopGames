"""Import the original Casino Blackjack block artwork without a runtime dependency."""
import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path


SUITS = ("spades", "hearts", "clubs", "diamonds")
RANKS = ("ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king")


def coverage(boxes):
    """Canonical horizontal bands describe the union, independent of rectangle splits."""
    layers = defaultdict(list)
    for box in boxes:
        a, b = box["from"], box["to"]
        points = tuple(round(value * 1_000_000) for value in (*a, *b))
        layers[(box["material"], points[2], points[5])].append(points)
    bands = []
    for (material, z1, z2), rectangles in sorted(layers.items()):
        ys = sorted({value for rect in rectangles for value in (rect[1], rect[4])})
        previous = None
        for y1, y2 in zip(ys, ys[1:]):
            intervals = sorted((rect[0], rect[3]) for rect in rectangles
                               if rect[1] <= y1 and rect[4] >= y2)
            merged = []
            for x1, x2 in intervals:
                if merged and x1 <= merged[-1][1]:
                    merged[-1][1] = max(merged[-1][1], x2)
                else:
                    merged.append([x1, x2])
            if previous is not None and previous[2] == merged:
                previous[1] = y2
            else:
                previous = [y1, y2, merged]
                bands.append((material, z1, z2, previous))
    return ";".join(f"{material}:{x1},{band[0]},{z1},{x2},{band[1]},{z2}"
                    for material, z1, z2, band in bands for x1, x2 in band[2])


def compact(boxes):
    """Merge only exactly adjoining cuboids with identical material and depth."""
    boxes = [dict(box, **{"from": list(box["from"]), "to": list(box["to"])}) for box in boxes]
    changed = True
    while changed:
        changed = False
        for axis in (0, 1):
            groups = defaultdict(list)
            others = [index for index in range(3) if index != axis]
            for box in boxes:
                key = (box["material"], *(box[side][index] for index in others for side in ("from", "to")))
                groups[key].append(box)
            result = []
            for group in groups.values():
                group.sort(key=lambda box: box["from"][axis])
                current = group[0]
                for following in group[1:]:
                    if current["to"][axis] == following["from"][axis]:
                        current["to"][axis] = following["to"][axis]
                        changed = True
                    else:
                        result.append(current)
                        current = following
                result.append(current)
            boxes = result
    # The body remains first so existing selection and lifecycle code retain its anchor.
    return sorted(boxes, key=lambda box: (box["from"][2], box["material"], box["from"], box["to"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    reference = json.loads(args.reference.read_text(encoding="utf-8-sig"))
    models, fingerprints = {}, {}
    original_count = merged_count = 0
    for index in range(53):
        face = "back" if index == 52 else f"{SUITS[index // 13]}_{RANKS[index % 13]}"
        source = reference[f"card_{index}"]
        assert not source["labels"]
        assert all(set(box) == {"from", "to", "material"} for box in source["boxes"])
        boxes = compact(source["boxes"])
        expected = coverage(source["boxes"])
        assert coverage(boxes) == expected, face
        models[face] = boxes
        coordinates = [sorted({round(box[side][axis] * 1_000_000)
                               for box in source["boxes"] for side in ("from", "to")})
                       for axis in range(3)]
        fingerprints[face] = {"count": len(boxes), "coordinates": coordinates,
                              "sha256": hashlib.sha256(expected.encode()).hexdigest()}
        original_count += len(source["boxes"])
        merged_count += len(boxes)
    (args.root / "tools/assets/playing-card-art.json").write_text(
        json.dumps(models, separators=(",", ":")) + "\n", encoding="utf-8")
    (args.root / "src/test/resources/blackjack-card-fingerprints.json").write_text(
        json.dumps(fingerprints, indent=2) + "\n", encoding="utf-8")
    print(f"Imported 53 exact card faces: {original_count} -> {merged_count} cuboids; all coverage checks passed")


if __name__ == "__main__":
    main()
