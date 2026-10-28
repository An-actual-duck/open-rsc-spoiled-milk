#!/usr/bin/env python3
"""Approved native data, shared neutral asset, and actual client renderer paths."""
from pathlib import Path
import hashlib,json,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
ASSET=ROOT/'dev/myworld/assets/sprites/npcs/gem-dragons'
manifest=json.loads((ASSET/'provenance.json').read_text())
assert set(manifest['sha256'])=={'gemdragon.png'}
for name,digest in manifest['sha256'].items():assert hashlib.sha256((ASSET/name).read_bytes()).hexdigest()==digest
for frame in manifest['frameSources']:
 assert hashlib.sha256((ASSET/'base-frames'/Path(frame['file']).name).read_bytes()).hexdigest()==frame['sha256']
im=Image.open(ASSET/'gemdragon.png').convert('RGBA');assert im.size==(1465,489)
changed=0
for slot in range(18):
 base=Image.open(ASSET/'base-frames'/f'{slot:02d}.png').convert('RGBA');column,row=divmod(slot,3);x0=column*226
 got=im.crop((x0,row*163,x0+base.width,(row+1)*163))
 for before,after in zip(base.get_flattened_data(),got.get_flattened_data()):
  r,g,b,a=before
  if a and max(r,g,b)-min(r,g,b)<=16 and r>20:
   v=int((r+g+b)/3+.5);assert after==(v,v,v,a)
   changed+=before!=after
  else:assert before==after,('fixed body/alpha changed',slot,before,after)
assert changed==5094,'only expected near-neutral shade normalization'
with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar')as jar:
 assert jar.read('myworld-assets/sprites/npcs/gem-dragons/gemdragon.png')==(ASSET/'gemdragon.png').read_bytes()
 for obsolete in ['sapphire','emerald','ruby','diamond','dragonstone']:
  assert 'myworld-assets/sprites/npcs/gem-dragons/'+obsolete+'.png'not in jar.namelist(),'no duplicated precolored runtime sheets'
 for name in ['gem-cutter-gloves','gem-cutters-monocle']:
  p=ROOT/'dev/myworld/assets/sprites/items/inventory-ground'/f'{name}.png';assert Image.open(p).size==(48,32)
  assert jar.read('myworld-assets/sprites/items/inventory-ground/'+name+'.png')==p.read_bytes()
npcs=json.loads((ROOT/'server/conf/server/defs/GemDragonNpcDefs.json').read_text())['npcs']
expected=';'.join(','.join(str(n[k])for k in ['attack','strength','hits','defense'])for n in npcs)
with tempfile.TemporaryDirectory(prefix='gem-dragon-client-')as scratch:
 subprocess.run(['javac','-cp',str(ROOT/'Client_Base/Open_RSC_Client.jar'),'-d',scratch,str(ROOT/'tests/myworld/GemDragonClientFixture.java')],check=True)
 for cwd in [ROOT,Path(scratch)]:
  subprocess.run(['java','-Xmx384m','-Djava.awt.headless=true','-cp',scratch+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar'),'orsc.GemDragonClientFixture',str(cwd),expected],cwd=cwd,check=True)
print('PASS original frame provenance, 5,094 neutral-only shade normalizations, one shared packaged sheet, real native alias and renderer paths')
