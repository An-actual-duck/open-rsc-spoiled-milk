#!/usr/bin/env python3
"""Frozen/current primitive height parity, with test-only locator operation evidence."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-halo-height-lookup','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar';frozen='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
paths=['Client_Base/src/orsc/graphics/three/World.java','Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java','Client_Base/src/orsc/NativeLayeredTerrainChunk.java']
old={p:subprocess.check_output(['git','show',f'{frozen}:{p}'],cwd=root,text=True) for p in paths}
new={p:(root/p).read_text() for p in paths}
def method(source,marker):
 start=source.index(marker);end=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
for marker in ['\tprivate NativeLayeredTerrainChunk findAvailableChunk(','\tpublic int getGroundElevation(','\tpublic boolean covers(','\tpublic Tile createTile(']:
 assert method(old[paths[1]],marker)==method(new[paths[1]],marker), 'Original snapshot method changed: '+marker
marker='\tprivate int presentationVertexElevation('
assert method(old[paths[0]],marker).split('\t\tNativeLayeredTerrainSnapshot source = null;')[0]==method(new[paths[0]],marker).split('\t\tif (isBrowserHaloHeightLookupEnabled())')[0], 'Loaded-sector-first or checked coordinate mapping changed'
outputs=[]
for variant in ['frozen','default','off','on']:
 run=suite/variant;classes=run/'classes';classes.mkdir(parents=True,exist_ok=True);selected=[]
 for relative in paths:
  source=(old if variant=='frozen' else new)[relative]
  if relative.endswith('NativeLayeredTerrainChunk.java'):
   # Test-only count, no production lookup counters or timing overhead.
   source=source.replace('\tprivate final int tileWireBytes;', '\tprivate final int tileWireBytes;\n\tprivate static int fixtureCoversCalls;')
   source=source.replace('\tpublic boolean covers(int worldX, int worldY) {','\tpublic boolean covers(int worldX, int worldY) {\n\t\tfixtureCoversCalls++;')
  target=run/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source);selected.append(str(target))
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes)]+selected+[str(root/'tests/myworld/BrowserResidentInputsFixture.java'),str(root/'tests/myworld/BrowserHaloHeightFixture.java')],check=True)
 output=run/'heights.bin';flags=[] if variant=='default' else ['-Dspoiledmilk.browserHaloHeightLookup='+str(variant=='on').lower()]
 result=subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true']+flags+['-cp',str(classes)+':'+str(jar),'orsc.BrowserHaloHeightFixture',str(output)],check=True,text=True,capture_output=True)
 (run/'result.txt').write_text(result.stdout+result.stderr);print(variant,result.stdout.strip());outputs.append(output.read_bytes())
assert all(data==outputs[0] for data in outputs), 'Frozen/default/OFF/ON exact height/sentinel/exception bytes differ'
print(f'PASS frozen {frozen[:9]} vs default/OFF/ON exact {len(outputs[0])}-byte raw/World height corpus; no GPU/FPS claim')
