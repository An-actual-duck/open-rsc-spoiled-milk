#!/usr/bin/env python3
from pathlib import Path
import subprocess,json
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-npc-layer-preparation','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar';current=out/'current';baseline=out/'baseline';current.mkdir(exist_ok=True);baseline.mkdir(exist_ok=True)
client='Client_Base/src/orsc/mudclient.java'
sources=[root/client,root/'Client_Base/src/orsc/graphics/two/GraphicsController.java',root/'tests/myworld/BrowserNpcLayerPreparationFixture.java']
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,sources)],check=True)
old=out/'mudclient.java';old.write_bytes(subprocess.check_output(['git','show','ee876dbe895d914a9df78088cec2ca35362ffc04:'+client],cwd=root))
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(baseline),str(old)],check=True)
results={}
for label,cp in [('baseline',str(baseline)+':'+str(current)+':'+str(jar)),('current',str(current)+':'+str(jar)),('disabled',str(current)+':'+str(jar))]:
 results[label]=subprocess.check_output(['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserNpcLayerPreparation='+str(label!='disabled').lower(),'-Dspoiledmilk.openglWorldSpritesVisible=true','-Dspoiledmilk.openglWorldMeshTexturedVisible=true','-Dspoiled_milk.opengl_sprite_overlay=true','-Dspoiled_milk.opengl_sprite_overlay_mode=phased','-Dspoiledmilk.openglWorldUiReplay=true','-cp',cp,'orsc.BrowserNpcLayerPreparationFixture'],cwd=root,text=True).strip().splitlines()[-1]
old=results['baseline'].split(':');new=results['current'].split(':')
assert old[:4]==new[:4],results
assert old[4]==new[4],('Native selector count changed',results)
assert int(new[5])<int(old[5]),('GPU preparation reuse not exercised',results)
assert results['disabled']==results['baseline'],('Disabled candidate changed original result or selector count',results)
(out/'parity.json').write_text(json.dumps(results,indent=2)+'\n');print('PASS actual drawNPC original/current command/RGB/production-GPU-composite parity: '+results['current'])
