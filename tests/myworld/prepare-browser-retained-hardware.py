#!/usr/bin/env python3
"""Default prepares an immutable isolated functional test stage. GPU/browser/server
launch is available only through the explicit --run-hardware option."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def prepare(run_id):
    if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,79}',run_id):
        raise ValueError('run-id must be a plain bounded name')
    base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-retained-frame','--mkdir'],text=True).strip())
    run=base/'hardware'/run_id
    run.mkdir(parents=True,exist_ok=False)
    web=run/'web';web.mkdir();oracle=web/'oracle';oracle.mkdir()
    source=run/'source';source.mkdir();classes=run/'classes';classes.mkdir()
    inputs={
        'browser-retained-frame.js':ROOT/'tools/browser-gameplay/web/browser-retained-frame.js',
        'index.html':ROOT/'tests/myworld/browser-retained-hardware.html',
        'browser-retained-hardware.js':ROOT/'tests/myworld/browser-retained-hardware.js',
    }
    for name,path in inputs.items():shutil.copyfile(path,web/name)
    graphics=ROOT/'Client_Base/src/orsc/graphics/two/GraphicsController.java'
    fixture=ROOT/'tests/myworld/BrowserRetainedHardwareOracle.java'
    shutil.copyfile(graphics,source/graphics.name);shutil.copyfile(fixture,source/fixture.name)
    jar=ROOT/'Client_Base/Open_RSC_Client.jar'
    if not jar.is_file():raise FileNotFoundError('Build client dependencies before preparing hardware oracle')
    with (run/'native-oracle.log').open('w') as log:
        subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(source/graphics.name),str(source/fixture.name)],stdout=log,stderr=subprocess.STDOUT,check=True)
        subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserRetainedHardwareOracle',str(oracle)],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True)
    native=json.loads((oracle/'oracle.json').read_text())
    assert native['width']==256 and native['height']==128 and len(native['steps'])==50
    frames=list(oracle.glob('*.rgba'));assert len(frames)==117
    for path in frames:
        data=path.read_bytes();assert len(data)==256*128*4 and all(value==255 for value in data[3::4]),path.name
    files={'/'+str(path.relative_to(web)):dict(sha256=sha(path),bytes=path.stat().st_size) for path in sorted(web.rglob('*')) if path.is_file()}
    receipt={
        'preparedOnly':True,'gpuExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),
        'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'moduleSource':str(inputs['browser-retained-frame.js'].relative_to(ROOT)),
        'moduleSha256':sha(web/'browser-retained-frame.js'),'graphicsSourceSha256':sha(source/graphics.name),
        'oracleSourceSha256':sha(source/fixture.name),'dependencyJarSha256':sha(jar),
        'webFiles':files,'nativeOracleFrames':len(frames),'origin':'GPU bottom-left fixture convention',
        'scope':'Prepared actual-native oracle and frozen functional test assets; no shader/GPU/FPS claim',
        'explicitRunCommand':['node',str(ROOT/'tests/myworld/run-browser-retained-hardware.mjs'),'--run-hardware',str(run)],
    }
    (run/'stage.json').write_text(json.dumps(receipt,indent=2)+'\n')
    return run

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    modes=parser.add_mutually_exclusive_group();modes.add_argument('--prepare-only',action='store_true');modes.add_argument('--run-hardware',action='store_true')
    args=parser.parse_args();run=prepare(args.run_id)
    print(json.dumps({'prepared':str(run),'gpuExecuted':False,'status':'PREPARED_ONLY'},indent=2),flush=True)
    if args.run_hardware:
        subprocess.run(['node',str(ROOT/'tests/myworld/run-browser-retained-hardware.mjs'),'--run-hardware',str(run)],cwd=ROOT,check=True)
if __name__=='__main__':main()
