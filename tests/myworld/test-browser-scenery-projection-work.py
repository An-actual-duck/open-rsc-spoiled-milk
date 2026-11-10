#!/usr/bin/env python3
"""Independent frozen325 Scene/RSModel oracle; CPU-only, no FPS claim."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-scenery-projection-work','--mkdir'],text=True).strip())
run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
production=['Client_Base/src/orsc/graphics/three/'+n+'.java'for n in ['Scene','RSModel','ProjectedModelPickBounds']]
fixtures=['ClericWellSpotClientTest','BrowserOwnershipGraphics','BrowserWallRoofOwnershipFixture','SceneOwnershipCullFixture','BrowserModelPickBoundsFixture','BrowserSceneryProjectionFixture']
current=run/'current';current.mkdir();old=run/'baseline';old.mkdir()
def compile_into(dst,sources,cp):
 with(run/(dst.name+'-compile.log')).open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',cp,'-d',str(dst),*map(str,sources)],cwd=ROOT,check=True,stdout=log,stderr=subprocess.STDOUT)
compile_into(current,[*[ROOT/n for n in production],*[ROOT/'tests/myworld'/(n+'.java')for n in fixtures]],str(jar))
frozen=[]
for name in production:
 p=old/Path(name).name;p.write_bytes(subprocess.check_output(['git','show',BASE+':'+name],cwd=ROOT));frozen.append(p)
compile_into(old,frozen,str(current)+':'+str(jar))
# Authority and candidate must have exactly the same ordinary/native rotate method.
def rotate_method(text):return text[text.index('\tfinal void rotate1024('):text.index('\n\tprivate void rotate256(',text.index('\tfinal void rotate1024('))].split('\n\t/** Frame-local browser projection split;')[0].rstrip()
assert rotate_method((old/'RSModel.java').read_text())==rotate_method((ROOT/production[1]).read_text()),'Native rotate changed'
def invoke(version,mode,enabled,probe=None):
 cp=(str(probe)+':'if probe else'')+(str(old)+':'if version=='baseline'else'')+str(current)+':'+str(jar)
 name=(probe.name+'-'if probe else'')+version+'-'+mode+'-'+enabled
 with(run/(name+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.three.BrowserSceneryProjectionFixture',mode,enabled],check=True,stdout=log,stderr=subprocess.STDOUT)
 text=(run/(name+'.log')).read_text();digests=re.findall(r'^[0-9a-f]{64}$',text,re.M);assert len(digests)==1,text
 witness=re.findall(r'^WITNESS_SHA=([0-9a-f]{64})$',text,re.M)
 return {'digest':digests[0],'witness':witness,'counts':dict((key,int(value))for key,value in re.findall(r'^(PROJECTED_VERTICES|DEFERRED_VERTICES|PERSPECTIVE_DIVISIONS|BOUNDS_DIVISIONS)=(\d+)$',text,re.M))}
results={}
for mode in ['scene','direct']:
 for version,enabled in [('baseline','false'),('current','false'),('current','true'),('current','default')]:
  if mode=='direct'and enabled=='default':continue
  results[version+'-'+mode+'-'+enabled]=invoke(version,mode,enabled)
 assert len({v['digest']for k,v in results.items()if'-'+mode+'-'in k})==1,results
 if mode=='scene':assert len({tuple(v['witness'])for k,v in results.items()if'-scene-'in k})==1 and results['current-scene-true']['witness'],results
# Instrument separate source copies; no counters/timers in production.
probes={}
for version,enabled in [('baseline','false'),('current','true')]:
 dst=run/('probe-'+version);dst.mkdir();path=(old/'RSModel.java')if version=='baseline'else ROOT/production[1];text=path.read_text()
 marker=re.search(r'(public (?:final )?class RSModel[^\{]*\{)',text);assert marker
 text=text[:marker.end()]+ '\n public static long TEST_PROJECTED_VERTICES,TEST_DEFERRED_VERTICES,TEST_PERSPECTIVE_DIVISIONS,TEST_BOUNDS_DIVISIONS;'+text[marker.end():]
 needle='\t\t\t\t\tif (zO < zTop) {';assert text.count(needle)==2
 text=text.replace(needle,'\t\t\t\t\tTEST_PROJECTED_VERTICES++; TEST_PERSPECTIVE_DIVISIONS += zO < zTop ? 0 : 2;\n'+needle,1)
 if version=='current':
  text=text.replace('this.getPickBoundsScale(), viewportY, mouseX, mouseY)) return true;','this.getPickBoundsScale(), viewportY, mouseX, mouseY)) { TEST_DEFERRED_VERTICES += this.vertHead; return true; }')
  text=text.replace('int z = this.vertZRot[vertex];','int z = this.vertZRot[vertex]; TEST_PROJECTED_VERTICES++; TEST_PERSPECTIVE_DIVISIONS += z < zTop ? 0 : 2;')
  text=text.replace('if (minDepth >= zTop &&', 'TEST_BOUNDS_DIVISIONS += minDepth >= zTop ? 8 : 0;\n if (minDepth >= zTop &&')
 (dst/'RSModel.java').write_text(text);compile_into(dst,[dst/'RSModel.java'],(str(old)+':'if version=='baseline'else'')+str(current)+':'+str(jar))
 probes[version]=invoke(version,'scene',enabled,dst)
 assert probes[version]['digest']==results[version+'-scene-'+enabled]['digest']
assert probes['current']['counts']['DEFERRED_VERTICES']>0,probes
assert probes['current']['counts']['PROJECTED_VERTICES']<probes['baseline']['counts']['PROJECTED_VERTICES'],probes
assert probes['current']['counts']['PERSPECTIVE_DIVISIONS']+probes['current']['counts']['BOUNDS_DIVISIONS']<probes['baseline']['counts']['PERSPECTIVE_DIVISIONS'],probes
for name in ['SceneOwnershipCullFixture','BrowserWallRoofOwnershipFixture']:
 with(run/(name+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-Dspoiledmilk.browserSceneryProjectionWork=true','-Dspoiledmilk.browserModelPickBounds=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.'+name],check=True,stdout=log,stderr=subprocess.STDOUT)
receipt={'passed':True,'baseline':BASE,'sourceSha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest()for n in production+[str(Path('tests/myworld')/(f+'.java'))for f in fixtures]},'results':results,'testOnlyCounters':probes,'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'limits':'CPU frozen parity/work reduction only; private defaultOFF; no browser/GPU/FPS measurement'}
(run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'run':str(run),**receipt},indent=2))
