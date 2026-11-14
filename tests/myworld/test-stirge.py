#!/usr/bin/env python3
"""Isolated Stirge mechanics and staged Slayer roster; never touches live data."""
from pathlib import Path
import subprocess,time,json,hashlib
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/stirge-encounter'/str(time.time_ns());BASE.mkdir(parents=True)
classes=BASE/'classes';classes.mkdir()
def run(argv,name,cwd=ROOT):
 r=subprocess.run([str(x)for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (BASE/(name+'.log')).write_text(r.stdout)
 if r.returncode:print('\n'.join(r.stdout.splitlines()[-35:]));raise SystemExit(r.returncode)
 for line in r.stdout.splitlines():
  if 'PASS 'in line:print(line)
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],'prepare')
cp=':'.join(str(ROOT/p)for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/StirgeCombatFixture.java'],'compile')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.StirgeCombatFixture'],'runtime',BASE/'runtime')

# Verify all approved native art pixels survive atlas packing without scaling.
from PIL import Image
from zipfile import ZipFile
atlas=Image.open(ROOT/'dev/myworld/assets/runtime/sprites/npcs/slayer-monsters/stirge.png').convert('RGBA')
for n in range(18):
 source=Image.open(ROOT/f'dev/myworld/assets/authoring/monsters/stirge/plunge-attack/frame-{n:02}.png').convert('RGBA')
 x=(n//3)*210+(30 if n<15 else 0);y=(n%3)*160+(15 if n<15 else 0)
 assert atlas.crop((x,y,x+source.width,y+source.height)).tobytes()==source.tobytes()
client=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/StirgeClientFixture.java'],'compile-client')
for mode in ['true','false']:
 for path,label in [(ROOT,'disk'),(BASE,'jar')]:
  run(['java','-Xmx384m','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'orsc.StirgeClientFixture',path,mode],f'client-{mode}-{label}',BASE)
with ZipFile(client)as jar:
 for asset in ['sprites/npcs/slayer-monsters/stirge.png','sprites/items/inventory-ground/bug-off.png','sprites/items/inventory-ground/sanguinary-gland.png']:
  assert jar.read('myworld-assets/'+asset)==(ROOT/'dev/myworld/assets/runtime'/asset).read_bytes()
print('PASS all eighteen approved native poses and packaged art pixels preserved')
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/SlayerTaskSupplyFixture.java'],'compile-task-supplies')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.SlayerTaskSupplyFixture',BASE/'task-supply-results'],'task-supplies',BASE/'runtime')

(BASE/'receipt.json').write_text(json.dumps({'passed':True,'publicServerTouched':False},indent=2)+'\n')
print('Evidence:',BASE)
