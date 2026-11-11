#!/usr/bin/env python3
"""Exact bounded arithmetic/staging tests; no performance inference."""
from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-region-build','--mkdir'],text=True).strip())
classes=suite/'helper-classes';classes.mkdir(exist_ok=True)
sources=['Client_Base/src/orsc/graphics/three/BrowserRegionBuild.java','tests/myworld/BrowserRegionBuildFixture.java']
subprocess.run(['javac','-source','8','-target','8','-d',str(classes)]+[str(root/s) for s in sources],check=True)
results=[]
for flags,expected in [([],False),(['-Dspoiledmilk.browserRegionBuild=true'],False),(['-Dspoiledmilk.browserBenchmark=true'],False),(['-Dspoiledmilk.browserRegionBuild=true','-Dspoiledmilk.browserBenchmark=true'],True)]:
 p=subprocess.run(['java','-Xmx256m']+flags+['-cp',str(classes),'orsc.graphics.three.BrowserRegionBuildFixture',str(expected).lower()],check=True,text=True,capture_output=True)
 results.append({'flags':flags,'expected':expected,'stdout':p.stdout,'stderr':p.stderr})
receipt={'sourceInputs':{s:hashlib.sha256((root/s).read_bytes()).hexdigest() for s in sources},'results':results,'scope':'CPU literal FNV/primitive array equality and gating only'}
(suite/'helper-result.json').write_text(json.dumps(receipt,indent=2)+'\n')
for row in results: print(row['stdout'],end='')
