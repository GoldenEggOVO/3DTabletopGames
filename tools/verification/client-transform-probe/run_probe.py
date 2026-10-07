"""Validate pack facing with an already installed official Minecraft client parser."""
import argparse
import json
import os
import shutil
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('minecraft_home', type=Path)
parser.add_argument('--version', default='26.2')
args = parser.parse_args()
root = Path(__file__).resolve().parents[3]
client = args.minecraft_home / 'versions' / args.version
metadata = json.loads((client / f'{args.version}.json').read_text(encoding='utf-8'))
libraries = [client / f'{args.version}.jar']
for library in metadata['libraries']:
    artifact = library.get('downloads', {}).get('artifact')
    if artifact:
        path = args.minecraft_home / 'libraries' / artifact['path']
        # Client metadata also lists libraries for other operating systems.
        if path.is_file():
            libraries.append(path)
classpath = os.pathsep.join(map(str, libraries))
classes = root / 'target/client-transform-probe'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([shutil.which('javac'), '-cp', classpath, '-d', str(classes),
                str(root / 'tools/verification/client-transform-probe/ClientTransformProbe.java')], check=True)
subprocess.run([shutil.which('java'), '-cp', str(classes) + os.pathsep + classpath,
                'ClientTransformProbe', str(root / 'target/resource-pack-build/assets/tabletop3d/models/item')], check=True)
