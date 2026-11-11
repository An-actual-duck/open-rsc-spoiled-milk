#!/usr/bin/env python3
"""Compile actual owner/cache/compositor and compare a frozen baseline; no GPU."""
from pathlib import Path
import hashlib, json, os, subprocess

ROOT=Path(__file__).resolve().parents[2]
BASE='8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
JAVA=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-owned-actor-pixels','--mkdir'],text=True).strip())/'cpu-oracle'
out.mkdir(exist_ok=True)
reference=out/'reference-src/orsc/FrozenActorSpriteTextureBuilder.java'
reference.parent.mkdir(parents=True,exist_ok=True)
data=subprocess.check_output(['git','show',BASE+':PC_Client/src/orsc/OpenGLSpriteTextureBuilder.java'],cwd=ROOT)
reference.write_bytes(data.replace(b'OpenGLSpriteTextureBuilder',b'FrozenActorSpriteTextureBuilder'))
pc=ROOT/'PC_Client/src/orsc';base=ROOT/'Client_Base/src'
sources=[pc/n for n in ['BrowserWorldSpriteCache.java','OpenGLSpriteTextureBuilder.java','OpenGLPixelWorkspace.java','CompositeWorldSpriteTexture.java','DynamicTextureData.java','OpenGLSpriteAlpha.java']]
sources += [base/p for p in ['com/openrsc/client/model/Sprite.java','orsc/BrowserOwnedActorPixels.java','orsc/MaterialEquipmentSprites.java','orsc/graphics/two/SpriteArchive/Frame.java','orsc/graphics/two/SpriteArchive/Unpacker.java']]
sources += [reference,ROOT/'tests/myworld/BrowserOwnedActorPixelsFixture.java']
classes=out/'classes';classes.mkdir(exist_ok=True)
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
subprocess.run([str(JAVA/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
logs=[]
archive=ROOT/'output/tests/browser-gameplay-spike/repeatable-baseline-2/web/Cache/video/Custom_Sprites.osar'
runs=[('false',['policy']),('true',['policy']),('true',[])]
if archive.exists():runs.append(('true',['archive',str(archive)]))
for policy,args in runs:
    command=[str(JAVA/'java'),'-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserOwnedActorPixels='+policy,'-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserOwnedActorPixelsFixture',*args]
    result=subprocess.run(command,check=True,capture_output=True,text=True);print(result.stdout,end='');logs.append(result.stdout)
(out/'cpu.log').write_text(''.join(logs))
report={'baselineCommit':BASE,'frozenCompositorSha256':hashlib.sha256(data).hexdigest(),'sourceSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources if p.is_relative_to(ROOT) and p!=reference},'dependencyJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'actualArchiveSha256':hashlib.sha256(archive.read_bytes()).hexdigest() if archive.exists() else None,'cpuPassed':True,'gpuExecuted':False}
(out/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
