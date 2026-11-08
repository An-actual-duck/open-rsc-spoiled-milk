#!/usr/bin/env python3
"""Fresh private native world + decoded-packet fixture; never a live host/account DB."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
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
# Fat core.jar contains legacy Log4j; use the supported launcher order on every JVM.
logging_libs = [ROOT/'server/lib'/name for name in
    ['log4j-api-2.17.0.jar', 'log4j-core-2.17.0.jar', 'log4j-iostreams-2.17.0.jar']]
libs = sorted((ROOT/'server/lib').glob('*.jar'))
other_libs = [path for path in libs if path not in logging_libs]
java = str(Path(shutil.which('java')).resolve(strict=True))
javac = str(Path(shutil.which('javac')).resolve(strict=True))
common = [*logging_libs, ROOT/'output/tests/combat/test-classes', core, *other_libs]
source = ROOT/'server/test/com/openrsc/server/combat/IdleWalkAdmissionFixture.java'
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

receipt = {'mode': a.mode, 'core': str(core), 'coreSha256': digest(core),
    'fixtureSourceSha256': digest(source), 'configSha256': digest(runtime/'myworld.conf'),
    'sourceCommit': subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
    'javaExecutable': java, 'javacExecutable': javac,
    'javaVersion': subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True),
    'javacVersion': subprocess.check_output([javac,'-version'],stderr=subprocess.STDOUT,text=True),
    'state': 'before-compile', 'exitCode': None,
    'scope': 'Decoded packet -> unchanged filter -> FIFO -> parser/processor -> authoritative movement; no native input or login'}
if overlay:
    receipt['overlay'] = {str(f.relative_to(overlay)): digest(f) for f in sorted(overlay.rglob('*.class'))}
receipt_path=out/'receipt.json'
def save_receipt():
    receipt_path.write_text(json.dumps(receipt, indent=2)+'\n')

save_receipt()
try:
    subprocess.run([javac, '-source', '8', '-target', '8', '-cp', os.pathsep.join(map(str,common)),
        '-d', str(classes), str(source)], check=True)
    cp = [*logging_libs, classes, ROOT/'output/tests/combat/test-classes',
        *([overlay] if overlay else []), core, *other_libs]
    command = [java, '-Xmx768m', '-XX:+UseSerialGC', '-cp', os.pathsep.join(map(str,cp)),
        'com.openrsc.server.combat.IdleWalkAdmissionFixture', a.mode, str(out/'result.json')]
    receipt.update(command=command,state='before-launch',
        fixtureClasses={str(f.relative_to(classes)): digest(f) for f in sorted(classes.rglob('*.class'))},
        harnessClasses={str(f.relative_to(ROOT)): digest(f) for f in sorted((ROOT/'output/tests/combat/test-classes').rglob('*.class'))},
        libraries={str(f.relative_to(ROOT)): digest(f) for f in libs})
    save_receipt()
    with (out/'fixture.log').open('w') as log:
        completed = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=120)
    receipt.update(state='finished',exitCode=completed.returncode)
except subprocess.TimeoutExpired:
    receipt.update(state='timed-out',timeoutSeconds=120)
    raise SystemExit('Fixture timed out; provenance and logs preserved at '+str(out))
except subprocess.CalledProcessError as error:
    receipt.update(state='compile-failed',exitCode=error.returncode)
    raise
finally:
    save_receipt()
if completed.returncode:
    print((out/'fixture.log').read_text()[-4000:], file=sys.stderr)
    raise SystemExit(completed.returncode)
data = json.loads((out/'result.json').read_text())
print(json.dumps({k:v for k,v in data.items() if k!='phaseSweep'}, sort_keys=True))
print('Phase result:', {k:v for k,v in data['phaseSweep'].items() if k!='samples'})
print('Evidence:', out)
