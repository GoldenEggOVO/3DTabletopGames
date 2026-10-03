"""Boot a clean localhost Purpur server three times to test create and restore."""

import json
import hashlib
import argparse
import os
import xml.etree.ElementTree as ET
import shutil
import subprocess
import time
import zipfile
from pathlib import Path

project = Path(__file__).resolve().parents[2]
workspace = project.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--server-dir", type=Path, default=workspace / "table-games" / "tabletop-runtime",
                    help="Prepared Purpur 26.2 cache with purpur-2622.jar, libraries and versions")
parser.add_argument("--maven-repo", type=Path, default=workspace / ".tools/m2")
parser.add_argument("--rooms-snapshot", type=Path, help="Synthetic all-bot snapshot to restore on boots 2 and 3; remaps its anchors to the fresh fixture world")
parser.add_argument("--craftengine-jar", type=Path, help="Optional local CE binary; never bundled")
parser.add_argument("--craftengine-cache", type=Path, help="Existing CE dependency cache (libs directory)")
args = parser.parse_args()
source = args.server_dir
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
runtime = project / "target" / f"standalone-smoke-{time.strftime('%Y%m%d-%H%M%S')}"
runtime.mkdir(parents=True)
shutil.copy2(source / "purpur-2622.jar", runtime / "purpur-2622.jar")
for name in ("libraries", "versions", "cache"):
    if (source / name).is_dir():
        shutil.copytree(source / name, runtime / name)
if "eula=true" not in (source / "eula.txt").read_text(encoding="utf-8"):
    raise RuntimeError("Source fixture must already have an accepted EULA")
shutil.copy2(source / "eula.txt", runtime / "eula.txt")
(runtime / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25617\nlevel-name=boards_smoke\n"
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
    "online-mode=false\nview-distance=2\nsimulation-distance=2\n"
    "spawn-protection=0\nenable-rcon=false\nenable-query=false\n", encoding="utf-8")
plugins = runtime / "plugins"
plugins.mkdir()
jar = project / "target" / f"3dtabletop-{version}.jar"
shutil.copy2(jar, plugins / jar.name)
if args.craftengine_jar:
    shutil.copy2(args.craftengine_jar, plugins / "CraftEngine.jar")
    ce = plugins / "CraftEngine"
    ce.mkdir()
    if args.craftengine_cache:
        shutil.copytree(args.craftengine_cache / "libs", ce / "libs")
    shutil.copytree(project / "craftengine/resources/tabletop3d", ce / "resources/tabletop3d")
    (ce / "config.yml").write_text("metrics: false\nupdate-checker: false\nresource-pack:\n  merge-external-folders: []\n  delivery:\n    send-on-join: false\n    auto-upload: false\n    resend-on-upload: false\n    hosting: []\n", encoding="utf-8")

tested_digest = hashlib.sha256((plugins / jar.name).read_bytes()).hexdigest()
paper_api = args.maven_repo / "io/papermc/paper/paper-api/26.2.build.111-stable/paper-api-26.2.build.111-stable.jar"
kyori = args.maven_repo / "net/kyori"
classpath = os.pathsep.join(map(str, (jar, paper_api,
    kyori / "adventure-key/5.2.0/adventure-key-5.2.0.jar",
    kyori / "adventure-api/5.2.0/adventure-api-5.2.0.jar",
    args.maven_repo / "org/jetbrains/annotations/26.1.0/annotations-26.1.0.jar",
    args.maven_repo / "com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar")))
classes = runtime / "probe-classes"
classes.mkdir()
subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(classes), str(project / "tools/standalone-probe/BoardsStandaloneProbe.java")], check=True)
with zipfile.ZipFile(plugins / "BoardsStandaloneProbe.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("plugin.yml", "name: BoardsStandaloneProbe\nversion: 1\nmain: dev.tabletop3d.probe.BoardsStandaloneProbe\napi-version: '26.2'\ndepend: [3dtabletop]\n")
    for path in classes.rglob("*.class"):
        archive.write(path, path.relative_to(classes).as_posix())

boots = []
snapshot_digest = None
boot_steps = tuple((number, "BOARDS_STANDALONE_CREATE_PASS" if number == 1 else "BOARDS_STANDALONE_RESTORE_PASS") for number in (1, 2, 3))
for number, marker in boot_steps:
    if args.rooms_snapshot and number == 2:
        snapshot_bytes = args.rooms_snapshot.read_bytes()
        snapshot_digest = hashlib.sha256(snapshot_bytes).hexdigest()
        snapshot = json.loads(snapshot_bytes)
        current_rooms = plugins / "3dtabletop/rooms.json"
        fixture_world = json.loads(current_rooms.read_text(encoding="utf-8"))["rooms"][0]["anchorWorld"]
        if not 1 <= len(snapshot["rooms"]) <= 32 or snapshot.get("returns"):
            raise ValueError("Expected 1-32 synthetic rooms and no player return locations")
        for room in snapshot["rooms"]:
            if not room["seats"] or not all(seat["bot"] for seat in room["seats"]):
                raise ValueError("Only synthetic all-bot snapshots are accepted")
            room["anchorWorld"] = fixture_world
        snapshot_text = json.dumps(snapshot, ensure_ascii=False, indent=2)
        current_rooms.write_text(snapshot_text, encoding="utf-8")
        (runtime / "multi-room-snapshot.json").write_text(snapshot_text, encoding="utf-8")
    snapshot_mode = bool(args.rooms_snapshot and number > 1)
    if snapshot_mode:
        marker = "BOARDS_SNAPSHOT_RESTORE_PASS"
    boot_stdout = runtime / f"boot-{number}.stdout.log"
    with boot_stdout.open("w", encoding="utf-8") as output:
        server = subprocess.Popen([shutil.which("java"), "-Xms512M", "-Xmx2G", "-XX:TieredStopAtLevel=1",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                   "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                                   f"-Dboards.probe.snapshot={str(snapshot_mode).lower()}",
                                   f"-Dboards.probe.craftengine={str(bool(args.craftengine_jar)).lower()}",
                                   "-jar", "purpur-2622.jar", "nogui"],
                                  cwd=runtime, stdin=subprocess.PIPE, stdout=output,
                                  stderr=subprocess.STDOUT, text=True,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        found = False
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline and server.poll() is None:
                # Each boot owns a fresh file; latest.log can still contain the prior boot's marker.
                log = boot_stdout.read_text(encoding="utf-8", errors="replace")
                if marker in log:
                    found = True
                    break
                if "BOARDS_STANDALONE_PROBE_FAIL" in log:
                    break
                time.sleep(1)
        finally:
            if server.poll() is None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(timeout=40)
                except (subprocess.TimeoutExpired, OSError):
                    server.kill()
                    server.wait()
        log = (runtime / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
        (runtime / f"boot-{number}.log").write_text(log, encoding="utf-8")
        boots.append({"boot": number, "pass": found and server.returncode == 0 and (not args.craftengine_jar or "BOARDS_CRAFTENGINE_PASS" in log),
                      "exit_code": server.returncode,
                      "markers": [line for line in log.splitlines()
                                  if "BOARDS_" in line or "Error occurred while enabling" in line]})
        if not boots[-1]["pass"]:
            break

receipt = {"version": version, "jar_sha256": tested_digest, "runtime": str(runtime), "pass": len(boots) == len(boot_steps) and all(boot["pass"] for boot in boots),
           "craftengine": bool(args.craftengine_jar), "boots": boots, "client_visual_test": False, "production_deployed": False}
if snapshot_digest:
    receipt["snapshot_source_sha256"] = snapshot_digest
    receipt["snapshot_rooms"] = len(snapshot["rooms"])
    receipt["snapshot_world_remapped_for_fixture"] = True
(runtime / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(receipt, ensure_ascii=False, indent=2))
raise SystemExit(0 if receipt["pass"] else 1)
