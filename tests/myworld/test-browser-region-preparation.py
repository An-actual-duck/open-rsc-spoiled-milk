#!/usr/bin/env python3
"""Frozen-source binary tile/authority parity, plus actual candidate merge lifecycle."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-region-preparation','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar'
frozen='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
reference=suite/'frozen-source';reference.mkdir(exist_ok=True)
sources=['Client_Base/src/orsc/graphics/three/World.java','Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java','Client_Base/src/orsc/NativeLayeredTerrainChunk.java']
for relative in sources:
 p=reference/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(subprocess.check_output(['git','show',f'{frozen}:{relative}'],cwd=root))
fixture=root/'tests/myworld/BrowserRegionPreparationFixture.java'
outputs=[]
for variant in ['frozen','off','on']:
 classes=suite/(variant+'-classes');classes.mkdir(exist_ok=True)
 selected=[reference/x for x in sources] if variant=='frozen' else [root/x for x in sources]+[root/'Client_Base/src/orsc/NativePresentationMergeCache.java']
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes)]+[str(x) for x in selected]+[str(fixture)],check=True)
 output=suite/(variant+'.bin')
 # The frozen implementation must not resolve the candidate helper from the jar.
 # Run no candidate-only controls for that independently compiled oracle.
 subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true','-Dfixture.frozen='+str(variant=='frozen').lower(),'-Dspoiledmilk.browserRegionPreparation='+str(variant=='on').lower(),'-cp',str(classes)+':'+str(jar),'orsc.BrowserRegionPreparationFixture',str(output)],check=True)
 outputs.append(output.read_bytes())
assert outputs[0]==outputs[1]==outputs[2], 'Frozen/current OFF/ON authority or exact tile bytes differ'
print(f'PASS frozen {frozen[:9]} vs OFF/ON exact {len(outputs[0])}-byte source/authority corpus; no GPU/FPS claim')
