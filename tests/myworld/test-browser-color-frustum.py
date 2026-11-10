#!/usr/bin/env python3
"""Prepare-only actual shader oracle, CPU conservative math and API/shadow contracts."""
from pathlib import Path
import json,subprocess
ROOT=Path(__file__).resolve().parents[2]
prepared=json.loads(subprocess.check_output(['python3',str(ROOT/'tests/myworld/prepare-browser-color-frustum-hardware.py'),'--prepare-only'],text=True));run=Path(prepared['prepared']);assert prepared['gpuExecuted']is False
meta=json.loads((run/'stage.json').read_text());assert meta['preparedOnly']and meta['gpuExecuted']is False
assert not (run/'hardware-result.json').exists()and not (run/'chrome-profile').exists(),'prepare-only launched hardware'
shader=run/'web/shaders/world.vert'
with (run/'cpu-math.log').open('w')as log:subprocess.run(['node',str(ROOT/'tests/myworld/test-browser-color-frustum.mjs'),str(shader)],cwd=ROOT,check=True,stdout=log,stderr=subprocess.STDOUT)
with (run/'api-contract.log').open('w')as log:subprocess.run(['node',str(ROOT/'tests/myworld/test-browser-gpu-cache.mjs'),'',str(shader)],cwd=ROOT,check=True,stdout=log,stderr=subprocess.STDOUT)
print('PASS prepared-only actual production shader + conservative bounds math + complete residency/shadow API contracts; GPU UNRUN: '+str(run))
