"""Package a locally verified acceptance build; never publish a GitHub Release."""
import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from package_source import package_source

project = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("receipt", type=Path, help="Passing three-boot runtime receipt")
parser.add_argument("--soak", type=Path, help="Optional passing continuous-game receipt for the same JAR")
parser.add_argument("--snapshot", type=Path, help="Optional passing multi-game recovery receipt for the same JAR")
args = parser.parse_args()
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
jar = project / "target" / f"3dtabletop-{version}.jar"
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
receipt = json.loads(args.receipt.read_text(encoding="utf-8"))
if not receipt.get("pass") or len(receipt.get("boots", [])) != 3 or not all(b.get("pass") for b in receipt["boots"]):
    raise SystemExit("Passing clean create/restart/migration receipt required")
if receipt.get("jar_sha256") != digest or receipt.get("version") != version:
    raise SystemExit("Runtime receipt must match this exact JAR")
soak = None
if args.soak:
    soak = json.loads(args.soak.read_text(encoding="utf-8"))
    if not soak.get("pass") or soak.get("exit_code") != 0 or soak.get("final_owned_entities") != 0:
        raise SystemExit("Passing soak receipt with clean shutdown and entity cleanup required")
    if soak.get("jar_sha256") != digest or soak.get("version") != version:
        raise SystemExit("Soak receipt must match this exact JAR")
    if soak.get("elapsed_seconds", 0) < soak.get("requested_seconds", 60):
        raise SystemExit("Soak did not complete its requested duration")
snapshot = None
if args.snapshot:
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8"))
    if (not snapshot.get("pass") or not 1 <= snapshot.get("snapshot_rooms", 0) <= 32
            or len(snapshot.get("boots", [])) != 3 or not all(b.get("pass") for b in snapshot["boots"])):
        raise SystemExit("Passing multi-game snapshot recovery receipt required")
    if snapshot.get("jar_sha256") != digest or snapshot.get("version") != version:
        raise SystemExit("Snapshot receipt must match this exact JAR")

totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
for report in (project / "target/surefire-reports").glob("TEST-*.xml"):
    suite = ET.parse(report).getroot()
    if suite.get("name", "").startswith("dev.tabletop3d."):
        for key in totals:
            totals[key] += int(suite.get(key, 0))
if totals["tests"] < 130 or any(totals[key] for key in ("failures", "errors", "skipped")):
    raise SystemExit(f"JUnit results not ready: {totals}")

with zipfile.ZipFile(jar) as artifact:
    plugin = artifact.read("plugin.yml").decode("utf-8")
    if "name: 3dtabletop" not in plugin or f"version: {version}" not in plugin:
        raise SystemExit("Incorrect plugin identity")
    if "ServerGames" in plugin or "serverboards" in plugin.lower():
        raise SystemExit("Legacy dependency or command remains")
    names = set(artifact.namelist())
    if any(n.startswith(("dev/server/games/", "dev/server/boards/")) for n in names):
        raise SystemExit("Legacy package was bundled")
    for required in ("dev/tabletop3d/BoardWindow.class", "dev/tabletop3d/ui/MessageText.class",
                     "dev/tabletop3d/ui/LabelLayout.class", "dev/tabletop3d/RoomText.class",
                     "dev/tabletop3d/rules/MahjongGame.class", "dev/tabletop3d/rules/LastCardGame.class",
                     "dev/tabletop3d/HandTable.class", "dev/tabletop3d/DiceTray.class",
                     "lang/en.yml", "lang/legacy.yml", "menus/catalog.yml", "menus/setup.yml", "menus/hand.yml"):
        if required not in names:
            raise SystemExit(f"Missing {required}")

source = package_source()
output = project / "deliverables" / version
output.mkdir(parents=True, exist_ok=True)
shutil.copy2(jar, output / jar.name)
shutil.copy2(source, output / source.name)
verification = {"version": version, "jar_sha256": digest, "junit": totals,
                "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=project, text=True).strip(),
                "runtime": receipt, "client_visual_test": False, "production_deployed": False,
                "release_published": False}
if soak is not None:
    verification["soak"] = soak
if snapshot is not None:
    verification["snapshot_recovery"] = snapshot
(output / "verification.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
checksums = f"{digest}  {jar.name}\n{hashlib.sha256(source.read_bytes()).hexdigest()}  {source.name}\n"
(output / "SHA256SUMS.txt").write_text(checksums, encoding="ascii")
package = output / f"3dtabletop-{version}.zip"
with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in (output / jar.name, output / source.name, output / "verification.json", output / "SHA256SUMS.txt"):
        archive.write(path, path.name)
    for name in ("README.md", "README.zh-CN.md", "CHANGELOG.md", "THIRD_PARTY.md", "LICENSE"):
        archive.write(project / name, name)
    for path in sorted((project / "docs").rglob("*.md")):
        archive.write(path, path.relative_to(project).as_posix())
    archive.write(project / "tools/server_boards_migrate.py", "tools/server_boards_migrate.py")
    for name in ("ludo-side-tray-frames.png", "lastcard-owner-view.png", "mahjong-owner-view.png"):
        preview = output / "previews" / name
        if preview.is_file():
            archive.write(preview, "previews/" + name)
print(json.dumps({"package": str(package), "zip_sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
                  "jar_sha256": digest, "junit": totals, "runtime_pass": True}, indent=2))
