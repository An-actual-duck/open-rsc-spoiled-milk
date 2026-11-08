#!/usr/bin/env python3
"""Fresh private native world + decoded-packet fixture; never a live host/account DB."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--mode', choices=['baseline', 'candidate'], required=True)
p.add_argument('--core', type=Path, required=True)
p.add_argument('--overlay', type=Path)
p.add_argument('--run', required=True, help='New run label; existing results are never overwritten')
a = p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', a.run):
    p.error('Use a simple run label')
base = Path(subprocess.check_output([sys.executable, str(ROOT/'scripts/workspace-layout.py'),
    'path', 'tests', 'idle-walk-admission', '--mkdir'], text=True).strip())
out = base/a.run
out.mkdir(exist_ok=False)
runtime = out/'runtime'
subprocess.run([sys.executable, str(ROOT/'tests/myworld/prepare-cleric-well-fixture.py'),
    str(runtime)], check=True, stdout=subprocess.DEVNULL)
config = (runtime/'myworld.conf').read_text()
config = config.replace('world:\n', 'world:\n\tnpc_capacity: 4000\n', 1)
(runtime/'myworld.conf').write_text(config)
core = a.core.resolve(strict=True)
overlay = a.overlay.resolve(strict=True) if a.overlay else None
classes = out/'fixture-classes'
classes.mkdir()
libs = sorted((ROOT/'server/lib').glob('*.jar'))
common = [ROOT/'output/tests/combat/test-classes', core, *libs]
source = ROOT/'server/test/com/openrsc/server/combat/IdleWalkAdmissionFixture.java'
subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', os.pathsep.join(map(str,common)),
    '-d', str(classes), str(source)], check=True)
cp = [classes, ROOT/'output/tests/combat/test-classes', *([overlay] if overlay else []), core, *libs]
command = ['java', '-Xmx768m', '-XX:+UseSerialGC', '-cp', os.pathsep.join(map(str,cp)),
    'com.openrsc.server.combat.IdleWalkAdmissionFixture', a.mode, str(out/'result.json')]
with (out/'fixture.log').open('w') as log:
    completed = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=120)
receipt = {'mode': a.mode, 'core': str(core), 'coreSha256': hashlib.sha256(core.read_bytes()).hexdigest(),
    'fixtureSourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'configSha256': hashlib.sha256((runtime/'myworld.conf').read_bytes()).hexdigest(),
    'command': command, 'exitCode': completed.returncode,
    'scope': 'Decoded packet -> unchanged filter -> FIFO -> parser/processor -> authoritative movement; no native input or login'}
if overlay:
    receipt['overlay'] = {str(f.relative_to(overlay)): hashlib.sha256(f.read_bytes()).hexdigest()
        for f in sorted(overlay.rglob('*.class'))}
(out/'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n')
if completed.returncode:
    print((out/'fixture.log').read_text()[-4000:], file=sys.stderr)
    raise SystemExit(completed.returncode)
data = json.loads((out/'result.json').read_text())
print(json.dumps({k:v for k,v in data.items() if k!='phaseSweep'}, sort_keys=True))
print('Phase result:', {k:v for k,v in data['phaseSweep'].items() if k!='samples'})
print('Evidence:', out)
