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
args = parser.parse_args()
source = args.server_dir
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
runtime = project / "target" / f"standalone-smoke-{time.strftime('%Y%m%d-%H%M%S')}"
runtime.mkdir(parents=True)
shutil.copy2(source / "purpur-2622.jar", runtime / "purpur-2622.jar")
for name in ("libraries", "versions", "cache"):
    if (source / name).is_dir():
        shutil.copytree(source / name, runtime / name)
(runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(runtime / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25617\nlevel-name=boards_smoke\n"
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
    "online-mode=false\nview-distance=2\nsimulation-distance=2\n"
    "spawn-protection=0\nenable-rcon=false\nenable-query=false\n", encoding="utf-8")
plugins = runtime / "plugins"
plugins.mkdir()
jar = project / "target" / f"3dtabletop-{version}.jar"
shutil.copy2(jar, plugins / jar.name)
tested_digest = hashlib.sha256((plugins / jar.name).read_bytes()).hexdigest()
paper_api = args.maven_repo / "io/papermc/paper/paper-api/26.2.build.111-stable/paper-api-26.2.build.111-stable.jar"
kyori = args.maven_repo / "net/kyori"
classpath = os.pathsep.join(map(str, (paper_api,
    kyori / "adventure-key/5.2.0/adventure-key-5.2.0.jar",
    kyori / "adventure-api/5.2.0/adventure-api-5.2.0.jar")))
classes = runtime / "probe-classes"
classes.mkdir()
subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(classes), str(project / "tools/standalone-probe/BoardsStandaloneProbe.java")], check=True)
with zipfile.ZipFile(plugins / "BoardsStandaloneProbe.jar", "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("plugin.yml", "name: BoardsStandaloneProbe\nversion: 1\nmain: dev.tabletop3d.probe.BoardsStandaloneProbe\napi-version: '26.2'\ndepend: [3dtabletop]\n")
    for path in classes.rglob("*.class"):
        archive.write(path, path.relative_to(classes).as_posix())

boots = []
for number, marker in ((1, "BOARDS_STANDALONE_CREATE_PASS"), (2, "BOARDS_STANDALONE_RESTORE_PASS"),
                       (3, "BOARDS_STANDALONE_MIGRATION_PASS")):
    if number == 3:
        current = plugins / "3dtabletop"
        legacy = plugins / "ServerBoards"
        if not current.resolve().is_relative_to(runtime.resolve()) or not legacy.resolve().is_relative_to(runtime.resolve()):
            raise RuntimeError("Unexpected migration fixture paths")
        shutil.move(current, legacy)
    with (runtime / f"boot-{number}.stdout.log").open("w", encoding="utf-8") as output:
        server = subprocess.Popen([shutil.which("java"), "-Xms512M", "-Xmx2G", "-XX:TieredStopAtLevel=1",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                   "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                                   "-jar", "purpur-2622.jar", "nogui"],
                                  cwd=runtime, stdin=subprocess.PIPE, stdout=output,
                                  stderr=subprocess.STDOUT, text=True,
                                  creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        found = False
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline and server.poll() is None:
                logfile = runtime / "logs/latest.log"
                log = logfile.read_text(encoding="utf-8", errors="replace") if logfile.exists() else ""
                if marker in log:
                    found = True
                    break
                if "BOARDS_STANDALONE_PROBE_FAIL" in log:
                    break
                time.sleep(1)
            if server.poll() is None:
                server.stdin.write("stop\n")
                server.stdin.flush()
                server.wait(timeout=40)
        except (subprocess.TimeoutExpired, OSError):
            server.kill()
            server.wait()
        log = (runtime / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
        (runtime / f"boot-{number}.log").write_text(log, encoding="utf-8")
        migrated = number != 3 or (legacy.is_dir() and
            (plugins / "3dtabletop/migration-from-serverboards.txt").is_file() and
            (plugins / "3dtabletop/rooms.json").is_file())
        boots.append({"boot": number, "pass": found and migrated and server.returncode == 0,
                      "exit_code": server.returncode,
                      "markers": [line for line in log.splitlines()
                                  if "BOARDS_" in line or "Error occurred while enabling" in line]})
        if not boots[-1]["pass"]:
            break

receipt = {"version": version, "jar_sha256": tested_digest, "runtime": str(runtime), "pass": len(boots) == 3 and all(boot["pass"] for boot in boots),
           "boots": boots, "client_visual_test": False, "production_deployed": False}
(runtime / "receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(receipt, ensure_ascii=False, indent=2))
raise SystemExit(0 if receipt["pass"] else 1)
