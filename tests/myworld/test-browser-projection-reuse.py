#!/usr/bin/env python3
"""Frozen production model/Scene oracle, exact cache ownership and bounded JVM cost."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2];BASE='7450594cf98cc6212811b4f48f25fb4313e108fc'
def main():
 p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,default=Path('/home/justin/jdk8u482-b08'));a=p.parse_args();java=a.java_home/'bin'
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-projection-reuse','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();current=run/'current';current.mkdir();old=run/'baseline';old.mkdir();frozen=run/'source';frozen.mkdir();jar=ROOT/'Client_Base/Open_RSC_Client.jar'
 production=['Client_Base/src/orsc/graphics/three/'+n+'.java' for n in ['Scene','RSModel']]
 helper=ROOT/'Client_Base/src/orsc/graphics/three/BrowserProjectionReuse.java';fixtures=[ROOT/'tests/myworld'/n for n in ['ClericWellSpotClientTest.java','BrowserOwnershipGraphics.java','BrowserWallRoofOwnershipFixture.java','BrowserProjectionReuseFixture.java']]
 with(run/'compile.log').open('w')as log:
  subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,[*[ROOT/s for s in production],helper,*fixtures])],check=True,stdout=log,stderr=subprocess.STDOUT)
  for s in production:(frozen/Path(s).name).write_bytes(subprocess.check_output(['git','show',BASE+':'+s],cwd=ROOT))
  subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(old),*map(str,frozen.glob('*.java'))],check=True,stdout=log,stderr=subprocess.STDOUT)
 # Original rotate method body is literally preserved; native/default code never
 # enters the optional cache wrapper. Compare source, not merely fingerprints.
 old_model=(frozen/'RSModel.java').read_text();new_model=(ROOT/production[1]).read_text();method=lambda s:s[s.index('\tfinal void rotate1024('):s.index('\n\tprivate void rotate256(',s.index('\tfinal void rotate1024('))]
 assert method(old_model)==method(new_model),'Literal original rotation path changed'
 outputs={}
 for label,enabled in [('baseline',False),('current-off',False),('current-on',True)]:
  cp=(str(old)+':'if label=='baseline'else'')+str(current)+':'+str(jar)
  with(run/(label+'.log')).open('w')as log:subprocess.run([str(java/'java'),'-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.three.BrowserProjectionReuseFixture',str(enabled).lower()],check=True,stdout=log,stderr=subprocess.STDOUT)
  outputs[label]=[line for line in (run/(label+'.log')).read_text().splitlines()if line.startswith('DIGEST=')][-1]
 assert len(set(outputs.values()))==1,outputs
 for name,args in [('budget',['true','budget']),('jvm-cost',['bench']),('default',['default'])]:
  with(run/(name+'.log')).open('w')as log:subprocess.run([str(java/'java'),'-Xmx768m','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.BrowserProjectionReuseFixture',*args],check=True,stdout=log,stderr=subprocess.STDOUT)
 result={'passed':True,'base':BASE,'javaHome':str(a.java_home),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip()),'sourceSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in [*[ROOT/s for s in production],helper,*fixtures]},'dependencyJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'outputs':outputs,'scope':'1800 direct model frames+15 malformed/extreme fallbacks and960 real Scene frames each frozen/OFF/ON;pixel/pick/order/mesh digest;no GPU/FPS claim','budget':(run/'budget.log').read_text().strip(),'jvmCostExploratory':(run/'jvm-cost.log').read_text().splitlines()}
 (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'run':str(run),**result},indent=2))
if __name__=='__main__':main()
