#!/usr/bin/env python3
"""Frozen real Scene and pure rejection differential. No GPU/browser/FPS claims."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='2b0681f841298d840ca13ee0ebb5a2ec7d9f6888'
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-model-pick-bounds','--mkdir'],text=True).strip())
run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();current=run/'current';old=run/'baseline';current.mkdir();old.mkdir();jar=ROOT/'Client_Base/Open_RSC_Client.jar';scene='Client_Base/src/orsc/graphics/three/Scene.java'
fixtures=['ClericWellSpotClientTest.java','BrowserOwnershipGraphics.java','BrowserWallRoofOwnershipFixture.java','SceneOwnershipCullFixture.java','BrowserModelPickBoundsFixture.java']
sources=[ROOT/scene,ROOT/'Client_Base/src/orsc/graphics/three/ProjectedModelPickBounds.java',*[ROOT/'tests/myworld'/name for name in fixtures]]
with(run/'compile.log').open('w')as log:
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,sources)],check=True,stdout=log,stderr=subprocess.STDOUT)
 frozen=run/'Scene.java';frozen.write_bytes(subprocess.check_output(['git','show',BASE+':'+scene],cwd=ROOT))
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(old),str(frozen)],check=True,stdout=log,stderr=subprocess.STDOUT)
results={}
for version,enabled in [('baseline',False),('baseline',True),('current',False),('current',True),('current',None)]:
 name=version+('-default'if enabled is None else'-on'if enabled else'-off');cp=(str(old)+':'if version=='baseline'else'')+str(current)+':'+str(jar)
 with(run/(name+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.three.BrowserModelPickBoundsFixture',version,'default'if enabled is None else str(enabled).lower()],check=True,stdout=log,stderr=subprocess.STDOUT)
 results[name]=(run/(name+'.log')).read_text().strip().splitlines()[-1]
assert len(set(results.values()))==1,results
with(run/'math.log').open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.BrowserModelPickBoundsFixture','math'],check=True,stdout=log,stderr=subprocess.STDOUT)
for fixture in ['SceneOwnershipCullFixture','BrowserWallRoofOwnershipFixture']:
 with(run/(fixture+'.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-Dspoiledmilk.browserModelPickBounds=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.'+fixture],check=True,stdout=log,stderr=subprocess.STDOUT)
# Isolated source copies count actual helper calls without shipping production counters/timers.
probe_results={}
helper=(ROOT/'Client_Base/src/orsc/graphics/three/ProjectedModelPickBounds.java').read_text()
helper=helper.replace('final class ProjectedModelPickBounds {','final class ProjectedModelPickBounds {\n static long TEST_MODEL_SCANS,TEST_MODEL_REJECTIONS,TEST_FACE_VALIDATIONS;')
helper=helper.replace('static boolean couldContainMouse(RSModel model, int viewportY, int mouseX, int mouseY) {','static boolean couldContainMouse(RSModel model, int viewportY, int mouseX, int mouseY) {\n TEST_MODEL_SCANS++;')
helper=helper.replace('return mouseX >= (double) minX - paddingX', 'boolean result=mouseX >= (double) minX - paddingX').replace('&& mouseY <= (double) maxY + paddingY;', '&& mouseY <= (double) maxY + paddingY;\n if(!result)TEST_MODEL_REJECTIONS++; return result;')
helper=helper.replace('static boolean hasOnlyRotatedVertexIndices(RSModel model, int[] indices, int count) {','static boolean hasOnlyRotatedVertexIndices(RSModel model, int[] indices, int count) {\n TEST_FACE_VALIDATIONS++;')
for version,enabled in [('baseline',False),('current',True)]:
 directory=run/('probe-'+version);directory.mkdir();scene_text=(frozen if version=='baseline' else ROOT/scene).read_text()
 scene_text=scene_text.replace('public final class Scene {','public final class Scene {\n public static long TEST_FACE_BOUNDS_CALLS;').replace('private boolean projectedFaceCouldContainMouse(RSModel model, int[] faceIndices, int vertexCount) {','private boolean projectedFaceCouldContainMouse(RSModel model, int[] faceIndices, int vertexCount) {\n TEST_FACE_BOUNDS_CALLS++;')
 (directory/'Scene.java').write_text(scene_text);(directory/'ProjectedModelPickBounds.java').write_text(helper)
 with(run/(version+'-probe-compile.log')).open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',str(current)+':'+str(jar),'-d',str(directory),str(directory/'Scene.java'),str(directory/'ProjectedModelPickBounds.java')],check=True,stdout=log,stderr=subprocess.STDOUT)
 with(run/(version+'-probe.log')).open('w')as log:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(directory)+':'+str(current)+':'+str(jar),'orsc.graphics.three.BrowserModelPickBoundsFixture',version,str(enabled).lower()],check=True,stdout=log,stderr=subprocess.STDOUT)
 lines=(run/(version+'-probe.log')).read_text().strip().splitlines();assert lines[-1]==next(iter(results.values()))
 probe_results[version]={line.split('=')[0]:int(line.split('=')[1])for line in lines[:-1]if'='in line}
assert probe_results['current']['PROBE_FACE_CALLS']<probe_results['baseline']['PROBE_FACE_CALLS'],probe_results
assert probe_results['current']['TEST_MODEL_REJECTIONS']>0 and probe_results['current']['TEST_FACE_VALIDATIONS']>0,probe_results
# Compile private bootstrap against freshly compiled Scene, never a stale getter stub.
bootstrap=run/'bootstrap';bootstrap.mkdir()
with(run/'bootstrap-compile.log').open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',str(current)+':'+str(jar),'-d',str(bootstrap),*[str(p)for p in sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').rglob('*.java'))]],check=True,stdout=log,stderr=subprocess.STDOUT)
meta={'passed':True,'baseline':BASE,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sources},'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'outputs':results,'testOnlyWorkCounters':probe_results,'scope':'576 real Scene frames each baseline/current, OFF/ON; frozen pick/order/resident/pixel digest;200000 conservative integer/scale cases;no browser/GPU/FPS claim'}
(run/'result.json').write_text(json.dumps(meta,indent=2)+'\n');print(json.dumps({'passed':True,'run':str(run),'outputs':results,'math':(run/'math.log').read_text().strip(),'testOnlyWorkCounters':probe_results},indent=2))
