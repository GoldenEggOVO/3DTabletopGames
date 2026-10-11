import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name == "nt", "Windows build launcher")
class OneClickBuildTest(unittest.TestCase):
    def setUp(self):
        directory = ROOT / "target/build-tests"
        directory.mkdir(parents=True, exist_ok=True)
        workspace = tempfile.TemporaryDirectory(dir=directory)
        self.addCleanup(workspace.cleanup)
        self.workspace = Path(workspace.name)
        self.project = self.workspace / "project with spaces"
        self.project.mkdir()
        for name in ("build.cmd", "tools/packaging/build.py", "tools/packaging/package_resource_pack.py"):
            if (ROOT / name).is_file():
                target = self.project / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / name, target)
        self.write("pom.xml", '<project><version>1.2.3</version></project>')
        self.write("resource-pack/pack.mcmeta", {"pack": {"pack_format": 88, "description": "Fixture"}})
        self.write("resource-pack/assets/tabletop3d/items/shared/tile.json", {
            "model": {"type": "minecraft:model", "model": "tabletop3d:item/shared/tile"}})
        self.write("resource-pack/assets/tabletop3d/models/item/shared/tile.json", {"textures": {}, "elements": []})
        self.write("resource-pack/craftengine/resources/tabletop3d/configuration/items.yml",
                   "items:\n  tabletop3d:tile:\n    material: paper\n    item_model: tabletop3d:shared/tile\n")
        (self.project / "src/main/resources").mkdir(parents=True)
        jdk = self.workspace / "jdk with spaces"
        (jdk / "bin").mkdir(parents=True)
        (jdk / "bin/javac.exe").touch()
        (jdk / "release").write_text('JAVA_VERSION="25.0.2"\n')
        self.env = dict(os.environ, JAVA_HOME=str(jdk))
        maven = self.workspace / ".tools/apache-maven-3.9.11/bin"
        maven.mkdir(parents=True)
        (self.workspace / ".tools/m2").mkdir()
        (maven / "mvn.cmd").write_bytes((
            f'@echo off\r\n"{sys.executable}" -X utf8 "%~dp0maven_stub.py" %*\r\n'
            'exit /b %errorlevel%\r\n').encode())
        (maven / "maven_stub.py").write_text(
            'import json, os, sys, zipfile\n'
            'from pathlib import Path\n'
            'Path("maven-called.json").write_text(json.dumps(sys.argv[1:]))\n'
            'mode = os.environ.get("TABLETOP_TEST_MAVEN", "success")\n'
            'if mode == "fail": raise SystemExit(7)\n'
            'checksum = Path("src/main/resources/resource-pack.sha1").read_text()\n'
            'if mode == "stale": checksum = "0" * 40\n'
            'with zipfile.ZipFile("target/3dtabletop-1.2.3.jar", "w") as jar:\n'
            '    jar.writestr("resource-pack.sha1", checksum)\n', encoding="utf-8")

    def write(self, name, value):
        path = self.project / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value if isinstance(value, str) else json.dumps(value), encoding="utf-8")

    def build(self, launcher=False):
        command = ([os.environ["COMSPEC"], "/d", "/c", str(self.project / "build.cmd")]
                   if launcher else [sys.executable, "-X", "utf8", str(self.project / "tools/packaging/build.py")])
        return subprocess.run(command, input="\n", cwd=self.workspace, env=self.env,
                              capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30)

    def test_success_builds_matching_files_from_a_different_working_directory(self):
        result = self.build()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        manifest = json.loads((self.project / "target/resource-pack-manifest.json").read_text())
        with zipfile.ZipFile(self.project / "target/3dtabletop-1.2.3.jar") as jar:
            self.assertEqual(manifest["sha1"], jar.read("resource-pack.sha1").decode().strip())
        arguments = json.loads((self.project / "maven-called.json").read_text())
        self.assertIn("-o", arguments)
        self.assertIn("package", arguments)
        self.assertIn("Build complete", result.stdout)
        self.assertIn("3dtabletop-1.2.3.jar", result.stdout)

    def test_invalid_resource_pack_stops_before_maven(self):
        self.write("resource-pack/assets/tabletop3d/textures/broken.png", "Not a PNG")
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid PNG", result.stderr)
        self.assertFalse((self.project / "maven-called.json").exists())
        self.assertNotIn("Build complete", result.stdout)

    def test_maven_failure_is_not_reported_as_a_success(self):
        self.env["TABLETOP_TEST_MAVEN"] = "fail"
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((self.project / "maven-called.json").is_file())
        self.assertNotIn("Build complete", result.stdout)

    def test_stale_plugin_checksum_is_rejected(self):
        self.env["TABLETOP_TEST_MAVEN"] = "stale"
        result = self.build()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum", result.stderr)
        self.assertNotIn("Build complete", result.stdout)

    def test_double_click_launcher_preserves_failure_exit_code(self):
        self.env["TABLETOP_TEST_MAVEN"] = "fail"
        result = self.build(launcher=True)
        self.assertNotEqual(0, result.returncode)
        self.assertTrue((self.project / "maven-called.json").is_file())
        self.assertNotIn("Build complete", result.stdout)


if __name__ == "__main__":
    unittest.main()
