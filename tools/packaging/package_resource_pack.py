"""Package the editable Minecraft assets and update the plugin checksum."""
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def asset_path(source, identifier, folder, suffix):
    namespace, name = identifier.split(":", 1)
    return source / "assets" / namespace / folder / (name + suffix)


def validate_assets(source):
    json.loads((source / "pack.mcmeta").read_text(encoding="utf-8"))
    items = source / "assets/tabletop3d/items"
    paths = sorted(items.rglob("*.json"))
    definitions = {path.stem: path for path in paths}
    models = sorted(definitions)
    if not models:
        raise ValueError("Resource pack has no item models")
    if len(definitions) != len(paths):
        raise ValueError("Resource-pack item IDs must be unique across directories")
    registration = source / "craftengine/resources/tabletop3d/configuration/items.yml"
    registered = dict(re.findall(
        r"^  tabletop3d:([a-z0-9_-]+):\n    material: paper\n    item_model: (tabletop3d:[a-z0-9_/-]+)$",
        registration.read_text(encoding="utf-8"), re.MULTILINE))
    if set(registered) != set(models):
        raise ValueError("CraftEngine registration differs from the resource-pack items")
    for name in models:
        path = definitions[name]
        identifier = "tabletop3d:" + path.relative_to(items).with_suffix("").as_posix()
        if registered[name] != identifier:
            raise ValueError(f"CraftEngine item-model registration differs from the item path: {name}")
        item = json.loads(path.read_text(encoding="utf-8"))
        model = asset_path(source, item["model"]["model"], "models", ".json")
        if not model.is_file():
            raise ValueError(f"Missing item model: {name}")
    for path in (source / "assets").rglob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        if "/models/" in path.as_posix():
            for texture in data.get("textures", {}).values():
                if texture.startswith(("#", "minecraft:")):
                    continue
                if not asset_path(source, texture, "textures", ".png").is_file():
                    raise ValueError(f"Missing texture {texture}: {path.name}")
        if path.name == "sounds.json":
            for event in data.values():
                for sound in event.get("sounds", []):
                    name = sound if isinstance(sound, str) else sound["name"]
                    if not name.startswith("minecraft:") and not asset_path(source, name, "sounds", ".ogg").is_file():
                        raise ValueError(f"Missing sound: {name}")
    return models


def package_resource_pack(project=ROOT):
    source = project / "resource-pack"
    models = validate_assets(source)
    files = [source / "pack.mcmeta"]
    for folder in ("assets", "licenses"):
        files.extend(path for path in (source / folder).rglob("*") if path.is_file())
    textures = list((source / "assets").rglob("*.png"))
    pixels = 0
    for texture in textures:
        with texture.open("rb") as image:
            header = image.read(24)
        if header[:8] != b"\x89PNG\r\n\x1a\n" or len(header) != 24:
            raise ValueError(f"Invalid PNG texture: {texture.name}")
        width, height = struct.unpack(">II", header[16:24])
        pixels += width * height
    target = project / "target/tabletop-resource-pack.zip"
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".zip.tmp")
    with zipfile.ZipFile(temporary, "w") as archive:
        for path in sorted(files):
            entry = zipfile.ZipInfo(path.relative_to(source).as_posix(), (1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    temporary.replace(target)
    contents = target.read_bytes()
    manifest = {"models": models, "sha1": hashlib.sha1(contents).hexdigest(),
                "sha256": hashlib.sha256(contents).hexdigest(), "bytes": len(contents),
                "texture_count": len(textures), "texture_pixels": pixels}
    (project / "target/resource-pack-manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (project / "src/main/resources/resource-pack.sha1").write_text(manifest["sha1"] + "\n", encoding="ascii")
    return manifest


if __name__ == "__main__":
    result = package_resource_pack()
    print(json.dumps({key: value for key, value in result.items() if key != "models"}, indent=2))
