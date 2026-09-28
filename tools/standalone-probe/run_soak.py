"""Run multi-game model/replay soak checks on a fresh loopback-only Purpur fixture."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

project = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--seconds", type=int, default=3600)
parser.add_argument("--server-dir", type=Path, default=project.parent / "table-games/tabletop-runtime")
parser.add_argument("--maven-repo", type=Path, default=project.parent / ".tools/m2")
args = parser.parse_args()
if not 60 <= args.seconds <= 7200:
    parser.error("seconds must be between 60 and 7200")
version = ET.parse(project / "pom.xml").getroot().find("{*}version").text
runtime = project / "target" / f"soak-{time.strftime('%Y%m%d-%H%M%S')}"
runtime.mkdir(parents=True)
shutil.copy2(args.server_dir / "purpur-2622.jar", runtime / "purpur-2622.jar")
for name in ("libraries", "versions", "cache"):
    if (args.server_dir / name).is_dir():
        shutil.copytree(args.server_dir / name, runtime / name)
(runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
(runtime / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=25618\nlevel-name=tabletop_soak\n"
    'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
    "online-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nenable-rcon=false\nenable-query=false\n", encoding="utf-8")
plugins = runtime / "plugins"
plugins.mkdir()
jar = project / "target" / f"3dtabletop-{version}.jar"
shutil.copy2(jar, plugins / jar.name)
digest = hashlib.sha256((plugins / jar.name).read_bytes()).hexdigest()
kyori = args.maven_repo / "net/kyori"
classpath = os.pathsep.join(map(str, (jar,
    args.maven_repo / "io/papermc/paper/paper-api/26.2.build.111-stable/paper-api-26.2.build.111-stable.jar",
    kyori / "adventure-key/5.2.0/adventure-key-5.2.0.jar",
    kyori / "adventure-api/5.2.0/adventure-api-5.2.0.jar")))
classes = runtime / "probe-classes"
classes.mkdir()
subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes),
                str(project / "tools/standalone-probe/BoardsSoakProbe.java")], check=True)
with zipfile.ZipFile(plugins / "BoardsSoakProbe.jar", "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("plugin.yml", "name: BoardsSoakProbe\nversion: 1\nmain: dev.tabletop3d.probe.BoardsSoakProbe\napi-version: '26.2'\ndepend: [3dtabletop]\n")
    for path in classes.rglob("*.class"):
        z.write(path, path.relative_to(classes).as_posix())
print(f"Soak runtime: {runtime}", flush=True)
with (runtime / "stdout.log").open("w", encoding="utf-8") as output:
    server = subprocess.Popen([shutil.which("java"), "-Xms512M", "-Xmx2G", "-XX:TieredStopAtLevel=1",
        "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        f"-Dtabletop.soak.seconds={args.seconds}", "-jar", "purpur-2622.jar", "nogui"], cwd=runtime,
        stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    found = False
    try:
        deadline = time.monotonic() + args.seconds + 300
        while time.monotonic() < deadline and server.poll() is None:
            logfile = runtime / "logs/latest.log"
            log = logfile.read_text(encoding="utf-8", errors="replace") if logfile.exists() else ""
            if "TABLETOP_SOAK_PASS" in log:
                found = True
                break
            if "TABLETOP_SOAK_FAIL" in log or "Error occurred while enabling" in log:
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
result_file = plugins / "BoardsSoakProbe/soak.json"
result = json.loads(result_file.read_text(encoding="utf-8")) if result_file.exists() else {}
result.update({"pass": bool(found and result.get("pass") and server.returncode == 0), "exit_code": server.returncode,
               "version": version, "jar_sha256": digest, "runtime": str(runtime), "client_visual_test": False})
(runtime / "receipt.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, indent=2), flush=True)
raise SystemExit(0 if result["pass"] else 1)
