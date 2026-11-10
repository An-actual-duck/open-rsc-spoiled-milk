#!/usr/bin/env python3
"""Current Scene versus pre-instrumentation source; enabled/disabled sampled clocks."""
from pathlib import Path
import json
import subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-scene-preparation','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar';current=out/'current';baseline=out/'baseline'
current.mkdir(exist_ok=True);baseline.mkdir(exist_ok=True)
scene='Client_Base/src/orsc/graphics/three/Scene.java'
fixtures=['ClericWellSpotClientTest.java','BrowserOwnershipGraphics.java','BrowserWallRoofOwnershipFixture.java','BrowserScenePreparationFixture.java']
sources=[root/scene,*[root/'tests/myworld'/name for name in fixtures]]
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,sources)],check=True)
old=out/'Scene.java';old.write_bytes(subprocess.check_output(['git','show','4a396089eb3badcb6af338c57b1b0a4c8837c228:'+scene],cwd=root))
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(baseline),str(old)],check=True)
results={}
for version,profile in [('baseline',False),('current',False),('current',True)]:
 cp=(str(baseline)+':' if version=='baseline' else '')+str(current)+':'+str(jar)
 result=subprocess.check_output(['java','-Djava.awt.headless=true','-Dspoiledmilk.rendererTelemetry='+str(profile).lower(),'-Dspoiledmilk.rendererTelemetryCountersOnly=true','-cp',cp,'orsc.graphics.three.BrowserScenePreparationFixture',version],cwd=root,text=True).strip().splitlines()[-1]
 results[version+('-profiled' if profile else '-plain')]=result
assert len(set(results.values()))==1,results
(out/'parity.json').write_text(json.dumps(results,indent=2)+'\n')
print('PASS exact Scene baseline/current/profiled pixels, mutable geometry/materials, picks, anchors, ordered actor calls, resident output and 1/32 sample/export/disabled-clock checks')
