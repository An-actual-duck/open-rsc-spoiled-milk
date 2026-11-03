#!/usr/bin/env python3
"""Native Aseprite reproduction, packaging, actual client loading, protected originals."""
from pathlib import Path
import hashlib,json,os,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'dev/myworld/assets/authoring/items/coating-reagents'
manifest=json.loads((BASE/'manifest.json').read_text())
for name,digest in manifest['files'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
for name,digest in manifest['unchanged'].items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
with tempfile.TemporaryDirectory(prefix='practice-item-art-')as folder:
 out=Path(folder);(out/'png').mkdir();(out/'aseprite').mkdir()
 subprocess.run([str(Path.home()/'.local/bin/aseprite'),'-b','--script-param','root='+str(ROOT),'--script-param','out='+str(out),'--script',str(BASE/'author.lua')],check=True,stdout=subprocess.DEVNULL)
 for name in ['viscous-coating','sticky-saliva-gland']:
  expected=ROOT/'dev/myworld/assets/runtime/sprites/items/inventory-ground'/(name+'.png')
  image=Image.open(expected).convert('RGBA');assert image.size==(48,32)
  assert image.tobytes()==Image.open(out/'png'/(name+'.png')).convert('RGBA').tobytes(),'native author replay '+name
  roundtrip=out/(name+'.png')
  subprocess.run([str(Path.home()/'.local/bin/aseprite'),'-b',str(BASE/'art/aseprite'/(name+'.aseprite')),'--save-as',str(roundtrip)],check=True,stdout=subprocess.DEVNULL)
  assert image.tobytes()==Image.open(roundtrip).convert('RGBA').tobytes(),'editable source '+name
  with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar')as jar:
   assert jar.read('myworld-assets/sprites/items/inventory-ground/'+name+'.png')==expected.read_bytes(),'packaged '+name
 java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
 subprocess.run([str(java/'javac'),'-J-Xmx128m','-cp',str(ROOT/'Client_Base/Open_RSC_Client.jar'),'-d',str(out),str(ROOT/'tests/myworld/PracticeItemArtFixture.java')],check=True)
 for custom in ['true','false']:
  subprocess.run([str(java/'java'),'-Xmx384m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(out)+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar'),'orsc.PracticeItemArtFixture',str(ROOT),str(out),custom],cwd=out,check=True)
print('PASS two approved native icons, Aseprite replay/roundtrips, packaged resources, both real client modes and protected originals')
