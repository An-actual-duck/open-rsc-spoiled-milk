#!/usr/bin/env python3
"""Native asset provenance and actual packaged client decoding. Server behavior: test_gem_dragons."""
from pathlib import Path
import hashlib,json,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
ASSET=ROOT/'dev/myworld/assets/sprites/npcs/gem-dragons'
manifest=json.loads((ASSET/'provenance.json').read_text())
for name,digest in manifest['sha256'].items():
 assert hashlib.sha256((ASSET/name).read_bytes()).hexdigest()==digest
for frame in manifest['frameSources']:
 assert hashlib.sha256((ASSET/'base-frames'/Path(frame['file']).name).read_bytes()).hexdigest()==frame['sha256']
for tier,color in [('sapphire',(0,76,255)),('emerald',(51,204,51)),('ruby',(255,51,0)),('diamond',(255,255,255)),('dragonstone',(187,0,255))]:
 im=Image.open(ASSET/(tier+'.png')).convert('RGBA');assert im.size==(1465,489)
 for slot in range(18):
  base=Image.open(ASSET/'base-frames'/f'{slot:02d}.png').convert('RGBA');column,row=divmod(slot,3);x0=column*226
  got=im.crop((x0,row*163,x0+base.width,(row+1)*163))
  for before,after in zip(base.get_flattened_data(),got.get_flattened_data()):
   r,g,b,a=before
   if a and max(r,g,b)-min(r,g,b)<=16 and r>20:
    v=(r+g+b)/3/255;expected=tuple(int(c*v+.5)for c in color)+(a,)
    assert after==expected,(tier,slot,before,after)
   else:assert before==after,(tier,slot,'fixed body/alpha changed')
with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar')as jar:
 for name in manifest['sha256']:assert jar.read('myworld-assets/sprites/npcs/gem-dragons/'+name)==(ASSET/name).read_bytes()
 for name in ['gem-cutter-gloves','gem-cutters-monocle']:
  p=ROOT/'dev/myworld/assets/sprites/items/inventory-ground'/f'{name}.png';assert Image.open(p).size==(48,32)
  assert jar.read('myworld-assets/sprites/items/inventory-ground/'+name+'.png')==p.read_bytes()
npcs=json.loads((ROOT/'server/conf/server/defs/GemDragonNpcDefs.json').read_text())['npcs']
expected=';'.join(','.join(str(n[k])for k in ['attack','strength','hits','defense'])for n in npcs)
with tempfile.TemporaryDirectory(prefix='gem-dragon-client-')as scratch:
 subprocess.run(['javac','-cp',str(ROOT/'Client_Base/Open_RSC_Client.jar'),'-d',scratch,str(ROOT/'tests/myworld/GemDragonClientFixture.java')],check=True)
 for cwd in [ROOT,Path(scratch)]:
  subprocess.run(['java','-Xmx384m','-Djava.awt.headless=true','-cp',scratch+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar'),'orsc.GemDragonClientFixture',str(cwd),expected],cwd=cwd,check=True)
print('PASS exact approved frame provenance, fixed body palette,5gemrecolors, packaged assets and real client decoding')
