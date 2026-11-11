#!/usr/bin/env python3
"""Frozen literal-plane and acknowledged-delta oracle. Prepares but never starts GPU test."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,subprocess,shutil
ROOT=Path(__file__).resolve().parents[2];BASE='8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
p=argparse.ArgumentParser();p.add_argument('--client-jar',type=Path,required=True);args=p.parse_args();jar=args.client_jar.resolve()
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-delta-transport','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();current=run/'current';old=run/'baseline';current.mkdir();old.mkdir();web=run/'web';web.mkdir();data=web/'data';data.mkdir()
coverage='Client_Base/src/orsc/graphics/two/WorldOverlayCoverage.java';tracker='Client_Base/src/orsc/graphics/two/WorldOverlayDeltaTransport.java';bridge='PC_Client/src/orsc/BrowserWebGLBridge.java'
fixtures=['WorldOverlayDeltaTransportFixture.java','WorldOverlayCoverageFixture.java','SceneSpriteRasterPolicyFixture.java']
def execute(command,log):
 with(run/log).open('w')as out:subprocess.run(command,check=True,cwd=ROOT,stdout=out,stderr=subprocess.STDOUT)
 return(run/log).read_text().strip()
execute(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*[str(ROOT/q)for q in [coverage,tracker,bridge]],*[str(ROOT/'tests/myworld'/q)for q in fixtures]],'compile.log')
frozen=old/'WorldOverlayCoverage.java';frozen.write_bytes(subprocess.check_output(['git','show',BASE+':'+coverage],cwd=ROOT));execute(['javac','-source','8','-target','8','-cp',str(current)+':'+str(jar),'-d',str(old),str(frozen)],'baseline-compile.log')
rows={}
for label,policy in [('baseline','false'),('baseline','true'),('current','false'),('current','true')]:
 cp=(str(old)+':'if label=='baseline'else'')+str(current)+':'+str(jar);rows[label+'-'+policy]=execute(['java','-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.two.WorldOverlayDeltaTransportFixture','literal',policy],label+'-'+policy+'.log').splitlines()[-1]
assert len(set(rows.values()))==1,rows
state=execute(['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.two.WorldOverlayDeltaTransportFixture','state',str(data)],'state.log')
execute(['node',str(ROOT/'tests/myworld/test-browser-overlay-delta.mjs')],'node-delta.log');execute(['node',str(ROOT/'tests/myworld/test-browser-overlay.mjs')],'node-baseline-overlay.log')
# Literal production compositor shader source stays unchanged.
original=subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/browser-overlay.js'],cwd=ROOT,text=True);present=(ROOT/'tools/browser-gameplay/web/browser-overlay.js').read_text();assert next(x for x in original.splitlines()if 'fragment=shader' in x)==next(x for x in present.splitlines()if 'fragment=shader' in x)
for name in ['browser-overlay.js','browser-overlay-delta.js','browser-retained-frame.js']:shutil.copy2(ROOT/'tools/browser-gameplay/web'/name,web/name)
for name in ['browser-overlay-delta-hardware.html','browser-overlay-delta-hardware.js']:shutil.copy2(ROOT/'tests/myworld'/name,web/name)
shutil.copy2(web/'browser-overlay-delta-hardware.html',web/'index.html')
meta=dict(passed=True,cpuOnly=True,processesStarted=False,baseline=BASE,sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),cleanSource=not subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip(),jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),literalPlaneDigests=rows,state=state,web=str(web),httpCommand=['python3','-m','http.server','--bind','127.0.0.1','--directory',str(web),'8178'],url='http://127.0.0.1:8178/',webHashes={f.name:hashlib.sha256(f.read_bytes()).hexdigest()for f in web.iterdir()if f.is_file()},dataHashes={f.name:hashlib.sha256(f.read_bytes()).hexdigest()for f in data.iterdir()if f.is_file()},sourceInputs={q:hashlib.sha256((ROOT/q).read_bytes()).hexdigest()for q in [coverage,tracker,bridge]+['tests/myworld/'+q for q in fixtures]},scope='CPU only. Complete literal RGB/T planes, fresh clear, first full/zero-touch/disappearing/moving UI, held source, retained/opaque/resize/ownerchange, invalid writers and stale/duplicate/superseded/mutated ACK. GL-double protocol passes; prepared real WebGL pixel comparison has NOT run.')
(run/'result.json').write_text(json.dumps(meta,indent=2)+'\n');print(json.dumps({'run':str(run),**{k:meta[k]for k in ['passed','cpuOnly','processesStarted','sourceCommit','cleanSource','literalPlaneDigests','state','web','httpCommand','url','scope']}},indent=2))
