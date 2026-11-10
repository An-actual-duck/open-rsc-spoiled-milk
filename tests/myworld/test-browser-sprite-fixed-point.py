#!/usr/bin/env python3
"""Frozen OLD production source, independent JVMs and exact command/RGB/T/composite bytes."""
from pathlib import Path
import os,subprocess,hashlib,json
ROOT=Path(__file__).resolve().parents[2];BASE='fe0528b2092159d4dbd8b3c539b6f0a860712f75'
suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-sprite-fixed-point','--mkdir'],text=True).strip())
jar=ROOT/'Client_Base/Open_RSC_Client.jar';java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
old=suite/'reference-source/orsc/graphics/two/GraphicsController.java';old.parent.mkdir(parents=True,exist_ok=True);old.write_bytes(subprocess.check_output(['git','show',BASE+':Client_Base/src/orsc/graphics/two/GraphicsController.java'],cwd=ROOT))
outputs=[]
for label,sources in [('old',[old]),('new',[])]:
    classes=suite/(label+'-'+java.parent.name);classes.mkdir(exist_ok=True);sources += [ROOT/'tests/myworld/SceneSpriteRasterPolicyFixture.java',ROOT/'tests/myworld/BrowserSpriteFixedPointFixture.java']
    subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
    out=suite/(label+'-commands.bin');outputs.append(out)
    result=subprocess.check_output([str(java/'java'),'-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.openglWorldSpritesVisible=true','-cp',str(classes)+os.pathsep+str(jar),'orsc.graphics.two.BrowserSpriteFixedPointFixture',str(out)],text=True);(suite/(label+'-run.log')).write_text(result);print(result.strip())
a,b=[p.read_bytes()for p in outputs]
if a!=b:
    at=next((i for i,(x,y)in enumerate(zip(a,b))if x!=y),min(len(a),len(b)))
    raise AssertionError(f'Frozen/current command/RGB/T/composite output differs at byte{at}, sizes{len(a)}/{len(b)}')
report={'baseline':BASE,'bytesCompared':len(a),'sha256':hashlib.sha256(a).hexdigest(),'privateCaptureCases':12000,'publicDrawCases':600,'independentJVMs':True,'BigIntegerEligibilityOracle':True,'GPU':False}
(suite/'result.json').write_text(json.dumps(report,indent=2)+'\n');print('PASS exact frozen/current outputs:',report)
