#!/usr/bin/env python3
"""Actual supported-writer occupancy CPU oracle, no browser/GPU/server."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-rect-transport','--mkdir'],text=True).strip())/'writer-observation';out.mkdir(exist_ok=True)
sources=[ROOT/'Client_Base/src/orsc/graphics/two'/name for name in ['WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java']]
sources.append(ROOT/'tests/myworld/WorldOverlayTileTrackerFixture.java')
classes=out/'classes';classes.mkdir(exist_ok=True);jar=ROOT/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
command=['java','-Djava.awt.headless=true','-Dspoiledmilk.browserOverlayDeltaTransport=true','-Dspoiledmilk.browserOverlayRectMode=observe','-cp',str(classes)+':'+str(jar),'orsc.graphics.two.WorldOverlayTileTrackerFixture']
result=subprocess.run(command,check=True,capture_output=True,text=True);(out/'cpu.log').write_text(result.stdout);print(result.stdout,end='')
(out/'result.json').write_text(json.dumps({'cpuPassed':True,'gpuExecuted':False,'sourceSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sources}},indent=2)+'\n')
