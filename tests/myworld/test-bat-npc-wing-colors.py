#!/usr/bin/env python3
"""Recolor scope and real client installation, without gameplay/network startup."""
from pathlib import Path
import hashlib,json,subprocess,time,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/bat-npc-wing-colors'/str(time.time_ns());BASE.mkdir(parents=True);classes=BASE/'classes';classes.mkdir()
def run(argv,name,cwd=ROOT):
 r=subprocess.run([str(x)for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 (BASE/(name+'.log')).write_text(r.stdout)
 if r.returncode:print('\n'.join(r.stdout.splitlines()[-35:]));raise SystemExit(r.returncode)
 for line in r.stdout.splitlines():
  if 'PASS' in line:print(line)
base=ROOT/'dev/myworld/assets/authoring/monsters/stirge/plunge-attack'
run(['aseprite','-b','--script-param','base='+str(base),'--script',base/'validate.lua'],'stirge-master')
for folder,frame in [(base,'frame-00.png'),(ROOT/'dev/myworld/assets/authoring/monsters/death-wing','frame-00.png')]:
 name='approved-front-tan.png' if folder==base else 'approved-front-dark.png'
 assert Image.open(folder/frame).convert('RGBA').tobytes()==Image.open(folder/'inputs'/name).convert('RGBA').tobytes()
# Every Death wing frame preserves its source alpha and all colors outside the wing mask.
death=ROOT/'dev/myworld/assets/authoring/monsters/death-wing';layout=json.loads((death/'frame-layout.json').read_text())
atlas=Image.open(ROOT/'dev/myworld/assets/runtime/sprites/npcs/death-wing/death-wing.png').convert('RGBA')
for row in layout:
 n=row['frame'];size=tuple(row['canvas']);raw=Image.open(death/f'inputs/bat/frame-{n:02}.png').convert('RGBA');im=Image.open(death/f'frame-{n:02}.png').convert('RGBA')
 assert im.size==size
 # Reconstruct original pixels by reading the immutable raw input at its declared offset.
 sx,sy=row['shift'];mask=im.getchannel('A')
 for y in range(size[1]):
  for x in range(size[0]):
   original=raw.getpixel((x-sx,y-sy)) if 0<=x-sx<raw.width and 0<=y-sy<raw.height else (0,0,0,0)
   pixel=im.getpixel((x,y));assert pixel[3]==original[3]
   if row['bodyGuardX'][0]<=x<=row['bodyGuardX'][1] and original[0:3] not in [(97,97,97),(124,124,124),(182,182,182),(76,76,76),(102,102,102),(127,127,127),(183,183,183)]:assert pixel==original
 assert not any(pixel[3] and pixel[0]==pixel[1]==pixel[2] and 50<=pixel[0]<230 for pixel in im.getdata()), 'Unmapped membrane gray in frame '+str(n)
 cell=atlas.crop(((n//3)*150,(n%3)*130,(n//3)*150+size[0],(n%3)*130+130));assert cell.tobytes()==im.tobytes()
client=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/BatNpcWingColorFixture.java'],'compile-client')
for custom in ['true','false']:
 for path,label in [(ROOT,'disk'),(BASE,'jar')]:
  run(['java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'orsc.BatNpcWingColorFixture',path,custom,ROOT],f'client-{custom}-{label}',BASE)
with zipfile.ZipFile(client)as jar:
 for asset in ['sprites/npcs/death-wing/death-wing.png','sprites/npcs/slayer-monsters/stirge.png']:
  assert jar.read('myworld-assets/'+asset)==(ROOT/'dev/myworld/assets/runtime'/asset).read_bytes()
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'publicServerTouched':False,'posesPerNpc':18,'currentFrontMatchesApproved':True},indent=2)+'\n')
print('PASS all thirty-six poses, approved front palettes, body/alpha preservation and packaged NPC atlases; evidence:',BASE)
