"""Package the verified 3dtabletop JAR and documentation for a release."""

import hashlib
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

project = Path(__file__).resolve().parents[1]
jar = project / "target/3dtabletop-1.3.0.jar"
receipt = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
if not receipt.get("pass") or len(receipt.get("boots", [])) != 3:
    raise SystemExit("Clean Purpur create, restart and migration receipt is required")
if not all(boot.get("pass") for boot in receipt["boots"]):
    raise SystemExit("Runtime probe failed")

totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
for report in (project / "target/surefire-reports").glob("TEST-*.xml"):
    suite = ET.parse(report).getroot()
    if not suite.get("name", "").startswith("dev.tabletop3d."):
        continue
    for key in totals:
        totals[key] += int(suite.get(key, 0))
if totals != {"tests": 126, "failures": 0, "errors": 0, "skipped": 0}:
    raise SystemExit(f"JUnit results not ready: {totals}")

with zipfile.ZipFile(jar) as artifact:
    plugin = artifact.read("plugin.yml").decode("utf-8")
    if "name: 3dtabletop" not in plugin or "version: 1.3.0" not in plugin:
        raise SystemExit("Incorrect plugin identity")
    if "ServerGames" in plugin or "serverboards" in plugin.lower():
        raise SystemExit("Legacy hard dependency or command remains")
    names = set(artifact.namelist())
    if any(name.startswith("dev/server/games/") or name.startswith("dev/server/boards/") for name in names):
        raise SystemExit("Legacy package was bundled")
    for required in ("dev/tabletop3d/BoardWindow.class", "dev/tabletop3d/RoomReplayVerifier.class",
                     "dev/tabletop3d/Language.class", "lang/en.yml", "menus/catalog.yml"):
        if required not in names:
            raise SystemExit(f"Missing {required}")

digest = hashlib.sha256(jar.read_bytes()).hexdigest()
deliverables = project / "deliverables"
deliverables.mkdir(exist_ok=True)
verification = {"version": "1.3.0", "jar_sha256": digest, "junit": totals,
                "runtime": {"purpur": "26.2-2622", "clean_create_restart_and_migration": True,
                            "optional_plugins_absent": ["ServerGames", "ServerMenu", "ServerCasino", "KaMenu"],
                            "receipt": receipt["runtime"]},
                "client_visual_test": False, "production_deployed": False}
(deliverables / "verification.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
(deliverables / "SHA256SUMS.txt").write_text(f"{digest}  {jar.name}\n", encoding="ascii")
package = deliverables / "3dtabletop-1.3.0.zip"
with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(jar, jar.name)
    for name in ("README.md", "MIGRATION.md", "FEATURES.md", "LICENSE"):
        archive.write(project / name, name)
    archive.write(project / "tools/server_boards_migrate.py", "tools/server_boards_migrate.py")
    archive.write(deliverables / "verification.json", "verification.json")
    archive.write(deliverables / "SHA256SUMS.txt", "SHA256SUMS.txt")
print(json.dumps({"package": str(package), "zip_sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
                  "jar_sha256": digest, "junit": totals, "runtime_pass": True}, indent=2))
