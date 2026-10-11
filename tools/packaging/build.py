"""Build the resource pack and its matching plugin for the Windows launcher."""
import hashlib
import os
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def build():
    if sys.version_info < (3, 12):
        raise RuntimeError("Python 3.12 or newer is required")
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    javac = shutil.which("javac")
    if javac:
        candidates.append(Path(javac).resolve().parent.parent)
    jdk = next((path for path in candidates
                if (path / "bin/javac.exe").is_file() and (path / "release").is_file()
                and re.search(r'^JAVA_VERSION="25(?:\.|\")', (path / "release").read_text(), re.MULTILINE)), None)
    if jdk is None:
        raise RuntimeError("JDK 25 is required; set JAVA_HOME or add its bin directory to PATH")
    environment = dict(os.environ, JAVA_HOME=str(jdk))
    bundled = ROOT.parent / ".tools/apache-maven-3.9.11/bin/mvn.cmd"
    maven = str(bundled) if bundled.is_file() else shutil.which("mvn")
    if not maven:
        raise RuntimeError("Maven 3.9 or newer is required; add its bin directory to PATH")
    arguments = [maven, "-B", "-ntp"]
    cache = ROOT.parent / ".tools/m2"
    if bundled.is_file() and cache.is_dir():
        arguments.extend(["-o", "-Dmaven.repo.local=" + str(cache)])
    arguments.append("package")

    print("[1/2] Packaging resource pack...", flush=True)
    subprocess.run([sys.executable, "-X", "utf8", str(ROOT / "tools/packaging/package_resource_pack.py")],
                   cwd=ROOT, check=True)
    print("[2/2] Compiling and testing plugin...", flush=True)
    subprocess.run(arguments, cwd=ROOT, env=environment, check=True)

    version = ET.parse(ROOT / "pom.xml").getroot().find("{*}version").text
    plugin = ROOT / "target" / f"3dtabletop-{version}.jar"
    pack = ROOT / "target/tabletop-resource-pack.zip"
    checksum = hashlib.sha1(pack.read_bytes()).hexdigest()
    with zipfile.ZipFile(plugin) as archive:
        if archive.read("resource-pack.sha1").decode("ascii").strip() != checksum:
            raise RuntimeError("Plugin/resource-pack checksum mismatch")
        if hashlib.sha1(archive.read("tabletop-resource-pack.zip")).hexdigest() != checksum:
            raise RuntimeError("Bundled resource pack differs from the packaged ZIP")
    print(f"\nBuild complete. Checksums match.\nPlugin: {plugin}\nResource pack: {pack}", flush=True)


if __name__ == "__main__":
    try:
        build()
    except (RuntimeError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile, KeyError) as error:
        print(f"\nBuild failed: {error}", file=sys.stderr)
        raise SystemExit(1)
