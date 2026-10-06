"""Extend the verified local package with the independent pack and CE registrations."""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("native_receipt", type=Path)
parser.add_argument("craftengine_receipt", type=Path)
args = parser.parse_args()
native = json.loads(args.native_receipt.read_text(encoding="utf-8"))
ce = json.loads(args.craftengine_receipt.read_text(encoding="utf-8"))
if not ce.get("pass") or not ce.get("craftengine") or ce.get("jar_sha256") != native.get("jar_sha256"):
    raise SystemExit("Passing CraftEngine receipt for the same JAR required")
subprocess.run([sys.executable, str(ROOT / "tools/package_standalone.py"), str(args.native_receipt)], check=True)
out = ROOT / "deliverables" / native["version"]
pack = ROOT / "target/tabletop-resource-pack.zip"
manifest = json.loads((ROOT / "target/resource-pack-manifest.json").read_text(encoding="utf-8"))
if len(manifest["models"]) != len(set(manifest["models"])) or hashlib.sha256(pack.read_bytes()).hexdigest() != manifest["sha256"]:
    raise SystemExit("Resource-pack manifest does not match ZIP")
with zipfile.ZipFile(out / f"3dtabletop-{native['version']}.jar") as jar:
    if jar.read("resource-pack.sha1").decode("ascii").strip() != manifest["sha1"]:
        raise SystemExit("Plugin checksum does not match the shipped resource pack; rebuild plugin after pack generation")
shutil.copy2(pack, out / pack.name)
shutil.copy2(ROOT / "target/resource-pack-manifest.json", out / "resource-pack-manifest.json")
with zipfile.ZipFile(out / "craftengine-registration.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted((ROOT / "resource-pack/craftengine/resources/tabletop3d").rglob("*")):
        if p.is_file():
            z.write(p, "plugins/CraftEngine/" + p.relative_to(ROOT / "resource-pack/craftengine").as_posix())
for mode in ("vanilla", "resource-pack", "mixed"):
    (out / f"rendering-{mode}.yml").write_text(
        "# Merge this rendering section into plugins/3dtabletop/config.yml; restart.\n"
        f"rendering:\n  mode: {mode}\n  resource-pack:\n"
        "    url: 'https://YOUR-HOST/tabletop-resource-pack.zip'\n"
        "    # Upload the ZIP shipped with this plugin; checksum and identity are automatic.\n", encoding="utf-8")
shutil.copy2(ROOT / "docs/craftengine.zh-CN.md", out / "INSTALL.zh-CN.md")
shutil.copy2(ROOT / "docs/native-table-counts.zh-CN.md", out / "native-table-counts.zh-CN.md")
shutil.copy2(ROOT / "docs/acceptance.zh-CN.md", out / "acceptance.zh-CN.md")
shutil.copy2(ROOT / "resource-pack/sources.json", out / "asset-sources.json")
for folder, source in (("pack", "pack-preview"), ("native-before", "native-before"), ("native-after", "native-after")):
    target = out / "previews" / folder
    target.mkdir(parents=True, exist_ok=True)
    for p in sorted((ROOT / "target" / source).glob("*")):
        if p.suffix in (".png", ".csv"):
            shutil.copy2(p, target / p.name)
evidence = out / "evidence"
evidence.mkdir(exist_ok=True)
shutil.copy2(args.native_receipt, evidence / "native-runtime.json")
shutil.copy2(args.craftengine_receipt, evidence / "craftengine-runtime.json")
for name in ("craftengine-package.log", "resource-green.log"):
    shutil.copy2(ROOT / "target" / name, evidence / name)
shutil.copy2(ROOT / "target/full-table-counts.csv", evidence / "full-table-counts.csv")
verification_path = out / "verification.json"
verification = json.loads(verification_path.read_text(encoding="utf-8"))
verification.update(craftengine=ce, resource_pack=manifest, client_audio_test=False, client_fps_test=False)
verification_path.write_text(json.dumps(verification, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
checksums = []
package = out / f"3dtabletop-{native['version']}.zip"
for p in sorted(out.iterdir()):
    if p.is_file() and p.name not in (package.name, "SHA256SUMS.txt"):
        checksums.append(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n")
(out / "SHA256SUMS.txt").write_text("".join(checksums), encoding="ascii")
# Rewrite instead of append so rerunning does not create duplicate ZIP entries.
with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted(out.rglob("*")):
        if p.is_file() and p != package:
            z.write(p, p.relative_to(out).as_posix())
    for name in ("README.md", "README.zh-CN.md", "LICENSE"):
        z.write(ROOT / name, name)
    for p in sorted((ROOT / "docs").rglob("*.md")):
        z.write(p, p.relative_to(ROOT).as_posix())
    for p in sorted((ROOT / "src/main/resources").rglob("*.yml")):
        if p.parent.name == "languages":
            z.write(p, p.relative_to(ROOT).as_posix())
print(package)
