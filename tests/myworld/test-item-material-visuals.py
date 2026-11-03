#!/usr/bin/env python3
"""Packaged runtime rendering and palette-mask isolation regression checks."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'


def main():
    subprocess.run(['python3', 'tools/generators/generate-item-visual-appearances.py', '--check'], cwd=ROOT, check=True)
    manifest = json.loads((ROOT/'dev/myworld/assets/authoring/items/material-visuals/manifest.json').read_text())
    for relative, digest in manifest['files'].items():
        assert hashlib.sha256((ROOT/relative).read_bytes()).hexdigest() == digest
    references = ROOT/'dev/myworld/assets/references/items'
    runtime = ROOT/'dev/myworld/assets/runtime/sprites/items/inventory-ground'
    for source, target in [('weapons/staves/id-02235.png','weapons/staves/staff-wood-base.png'),
                           ('weapons/staves/id-02241.png','weapons/staves/staff-orb-base.png'),
                           ('weapons/crossbows/id-02199.png','weapons/crossbows/crossbow-wood-base.png'),
                           ('ammunition/bolts/id-02206.png','ammunition/bolts/metal-bolts-base.png'),
                           ('materials/wood/id-02164.png','materials/wood/log-wood-base.png')]:
        a = Image.open(references/source).convert('RGBA')
        b = Image.open(runtime/target).convert('RGBA')
        assert a.size == b.size == (48,32) and a.getchannel('A').tobytes() == b.getchannel('A').tobytes()
        for before, after in zip(a.get_flattened_data(),b.get_flattened_data()):
            r,g,blue,alpha = before
            if not alpha: continue
            if 'crossbow' in target and not (r>g>blue): assert before == after, 'String/metal changed'
            if 'orb' in target:
                if r==g==blue: assert before == after, 'Orb changed'
                else: assert after[0]==after[1] and after[2]!=after[1], 'Shaft must use blue mask'
    with tempfile.TemporaryDirectory(prefix='material-visuals-') as temp:
        jar = ROOT/'Client_Base/Open_RSC_Client.jar'
        subprocess.run([str(JAVA/'javac'), '-cp', str(jar), '-d', temp,
                        str(ROOT/'tests/myworld/MaterialVisualClientFixture.java')], check=True)
        subprocess.run([str(JAVA/'java'), '-Xmx384m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
                        '-cp', temp+os.pathsep+str(jar), 'orsc.MaterialVisualClientFixture', str(ROOT)],
                       cwd=temp, check=True)
    print('PASS native icon alpha/scale, crossbow string/metal isolation and independent staff shaft/orb masks')


if __name__ == '__main__':
    main()
