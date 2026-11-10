#!/usr/bin/env python3
"""Check native invisible-material parity and a frozen pre-fix regression control."""
from pathlib import Path
import subprocess
import json

root = Path(__file__).resolve().parents[2]
suite = Path(subprocess.check_output([
    'python3', str(root / 'scripts/workspace-layout.py'),
    'path', 'tests', 'browser-webgl', '--mkdir'
], text=True).strip())
output = suite / 'material-classes'
output.mkdir(exist_ok=True)
jar = root / 'Client_Base/Open_RSC_Client.jar'
subprocess.run([
    'javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', str(output),
    str(root / 'tests/myworld/BrowserChunkMaterialsFixture.java')
], check=True)
subprocess.run([
    'java', '-Djava.awt.headless=true', '-cp', str(output) + ':' + str(jar),
    'orsc.BrowserChunkMaterialsFixture'
], check=True)

# Prove that authentic invisible walls reproduce with the former browser encoder.
# It draws diagnostic WALL red where the native material decision skips a batch.
baseline = 'cb699e5c425d97b01bb357955c71b201879608ca'
old = suite / 'invisible-wall-old-source/orsc/BrowserChunkMaterials.java'
old.parent.mkdir(parents=True, exist_ok=True)
old.write_bytes(subprocess.check_output([
    'git', 'show', baseline + ':PC_Client/src/orsc/BrowserChunkMaterials.java'
], cwd=root))
old_classes = suite / 'invisible-wall-old-classes'
old_classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(jar),
                '-d', str(old_classes), str(old),
                str(root / 'tests/myworld/BrowserChunkMaterialsFixture.java')], check=True)
control = subprocess.run(['java', '-Djava.awt.headless=true', '-cp',
                          str(old_classes) + ':' + str(jar),
                          'orsc.BrowserChunkMaterialsFixture'], capture_output=True, text=True)
if control.returncode == 0 or 'Invisible wall emitted a color/shadow draw' not in control.stderr:
    raise AssertionError('Frozen original encoder did not reproduce invisible-wall regression: ' + control.stdout + control.stderr)
(suite / 'invisible-wall-result.json').write_text(json.dumps({
    'baseline': baseline, 'frozenControlReproduced': True,
    'nativePolicy': 'Textured missing-material batches are skipped',
    'sourceMapAndCollisionModified': False, 'gpuProof': False,
    'originalFailure': control.stderr.splitlines()[0]
}, indent=2) + '\n')
print('PASS frozen original browser encoder reproduces authentic invisible-wall color/shadow batches; fixed encoder skips them')
