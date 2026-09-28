"""Package a locally verified acceptance build; never publish a GitHub Release."""
import hashlib
import json
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from package_source import package_source

project = Path(__file__).resolve().parents[1]
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
jar = project / "target" / f"3dtabletop-{version}.jar"
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
receipt = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if not receipt.get("pass") or len(receipt.get("boots", [])) != 3 or not all(b.get("pass") for b in receipt["boots"]):
    raise SystemExit("Passing clean create/restart/migration receipt required")
if receipt.get("jar_sha256") != digest or receipt.get("version") != version:
    raise SystemExit("Runtime receipt must match this exact JAR")

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
                     "lang/en.yml", "lang/legacy.yml", "menus/catalog.yml"):
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
print(json.dumps({"package": str(package), "zip_sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
                  "jar_sha256": digest, "junit": totals, "runtime_pass": True}, indent=2))
