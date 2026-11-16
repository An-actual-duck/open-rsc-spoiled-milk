#!/usr/bin/env python3
"""Flight alignment, real packaged loaders, size regressions and prayer pictograms."""
from pathlib import Path
import subprocess,time,json,hashlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/divine-bolt-visuals'/str(time.time_ns());BASE.mkdir(parents=True)
def run(cmd,name):
 r=subprocess.run([str(x) for x in cmd],cwd=BASE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (BASE/(name+'.log')).write_text(r.stdout)
 if r.returncode:print(r.stdout[-5000:]);raise SystemExit(r.returncode)
 for line in r.stdout.splitlines():
  if 'PASS ' in line:print(line)
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-source','8','-target','8','-cp',jar,'-d',BASE,ROOT/'tests/myworld/java/DivineBoltVisualFixture.java'],'compile')
for mode in ['true','false']:run(['java','-Djava.awt.headless=true','-cp',str(BASE)+':'+str(jar),'orsc.DivineBoltVisualFixture',BASE/mode,mode],'client-'+mode)
a=ROOT/'dev/myworld/assets/authoring/equipment/god-crossbows/shots-v4'
core=Image.open(a/'basic/bolt.png').convert('RGBA');mask=[(x,y) for y in range(96) for x in range(96) if core.getpixel((x,y))[3]]
assert mask and max(y for x,y in mask)-min(y for x,y in mask)<10
for god in ['saradomin','zamorak','guthix']:
 im=Image.open(a/god/'bolt.png').convert('RGBA');first=[im.getpixel((x,y)) for x,y in mask]
 for f in range(6):
  assert [im.getpixel((96*f+x,y)) for x,y in mask]==first,'Bolt core shifts with trail phase'
  trail=[(x,y) for y in range(96) for x in range(35) if im.getpixel((96*f+x,y))[3]>=64]
  assert len(trail)>100 and max(y for x,y in trail)-min(y for x,y in trail)>=12,'Trail too small'
for name in ['divine-bolts','thorned-bolts','bolts-of-wrath']:
 im=Image.open(ROOT/f'dev/myworld/assets/runtime/ui/prayer/{name}.png').convert('RGBA');assert im.size==(512,512)
 assert all((r,g,b)==(255,255,255) for r,g,b,alpha in im.get_flattened_data() if alpha),'Icons must be white silhouettes'
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'clientSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'publicServerTouched':False},indent=2)+'\n')
print('Evidence:',BASE)
