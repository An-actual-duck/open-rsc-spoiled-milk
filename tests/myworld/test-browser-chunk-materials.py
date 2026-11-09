#!/usr/bin/env python3
"""Check exact material/index streams after removing per-index allocation."""
from pathlib import Path
import subprocess

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
