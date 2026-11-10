#!/usr/bin/env python3
"""Frozen206 independent Chunk/Snapshot/World parity and source-only path counts."""
from pathlib import Path
from datetime import datetime,timezone
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
FROZEN='2067399022fe0fb2f336437f30b1621ffa99a80c'
category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-primitive-chunk-coverage','--mkdir'],text=True).strip())
run=category/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
paths=['Client_Base/src/orsc/NativeLayeredTerrainChunk.java','Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java','Client_Base/src/orsc/graphics/three/World.java']
old={p:subprocess.check_output(['git','show',FROZEN+':'+p],cwd=ROOT,text=True)for p in paths}
new={p:(ROOT/p).read_text()for p in paths}
assert old[paths[1]]==new[paths[1]] and old[paths[2]]==new[paths[2]],'Snapshot/World changed beyond focused chunk candidate'
def method(source,marker):
 start=source.index(marker);end=source.index('{',start)+1;depth=1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
old_covers=method(old[paths[0]],'\tpublic boolean covers(')
new_covers=method(new[paths[0]],'\tpublic boolean covers(')
assert old_covers[old_covers.index('\n\t\tlong minimumX'):]==new_covers[new_covers.index('\n\t\tlong minimumX'):],'Long fallback changed'
constructor=method(old[paths[0]],'\tprivate NativeLayeredTerrainChunk(')
current_constructor=method(new[paths[0]],'\tprivate NativeLayeredTerrainChunk(')
assert current_constructor[:current_constructor.index('\t\t// Append only after')].rstrip()==constructor[:constructor.rfind('\n\t}')].rstrip(),'Constructor validation/copy order changed'
for marker in ['\tpublic Tile createTile(','\tint groundElevation(','\tint groundOverlay(','\tprivate int tileOffset(','\tprivate static void requireSafeChunkCoordinate(']:
 assert method(old[paths[0]],marker)==method(new[paths[0]],marker),'Original authority changed: '+marker
outputs={};results={}
fixtures=[ROOT/'tests/myworld/BrowserResidentInputsFixture.java',ROOT/'tests/myworld/BrowserPrimitiveChunkCoverageFixture.java']
for variant in ['frozen','default','off','on']:
 directory=run/variant;classes=directory/'classes';classes.mkdir(parents=True);selected=[]
 for relative in paths:
  source=(old if variant=='frozen'else new)[relative]
  if relative.endswith('NativeLayeredTerrainChunk.java'):
   source=source.replace('\tprivate final int tileWireBytes;','\tprivate final int tileWireBytes;\n private static long fixtureCoversCalls,fixtureLongMultiplications,fixtureIntegerHits;')
   source=source.replace('\tpublic boolean covers(int worldX, int worldY) {','\tpublic boolean covers(int worldX, int worldY) {\n fixtureCoversCalls++;')
   source=source.replace('\t\tlong minimumX = (long) chunkX * size;','\t\tfixtureLongMultiplications += 2;\n\t\tlong minimumX = (long) chunkX * size;')
   if variant!='frozen':source=source.replace('&& orsc.graphics.three.Renderer3DSettings.isBrowserPresentationAvailable()) {','&& orsc.graphics.three.Renderer3DSettings.isBrowserPresentationAvailable()) {\n fixtureIntegerHits++;')
  target=directory/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(source);selected.append(target)
 with(directory/'compile.log').open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,selected+fixtures)],check=True,stdout=log,stderr=subprocess.STDOUT)
 flags=[]if variant=='default'else['-Dspoiledmilk.browserPrimitiveChunkCoverage='+str(variant=='on').lower()]
 output=directory/'corpus.bin'
 with(directory/'run.log').open('w')as log:subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true',*flags,'-cp',str(classes)+':'+str(jar),'orsc.BrowserPrimitiveChunkCoverageFixture',str(output)],check=True,stdout=log,stderr=subprocess.STDOUT)
 outputs[variant]=output.read_bytes();results[variant]=(directory/'run.log').read_text().strip();print(variant,results[variant])
assert all(data==outputs['frozen']for data in outputs.values()),'Frozen/default/OFF/ON coverage/height/tile/error bytes differ'
receipt={'passed':True,'frozen':FROZEN,'fixtureProvenance':'Snapshot/World corpus salvaged as test input from9fd3957429d43ca07e929a999461f1b540f752cf; no height/index implementation imported','bytes':len(outputs['frozen']),'corpusSha256':hashlib.sha256(outputs['frozen']).hexdigest(),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'sourceSha256':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest()for p in paths},'fixtureSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in fixtures},'results':results,'limits':'CPU exact frozen parity and instrumented path counts only; no browser/GPU/FPS claim'}
(run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'run':str(run),**receipt},indent=2))
