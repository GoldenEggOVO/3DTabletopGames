import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class PackageResourcePackTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location(
            "package_resource_pack", ROOT / "tools/packaging/package_resource_pack.py")
        self.builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.builder)
        directory = ROOT / "target/packaging-tests"
        directory.mkdir(parents=True, exist_ok=True)
        self.workspace = tempfile.TemporaryDirectory(dir=directory)
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.source = self.root / "resource-pack"
        self.write("pack.mcmeta", {"pack": {"pack_format": 88, "description": "Test"}})
        self.write("assets/tabletop3d/items/tile.json", {
            "model": {"type": "minecraft:model", "model": "tabletop3d:item/tile"}})
        self.write("assets/tabletop3d/models/item/tile.json", {"textures": {}, "elements": []})
        self.write("assets/tabletop3d/sounds.json", {})
        self.write("licenses/notice.txt", "Test attribution")
        registration = self.source / "craftengine/resources/tabletop3d/configuration/items.yml"
        registration.parent.mkdir(parents=True)
        registration.write_text("items:\n  tabletop3d:tile:\n    material: paper\n    item_model: tabletop3d:tile\n")
        (self.root / "src/main/resources").mkdir(parents=True)

    def write(self, name, data):
        path = self.source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data if isinstance(data, str) else json.dumps(data), encoding="utf-8")
        return path

    def test_static_files_are_copied_without_rendering_or_rewriting(self):
        self.write("originals/private.txt", "Authoring material")
        self.write("sources.json", {"originals": []})
        before = {p.relative_to(self.source).as_posix(): p.read_bytes()
                  for p in self.source.rglob("*") if p.is_file()}
        manifest = self.builder.package_resource_pack(self.root)
        with zipfile.ZipFile(self.root / "target/tabletop-resource-pack.zip") as archive:
            expected = {name: data for name, data in before.items()
                        if name == "pack.mcmeta" or name.startswith(("assets/", "licenses/"))}
            self.assertEqual(expected, {name: archive.read(name) for name in archive.namelist()})
        self.assertEqual(before, {p.relative_to(self.source).as_posix(): p.read_bytes()
                                 for p in self.source.rglob("*") if p.is_file()})
        self.assertEqual(["tile"], manifest["models"])
        self.assertEqual(manifest["sha1"],
                         (self.root / "src/main/resources/resource-pack.sha1").read_text().strip())

    def test_repacking_identical_files_has_an_identical_checksum(self):
        first = self.builder.package_resource_pack(self.root)
        self.write("pack.mcmeta", {"pack": {"pack_format": 88, "description": "Test"}})
        second = self.builder.package_resource_pack(self.root)
        self.assertEqual(first["sha256"], second["sha256"])
        self.write("licenses/notice.txt", "Updated attribution")
        self.assertNotEqual(first["sha256"], self.builder.package_resource_pack(self.root)["sha256"])

    def test_missing_item_model_prevents_publishing_a_broken_pack(self):
        (self.source / "assets/tabletop3d/models/item/tile.json").unlink()
        with self.assertRaisesRegex(ValueError, "model"):
            self.builder.package_resource_pack(self.root)
        self.assertFalse((self.root / "target/tabletop-resource-pack.zip").exists())

    def test_unregistered_item_prevents_publishing_a_broken_pack(self):
        self.write("assets/tabletop3d/items/unknown.json", {
            "model": {"type": "minecraft:model", "model": "tabletop3d:item/tile"}})
        with self.assertRaisesRegex(ValueError, "registration"):
            self.builder.package_resource_pack(self.root)


if __name__ == "__main__":
    unittest.main()
