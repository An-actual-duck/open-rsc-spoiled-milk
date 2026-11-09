#!/usr/bin/env python3
"""Exact pixel/coverage/SCENE contract across measured baseline and browser-only optimization."""
from pathlib import Path
import json
import subprocess
ROOT=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-frame-preparation','--mkdir'],text=True).strip())
jar=ROOT/'Client_Base/Open_RSC_Client.jar';current=out/'current';baseline=out/'baseline';current.mkdir(exist_ok=True);baseline.mkdir(exist_ok=True)
graphics='Client_Base/src/orsc/graphics/two/GraphicsController.java'
sources=[ROOT/graphics,ROOT/'tests/myworld/WorldOverlayCoverageFixture.java',ROOT/'tests/myworld/SceneSpriteRasterPolicyFixture.java',ROOT/'tests/myworld/BrowserFramePreparationFixture.java']
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,sources)],check=True)
old=out/'GraphicsController.java';old.write_bytes(subprocess.check_output(['git','show','67cfe60076f5d956c3a240165cf0f8c39a576f5b:'+graphics],cwd=ROOT))
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(baseline),str(old)],check=True)
flags=['-Djava.awt.headless=true','-Dspoiledmilk.openglWorldSpritesVisible=true','-Dspoiledmilk.openglWorldMeshTexturedVisible=true','-Dspoiled_milk.opengl_sprite_overlay=true','-Dspoiled_milk.opengl_sprite_overlay_mode=native-ui','-Dspoiled_milk.opengl_native_ui_replace=true','-Dspoiled_milk.opengl_ui_base_frame=true','-Dspoiledmilk.rendererTelemetry=true','-Dspoiledmilk.rendererTelemetryCountersOnly=true']
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.two.BrowserFramePreparationFixture','telemetry-off'],check=True,cwd=ROOT)
results=[]
for mode in ['browser','native','native-coverage','software','scratch','login']:
    rows=[]
    for label,cp,enabled in [('original',str(baseline)+':'+str(current)+':'+str(jar),'false'),('disabled',str(current)+':'+str(jar),'false'),('enabled',str(current)+':'+str(jar),'true')]:
        command=['java',*flags,'-Dspoiledmilk.browserFramePreparation='+enabled,'-cp',cp,'orsc.graphics.two.BrowserFramePreparationFixture',mode]
        output=subprocess.check_output(command,cwd=ROOT,text=True)
        row=json.loads(output.strip().splitlines()[-1]);rows.append(row)
    assert rows[0]==rows[1],('Disabled optimization changed baseline',mode,rows)
    for key in ['rgb','coverage','scene','sceneCommands']:
        assert rows[0][key]==rows[2][key],(mode,key,rows)
    if mode=='browser':
        assert rows[0]['commands']>0 and rows[2]['commands']==0,rows
    else:
        assert rows[0]['commands']==rows[2]['commands'],('Non-owner command stream changed',mode,rows)
        assert rows[0]['commandFingerprint']==rows[2]['commandFingerprint'],('Non-owner immutable command DTO changed',mode,rows)
    results.append({'mode':mode,'original':rows[0],'enabled':rows[2]})
(out/'parity.json').write_text(json.dumps(results,indent=2)+'\n')
print('PASS actual writer RGB/T, retained fade and SCENE snapshot equality; native/software/scratch/login streams unchanged; unused browser UI/world commands removed; bounded phase counters present.')
