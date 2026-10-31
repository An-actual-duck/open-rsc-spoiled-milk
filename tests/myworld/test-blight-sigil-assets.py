#!/usr/bin/env python3
"""Approved Thorn pixels, editable roundtrips, stable archives/old stock, and shipped resource bytes."""
from pathlib import Path
import hashlib,json,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'dev/myworld/blight-sigil-production'
ASSETS=ROOT/'dev/myworld/assets/sprites/items/inventory-ground/resources/sigils'
manifest=json.loads((BASE/'manifest.json').read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for name,digest in manifest['files'].items():assert sha(ROOT/name)==digest,name
for name,digest in manifest['unchangedArchiveHashes'].items():assert sha(ROOT/name)==digest,name
for name,digest in manifest['originalSigilPngHashes'].items():assert sha(ASSETS/name)==digest,name
rows={row['id']:row for row in json.loads((ROOT/'server/conf/server/defs/ItemDefsCustom.json').read_text())['items']}
for row in manifest['items']:assert rows[row['id']]==row,row['id']
with tempfile.TemporaryDirectory(prefix='blight-sigil-export-') as directory:
 for grade in ['stone','silver']:
  for state in ['uninfused','infused']:
   name=f'blight-{grade}-{state}';png=ASSETS/(name+'.png');image=Image.open(png).convert('RGBA');assert image.size==(28,25)
   out=Path(directory)/(name+'.png')
   subprocess.run(['aseprite','-b',str(BASE/'aseprite'/(name+'.aseprite')),'--save-as',str(out)],check=True,stdout=subprocess.DEVNULL)
   assert list(Image.open(out).convert('RGBA').getdata())==list(image.getdata()),name
 for state,candidate in [('uninfused','carved'),('infused','infused')]:
  stone=Image.open(ASSETS/f'blight-stone-{state}.png').convert('RGBA')
  approved=Image.open(ROOT/f'dev/myworld/blight-sigil-candidate/blight-thorn-{candidate}-28x25.png').convert('RGBA')
  assert list(stone.getdata())==list(approved.getdata()),'approved stone artwork changed'
  silver=Image.open(ASSETS/f'blight-silver-{state}.png').convert('RGBA')
  palette=dict(zip([(65,74,86),(91,87,98),(95,102,112),(111,120,131),(129,134,145),(143,152,163),(159,168,179),(181,174,195)],[55,75,95,115,190,215,235,255]))
  glyph=0
  for a,b in zip(stone.getdata(),silver.getdata()):
   expected=(palette[a[:3]],)*3+(255,) if a[3]==255 and a[:3] in palette else a
   assert b==expected,'silver changed approved symbol/alpha'
   glyph+=int(a[3]>0 and a[:3] not in palette)
  assert glyph==44,(state,glyph)
with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar') as jar:
 for grade in ['stone','silver']:
  for state in ['uninfused','infused']:
   name=f'blight-{grade}-{state}.png'
   assert jar.read('myworld-assets/sprites/items/inventory-ground/resources/sigils/'+name)==(ASSETS/name).read_bytes()
print('PASS: four approved Blight assets/ASE roundtrips/44 glyph pixels, server rows, packaged bytes, 17 old PNGs and all three native archives unchanged')
