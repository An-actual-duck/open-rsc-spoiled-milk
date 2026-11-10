#!/usr/bin/env python3
"""CPU guards for explicit runtime options; no browser, GPU, server or JVM starts."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import time
ROOT=Path(__file__).resolve().parents[2]

def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-stage',required=True,type=Path);args=parser.parse_args()
    preparer=load('runtime_preparer',ROOT/'tests/myworld/prepare-browser-runtime-engine.py')
    server=load('runtime_server',ROOT/'tests/myworld/serve-browser-runtime-engine.py')
    metadata,raw,inputs,java=preparer.source_inputs(args.source_stage)
    category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-runtime-engine-matrix','--mkdir'],text=True).strip())
    suite=category/('cpu-'+str(time.time_ns()));suite.mkdir()
    receipts=[]
    for variant in preparer.VARIANTS:
        run=suite/variant;receipt=preparer.prepare(args.source_stage,run,variant,8188);receipts.append(receipt)
        assert server.verify(run)==run/'web'
        assert receipt['actualEngineObserved'] is None and receipt['actualJavaVersionObserved'] is None
        assert (run/'web/probe.jar').read_bytes()==inputs['/probe.jar']
        assert (run/'web/browser-native-call-dispatch.js').read_bytes()==inputs['/browser-native-call-dispatch.js']
        original=inputs['/browser-native-runtime-probe.js'].decode();changed=(run/'web/browser-native-runtime-probe.js').read_text()
        native=original[original.index('const scalar='):original.index('try{\n if(![')]
        assert native in changed,'Native implementation/wrapper changed'
        protocol=original[original.index(" const run=cheerpjRunMain"):original.index(' proof.passed=true;')]
        assert protocol in changed,'1903/void/fatal acceptance protocol changed'
        try:preparer.prepare(args.source_stage,run,variant,8188)
        except FileExistsError:pass
        else:raise AssertionError('Existing immutable stage overwritten')
        result=subprocess.run(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-runtime-engine-options.mjs'),str(run),variant],cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        (run/'cpu.log').write_text(result.stdout);print(result.stdout,end='');result.check_returncode()
    altered=suite/'altered-input';(altered/'web').mkdir(parents=True);(altered/'source').mkdir()
    (altered/'stage.json').write_bytes(raw);(altered/'source/NativeDispatchProbe.java').write_bytes(java)
    for name,value in inputs.items():(altered/'web'/name.lstrip('/')).write_bytes(value)
    (altered/'web/index.html').write_text('altered')
    output=suite/'must-not-exist'
    try:preparer.prepare(altered,output,'java17-js',8188)
    except ValueError:pass
    else:raise AssertionError('Altered reviewed artifact accepted')
    assert not output.exists(),'Invalid input wrote a stage'
    assert preparer.source_inputs(args.source_stage)[1:]==(raw,inputs,java),'Source stage changed'
    for variant,port in [('java8-js',8188),('java17-wasmgc',8188),('java17-js',80)]:
        try:preparer.prepare(args.source_stage,output,variant,port)
        except ValueError:pass
        else:raise AssertionError('Unapproved variant/port accepted')
    (suite/'result.json').write_text(json.dumps(dict(passed=True,cpuOnly=True,browserExecuted=False,jvmExecuted=False,sourceManifestSha256=hashlib.sha256(raw).hexdigest(),receipts=receipts),indent=2)+'\n')
    print('PASS exact v3 input provenance, immutable stage guards and unchanged1903 protocol; no authentic runtime/GPU/FPS claim')
if __name__=='__main__':main()
