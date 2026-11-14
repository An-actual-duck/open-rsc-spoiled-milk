#!/usr/bin/env python3
"""Offline production loot delivery and disk/JAR item icons; no live state."""
from pathlib import Path
import subprocess,time,json,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/bat-wing-drops'/str(time.time_ns());BASE.mkdir(parents=True)
classes=BASE/'classes';classes.mkdir()
def run(argv,name,cwd=ROOT):
 r=subprocess.run([str(x)for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (BASE/(name+'.log')).write_text(r.stdout)
 if r.returncode:print('\n'.join(r.stdout.splitlines()[-35:]));raise SystemExit(r.returncode)
 for line in r.stdout.splitlines():
  if 'PASS 'in line:print(line)
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],'prepare')
cp=':'.join(str(ROOT/p)for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/BatWingDropsFixture.java'],'compile-server')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.BatWingDropsFixture'],'server',BASE/'runtime')
client=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/BatWingClientFixture.java'],'compile-client')
for mode in ['true','false']:
 for path,label in [(ROOT,'disk'),(BASE,'jar')]:run(['java','-Xmx384m','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'orsc.BatWingClientFixture',path,mode],f'client-{mode}-{label}',BASE)
base=ROOT/'dev/myworld/assets/runtime/sprites/items/inventory-ground'
with zipfile.ZipFile(client)as jar:
 for name in ['bat-wing','death-wing-wing','stirge-wing']:
  p=base/(name+'.png');im=Image.open(p).convert('RGBA');assert im.size==(48,32)and set(im.getchannel('A').get_flattened_data())=={0,255}
  assert jar.read('myworld-assets/sprites/items/inventory-ground/'+name+'.png')==p.read_bytes()
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'publicServerTouched':False,'baseDenominator':16,'npcItems':{'43':3467,'768':3468,'882':3469}},indent=2)+'\n')
print('PASS native wing sprite and exact packaged PNG; evidence:',BASE)
