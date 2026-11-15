#!/usr/bin/env python3
"""Approved native style, all inventory masks, archive preservation and actual renderer paths."""
from pathlib import Path
import subprocess,json,time,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'output/tests/scimitar-coating-art'/str(time.time_ns());BASE.mkdir(parents=True);classes=BASE/'classes';classes.mkdir()
def run(cmd,name):
 p=subprocess.run([str(x)for x in cmd],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(BASE/(name+'.log')).write_text(p.stdout)
 if p.returncode:print('\n'.join(p.stdout.splitlines()[-25:]));raise SystemExit(p.returncode)
 for line in p.stdout.splitlines():
  if 'PASS 'in line:print(line)
run(['python3',ROOT/'scripts/generate-scimitar-coating-art.py','--check'],'pack-check')
source=ROOT/'dev/myworld/assets/authoring/equipment/scimitar-coatings';runtime=ROOT/'dev/myworld/assets/runtime/sprites/items/inventory-ground'
manifest=json.loads((ROOT/'config/scimitar-coatings.json').read_text())
reference=Image.open(source/'inputs/inventory/398.png').convert('RGBA');approved=Image.open(source/'approved-style-b.png').convert('RGBA');mask=[a!=b for a,b in zip(reference.getdata(),approved.getdata())]
with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar')as jar:
 for row in manifest['variants']:
  raw=Image.open(source/'inputs/inventory'/f'{row["base"]}.png').convert('RGBA');file=runtime/f'anticoagulant-scimitar-{row["base"]}.png';im=Image.open(file).convert('RGBA')
  assert raw.size==im.size==(48,32) and raw.getchannel('A').tobytes()==im.getchannel('A').tobytes()
  for i,(a,b)in enumerate(zip(raw.getdata(),im.getdata())):assert (a!=b)==mask[i]
  assert jar.read('myworld-assets/sprites/items/inventory-ground/'+file.name)==file.read_bytes()
 assert Image.open(runtime/'anticoagulant-scimitar-398.png').tobytes()==approved.tobytes()
client=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-cp',client,'-d',classes,ROOT/'tests/myworld/java/ScimitarCoatingArtFixture.java'],'compile-client')
for mode in ['true','false']:run(['java','-Xmx512m','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'orsc.ScimitarCoatingArtFixture',ROOT,mode],'client-'+mode)
print('PASS approved B icon exact, all fourteen blade-only overlays and packaged PNGs; evidence:',BASE)
