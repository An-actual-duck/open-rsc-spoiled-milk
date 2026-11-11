#!/usr/bin/env python3
"""Frozen Scene/model differential for private whole-model CPU preparation bypass."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,subprocess,json,hashlib
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args();jar=a.client_jar.resolve()
BASE='8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-static-model-preparation','--mkdir'],text=True).strip())
run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();current=run/'current';old=run/'baseline';current.mkdir();old.mkdir()
paths=['Client_Base/src/orsc/graphics/three/Scene.java','Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/graphics/three/BrowserStaticModelBounds.java']
fixtures=['ClericWellSpotClientTest.java','BrowserOwnershipGraphics.java','BrowserWallRoofOwnershipFixture.java','BrowserStaticModelPreparationFixture.java']
with(run/'compile.log').open('w')as log:
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*[str(ROOT/q)for q in paths],*[str(ROOT/'tests/myworld'/q)for q in fixtures]],check=True,stdout=log,stderr=subprocess.STDOUT)
 frozen=[]
 for path in paths[:2]:
  q=old/Path(path).name;q.write_bytes(subprocess.check_output(['git','show',BASE+':'+path],cwd=ROOT));frozen.append(q)
 subprocess.run(['javac','-source','8','-target','8','-cp',str(current)+':'+str(jar),'-d',str(old),*map(str,frozen)],check=True,stdout=log,stderr=subprocess.STDOUT)
rows={}
for label,enabled in [('baseline','false'),('baseline','true'),('current','false'),('current','true'),('current','default')]:
 name=label+'-'+enabled;cp=(str(old)+':'if label=='baseline'else'')+str(current)+':'+str(jar)
 with(run/(name+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.three.BrowserStaticModelPreparationFixture',label,enabled],check=True,stdout=log,stderr=subprocess.STDOUT)
 lines=(run/(name+'.log')).read_text().strip().splitlines();rows[name]={'digest':lines[-1],'work':lines[-2]}
assert len({r['digest']for r in rows.values()})==1,rows
for mode in ['math','guards']:
 with(run/(mode+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.BrowserStaticModelPreparationFixture',mode],check=True,stdout=log,stderr=subprocess.STDOUT)
bootstrap=run/'bootstrap';bootstrap.mkdir()
with(run/'bootstrap.log').open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',str(current)+':'+str(jar),'-d',str(bootstrap),*[str(q)for q in sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').rglob('*.java'))]],check=True,stdout=log,stderr=subprocess.STDOUT)
meta={'passed':True,'baseline':BASE,'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'rows':rows,'math':(run/'math.log').read_text().strip(),'guards':(run/'guards.log').read_text().strip(),'scope':'960 real Scene frames per cohort; exact picks/occlusion/anchors/ordered calls/resident meshes/pixels/native/default, mutable topology, signed levels;200000 conservative math cases; CPU only, no browser/FPS claim'}
(run/'result.json').write_text(json.dumps(meta,indent=2)+'\n');print(json.dumps({'run':str(run),**meta},indent=2))
