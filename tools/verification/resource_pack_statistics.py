"""Compare exterior geometry and unique referenced texture pixels in two packs."""
import argparse
import csv
import io
import json
from pathlib import Path
import zipfile
from PIL import Image


def statistics(path):
    models, textures = {}, set()
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.startswith("assets/tabletop3d/models/item/") or not name.endswith(".json"):
                continue
            model = json.loads(archive.read(name))
            models[Path(name).stem] = {"elements": len(model["elements"]),
                                     "quads": sum(len(e["faces"]) for e in model["elements"])}
            textures.update(model["textures"].values())
        pixels = 0
        for name in textures:
            image = Image.open(io.BytesIO(archive.read("assets/" + name.replace(":", "/textures/") + ".png")))
            pixels += image.width * image.height
    return dict(models=models, quads=sum(m["quads"] for m in models.values()),
                texture_count=len(textures), texture_pixels=pixels, file_bytes=Path(path).stat().st_size)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    before, after = statistics(args.before), statistics(args.after)
    assert before["models"].keys() == after["models"].keys(), "Model catalogue changed"
    result = dict(before=before, after=after,
                  quad_reduction_percent=100 * (1 - after["quads"] / before["quads"]),
                  client_fps_measured=False)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    with args.output.with_suffix(".csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(("model", "before_elements", "after_elements", "before_quads", "after_quads"))
        for name in sorted(before["models"]):
            a, b = before["models"][name], after["models"][name]
            writer.writerow((name, a["elements"], b["elements"], a["quads"], b["quads"]))
    print(json.dumps({key: value for key, value in result.items() if key not in ("before", "after")}))
    print(json.dumps({key: {k: v for k, v in result[key].items() if k != "models"} for key in ("before", "after")}))
