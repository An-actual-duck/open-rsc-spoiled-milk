#!/usr/bin/env python3
"""Approved held exports, fixed bolt geometry and real client sprite selection."""
from pathlib import Path
from PIL import Image
import json,hashlib,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/god-crossbow-visuals'/str(time.time_ns());BASE.mkdir(parents=True)
def run(argv,name):
 result=subprocess.run([str(x)for x in argv],cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 (BASE/(name+'.log')).write_text(result.stdout)
 if result.returncode:print(result.stdout[-5000:]);raise SystemExit(result.returncode)
 for line in result.stdout.splitlines():
  if 'PASS 'in line:print(line)
run(['python3','scripts/build-god-crossbow-visuals.py','--check'],'archive-check')
author=ROOT/'dev/myworld/assets/authoring/equipment/god-crossbows'
source=Image.open(author/'shots-v3/inputs/bolt-basic.png').convert('RGBA');checks=0
for god in ['saradomin','zamorak','guthix']:
 sheet=Image.open(author/f'shots-v3/{god}/bolt.png').convert('RGBA');assert sheet.size==(192,32)
 first=sheet.crop((10,4,29,20));assert first.getchannel('A').tobytes()==source.getchannel('A').tobytes()
 for f in range(6):
  frame=sheet.crop((f*32,0,f*32+32,32));core=frame.crop((10,4,29,20))
  assert core.tobytes()==first.tobytes(),'Bolt itself animates or trail overlaps it';checks+=1
  trail=[frame.getpixel((x,y))[3]for y in range(20,32)for x in range(32)]
  assert 1<=sum(a>0 for a in trail)<=20,'Trail is missing or excessive';checks+=1
run(['javac','-source','8','-target','8','-cp','Client_Base/Open_RSC_Client.jar','-d',BASE,'tests/myworld/java/GodCrossbowVisualFixture.java'],'compile')
for mode in ['true','false']:
 run(['java','-Djava.awt.headless=true','-cp',str(BASE)+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar'),'orsc.GodCrossbowVisualFixture',ROOT,mode],'client-'+mode)
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'staticBoltChecks':checks,'clientSha256':hashlib.sha256((ROOT/'Client_Base/Open_RSC_Client.jar').read_bytes()).hexdigest(),'publicServerTouched':False},indent=2)+'\n')
print('PASS',checks,'archived-v3 fixed-bolt geometry checks; selected art also verified; evidence:',BASE)
