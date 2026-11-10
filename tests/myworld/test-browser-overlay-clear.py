#!/usr/bin/env python3
"""Frozen-source actual RGB/T/tokens and live borrowed-view differential."""
from pathlib import Path
import subprocess,os,hashlib,json
root=Path(__file__).resolve().parents[2];base='9e570ad332a3e641238acb0aa646b9884b3aa23a'
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-overlay-clear','--mkdir'],text=True).strip());jar=root/'Client_Base/Open_RSC_Client.jar'
paths=['Client_Base/src/orsc/graphics/two/GraphicsController.java','Client_Base/src/orsc/graphics/two/WorldOverlayCoverage.java'];outputs={}
for label in ['old','new']:
 classes=out/label;classes.mkdir(exist_ok=True);sources=[]
 for p in paths:
  f=root/p
  if label=='old':f=out/('old-'+Path(p).name)/Path(p).name;f.parent.mkdir(exist_ok=True);f.write_bytes(subprocess.check_output(['git','show',base+':'+p],cwd=root))
  sources.append(str(f))
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*sources,str(root/'tests/myworld/BrowserOverlayClearFixture.java'),str(root/'tests/myworld/WorldOverlayCoverageFixture.java'),str(root/'tests/myworld/SceneSpriteRasterPolicyFixture.java')],check=True)
 for selected in ['true','false']:
  target=out/(label+'-'+selected+'.bin');subprocess.run(['java','-Djava.awt.headless=true','-Dspoiledmilk.browserFramePreparation='+selected,'-Dtest.currentClear='+('true'if label=='new'else'false'),'-cp',str(classes)+os.pathsep+str(jar),'orsc.graphics.two.BrowserOverlayClearFixture',str(target)],check=True);outputs[label,selected]=target.read_bytes()
for selected in ['true','false']:assert outputs['old',selected]==outputs['new',selected],f'Coverage/RGB/coherence changed: {selected}'
r={'baseline':base,'bytesCompared':sum(len(outputs['new',s])for s in ['true','false']),'sha256':{s:hashlib.sha256(outputs['new',s]).hexdigest()for s in ['true','false']},'GPU':False};(out/'result.json').write_text(json.dumps(r,indent=2)+'\n');print('PASS exact frozen coverage/RGB/tokens, live-view mutations and exceptions',r)
