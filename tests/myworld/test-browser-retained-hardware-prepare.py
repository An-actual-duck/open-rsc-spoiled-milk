#!/usr/bin/env python3
"""CPU-only contracts for an already prepared immutable functional stage.
Does not launch a browser or HTTP server. RUN is the printed prepare destination.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
run=Path(sys.argv[1]).resolve()
receipt=json.loads((run/'stage.json').read_text())
assert receipt['preparedOnly'] and receipt['gpuExecuted'] is False
assert receipt['nativeOracleFrames']==117
assert not (run/'chrome-profile').exists() and not (run/'hardware-result.json').exists()
assert len(receipt['webFiles'])==121
for url,item in receipt['webFiles'].items():
    file=run/'web'/url.lstrip('/')
    assert not file.is_symlink() and file.is_file()
    data=file.read_bytes()
    assert len(data)==item['bytes'] and hashlib.sha256(data).hexdigest()==item['sha256'],url
assert hashlib.sha256((run/'web/browser-retained-frame.js').read_bytes()).hexdigest()==receipt['moduleSha256']
native=json.loads((run/'web/oracle/oracle.json').read_text())
assert len(native['steps'])==50 and native['countCases']==16
# Native RGB-floor oracle, including the retained-white behavior that affine
# transmittance does not reproduce. Coordinate0,1 is untouched by all UI boxes.
for name,value in [('pattern',255),('full-10',121),('full-30',16),('full-50',0)]:
    image=(run/'web/oracle'/(name+'.rgba')).read_bytes();at=native['width']*4
    assert image[at:at+4]==bytes([value,value,value,255]),name
for file in ['browser-retained-hardware.js','run-browser-retained-hardware.mjs']:
    subprocess.run(['node','--check',str(ROOT/'tests/myworld'/file)],check=True)
# This intentionally exercises only the guard before any stage access, profile,
# HTTP server or Chrome spawn. Importing the runner without explicit flag fails.
result=subprocess.run(['node',str(ROOT/'tests/myworld/run-browser-retained-hardware.mjs')],capture_output=True,text=True)
assert result.returncode!=0 and 'Explicit --run-hardware RUN is required' in result.stderr
assert not (run/'chrome-profile').exists() and not (run/'hardware-result.json').exists()
print('PASS prepared-only source hashes,117 native frames, retained-white oracle, explicit-run guard and syntax. GPU remains unrun.')
