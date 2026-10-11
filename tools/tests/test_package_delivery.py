import hashlib
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


class PackageDeliveryTest(unittest.TestCase):
    def setUp(self):
        directory = ROOT / "target/delivery-tests"
        directory.mkdir(parents=True, exist_ok=True)
        workspace = tempfile.TemporaryDirectory(dir=directory)
        self.addCleanup(workspace.cleanup)
        self.project = Path(workspace.name)
        shutil.copytree(ROOT / "tools/packaging", self.project / "tools/packaging",
                        ignore=shutil.ignore_patterns("__pycache__"))
        self.write("pom.xml", '<project><version>test</version></project>')
        for name in ("README.md", "README.zh-CN.md", "LICENSE"):
            self.write(name, "Fixture")
        self.write("target/surefire-reports/TEST-fixture.xml",
                   '<testsuite name="dev.tabletop3d.Fixture" tests="130" failures="0" errors="0" skipped="0"/>')
        self.write("src/test/java/dev/tabletop3d/Fixture.java", "class Fixture {}")
        with zipfile.ZipFile(self.project / "target/3dtabletop-test.jar", "w") as jar:
            jar.writestr("plugin.yml", "name: 3dtabletop\nversion: test\n")
            for name in (
                    "menu/BoardWindow", "ui/MessageText", "ui/LabelLayout", "text/RoomText",
                    "rules/mahjong/MahjongGame", "rules/coloreight/ColorEightGame", "render/HandTable",
                    "render/dice/DiceTray"):
                jar.writestr(f"dev/tabletop3d/{name}.class", b"")
            for name in ("en_US", "zh_CN"):
                jar.writestr(f"languages/{name}.yml", b"")
            jar.writestr("resource-pack.sha1", hashlib.sha1(b"pack").hexdigest())
        digest = hashlib.sha256((self.project / "target/3dtabletop-test.jar").read_bytes()).hexdigest()
        self.receipt = {"pass": True, "version": "test", "jar_sha256": digest,
                        "boots": [{"pass": True}] * 3}
        self.write("native.json", self.receipt)
        self.write("ce.json", dict(self.receipt, craftengine=True))
        self.write("soak.json", dict(self.receipt, exit_code=0, final_owned_entities=0,
                                      elapsed_seconds=60, requested_seconds=60))
        self.write("target/tabletop-resource-pack.zip", "pack")
        self.write("target/resource-pack-manifest.json", {
            "models": ["tile"], "sha256": hashlib.sha256(b"pack").hexdigest(),
            "sha1": hashlib.sha1(b"pack").hexdigest()})
        self.write("resource-pack/sources.json", {})
        self.write("resource-pack/craftengine/resources/tabletop3d/pack.yml", "name: Test")
        self.write("checks.log", "Fixture evidence")
        self.env = dict(os.environ, GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="safe.directory",
                        GIT_CONFIG_VALUE_0=str(self.project))
        subprocess.run(["git", "init", "--quiet"], cwd=self.project, env=self.env, check=True)
        subprocess.run(["git", "add", "README.md"], cwd=self.project, env=self.env, check=True)
        subprocess.run(["git", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                        "commit", "--quiet", "-m", "fixture"], cwd=self.project, env=self.env, check=True)

    def write(self, name, value):
        path = self.project / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value if isinstance(value, str) else json.dumps(value), encoding="utf-8")

    def package(self, *arguments):
        return subprocess.run([sys.executable, "tools/packaging/package_craftengine.py",
                               "native.json", "ce.json", *arguments], cwd=self.project,
                              env=self.env, capture_output=True, text=True)

    def test_clean_build_needs_no_legacy_logs_or_previews(self):
        result = self.package()
        self.assertEqual(0, result.returncode, result.stderr)
        output = self.project / "deliverables/test"
        self.assertFalse((output / "previews").exists())
        with zipfile.ZipFile(output / "3dtabletop-test.zip") as package:
            self.assertEqual(len(package.namelist()), len(set(package.namelist())))
            self.assertIn("evidence/craftengine-runtime.json", package.namelist())

    def test_selected_evidence_and_soak_are_preserved(self):
        result = self.package("--soak", "soak.json", "--evidence", "checks.log")
        self.assertEqual(0, result.returncode, result.stderr)
        output = self.project / "deliverables/test"
        verification = json.loads((output / "verification.json").read_text(encoding="utf-8"))
        self.assertEqual(60, verification["soak"]["elapsed_seconds"])
        self.assertEqual("Fixture evidence", (output / "evidence/checks.log").read_text())

    def test_retired_suite_does_not_change_current_results(self):
        self.write("target/surefire-reports/TEST-retired.xml",
                   '<testsuite name="dev.tabletop3d.Retired" tests="11" failures="1" errors="0" skipped="0"/>')
        result = self.package()
        self.assertEqual(0, result.returncode, result.stderr)
        output = self.project / "deliverables/test"
        verification = json.loads((output / "verification.json").read_text(encoding="utf-8"))
        self.assertEqual({"tests": 130, "failures": 0, "errors": 0, "skipped": 0}, verification["junit"])

    def test_retired_reports_cannot_satisfy_test_gate(self):
        (self.project / "src/test/java/dev/tabletop3d/Fixture.java").unlink()
        result = self.package()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("JUnit results not ready", result.stderr)


if __name__ == "__main__":
    unittest.main()
