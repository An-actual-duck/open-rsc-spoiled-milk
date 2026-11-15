#!/usr/bin/env python3
"""Independent manifest, archive, Aseprite roundtrip and paired real-client checks."""
import gzip,hashlib,importlib.util,json,subprocess,tempfile,zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('coatings',ROOT/'scripts/generate-battleaxe-coatings.py');gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
subprocess.run(['python3',str(ROOT/'scripts/generate-battleaxe-coatings.py'),'--check'],check=True)
manifest=json.loads((ROOT/'dev/myworld/assets/authoring/equipment/battleaxe-coatings/manifest.json').read_text());rows=manifest['variants']
assert len(rows)==15 and {r['coatedId']for r in rows}==set(range(3411,3426))
# Approved well textures were appended after the coating preservation baseline.
# Validate their source-owned payloads, not just an allow-list of anonymous IDs.
wells=json.loads((ROOT/'dev/myworld/assets/authoring/world/cleric-wells/installation.json').read_text())
subprocess.run(['python3',str(ROOT/'scripts/install-cleric-well-assets.py'),'--check'],check=True)
well_custom={'textures:'+str(row['textureIndex'])for row in wells['textures'].values()}
well_classic={str(row['nativeSprite'])for row in wells['textures'].values()}
well_classic.update(str(row['nativeSprite'])for row in wells['supplementalOriginalCustomTextures'].values())
before=subprocess.check_output(['git','show','3925067ce:Client_Base/Cache/video/Custom_Sprites.osar'],cwd=ROOT)
old={g+':'+k:hashlib.sha256(b).hexdigest()for g,e in gen.archive(gzip.decompress(before))for k,b in e}
new={g+':'+k:hashlib.sha256(b).hexdigest()for g,e in gen.archive(gzip.decompress((ROOT/'Client_Base/Cache/video/Custom_Sprites.osar').read_bytes()))for k,b in e}
subprocess.run(['python3',str(ROOT/'scripts/generate-scimitar-coating-art.py'),'--check'],check=True)
assert set(new)-set(old)=={'equipment:viscousbattleaxe','equipment:anticoagulantscimitar'}|well_custom
assert all(new[k]==v for k,v in old.items()),'unrelated serialized sprite entries changed'
authentic=subprocess.check_output(['git','show','3925067ce:Client_Base/Cache/video/Authentic_Sprites.orsc'],cwd=ROOT)
with zipfile.ZipFile(__import__('io').BytesIO(authentic))as a,zipfile.ZipFile(ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc')as b:
 assert set(b.namelist())-set(a.namelist())=={str(i)for i in range(4000,4018)}|{str(i)for i in range(4300,4318)}|well_classic
 assert authentic[:a.start_dir]==(ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc').read_bytes()[:a.start_dir], 'all original local headers/compressed bytes retained'
 for name in a.namelist():
  assert a.read(name)==b.read(name),'original classic payload changed'
  x,y=a.getinfo(name),b.getinfo(name)
  for field in ['date_time','compress_type','comment','extra','external_attr','internal_attr','create_system','create_version','extract_version','flag_bits','file_size','CRC','compress_size','header_offset']:
   assert getattr(x,field)==getattr(y,field),('original classic metadata changed',name,field)
asset=ROOT/'dev/myworld/assets/runtime/sprites';art=ROOT/'dev/myworld/assets/authoring/equipment/battleaxe-coatings/art'
entries=gen.art.archive_entries(ROOT/'Client_Base/Cache/video/Custom_Sprites.osar')
base=entries['equipment','battleaxe'];coated=entries['equipment','viscousbattleaxe']
assert base['kind']==coated['kind'] and base['layer']==coated['layer']==3
pooled=ROOT/'dev/myworld/assets/authoring/equipment/battleaxe-coatings/pooled-v2'
layouts=json.loads((pooled/'frame-layout.json').read_text())
added_total=0
for n,((original,meta),(result,got))in enumerate(zip(base['frames'],coated['frames'])):
 expected=Image.open(pooled/'held'/f'{n:02}.png').convert('RGBA')
 assert result.tobytes()==expected.tobytes(),'only reviewed pooled-rim artwork allowed'
 assert got['canvas']==meta['canvas'] and got['offset']==layouts[n]['coated']['offset'] and got['crop']==layouts[n]['coated']['crop']
 assert all(0<=old-new<=1 for old,new in zip(meta['offset'],got['offset'])),'hand-anchor crop adjustment exceeds one pixel'
 for y in range(original.height):
  for x in range(original.width):
   a,b=original.getpixel((x,y)),result.getpixel((x,y))
   if a[3]:
    assert b[3]==a[3],'original axe pixel removed'
    if a!=b:assert a[0]==a[1]==a[2] and a[0]>24,'handle or non-metal recolored'
   elif b[3]:
    added_total+=1
    assert any(0<=xx<original.width and 0<=yy<original.height and (lambda c:c[3] and c[0]==c[1]==c[2] and c[0]>24)(original.getpixel((xx,yy))) for xx,yy in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]),'detached slime pixel'
assert added_total>0,'approved pooled lip missing'
standard=Image.open(pooled/'inventory/standard.png').convert('RGBA')
reference=Image.open(pooled/'inputs/inventory/84.png').convert('RGBA')
alpha=Image.open(pooled/'approved-style-b.png').getchannel('A')
assert standard.getchannel('A').tobytes()==alpha.tobytes(),'approved B silhouette changed'
assert sum(a!=b for a,b in zip(reference.getdata(),standard.getdata()))==50,'approved B overlay changed'
assert all(a==b or a[:3]in [(18,79,72),(32,128,115),(74,174,153)] for a,b in zip(standard.getdata(),reference.getdata())),'unapproved coating color'
for row in rows:
 assert (ROOT/f'dev/myworld/assets/runtime/sprites/items/inventory-ground/viscous-battleaxe-{row["baseId"]}.png').read_bytes()==(pooled/'inventory'/('dragon.png'if row['baseId']==2752 else 'standard.png')).read_bytes()
with tempfile.TemporaryDirectory(prefix='coating-assets-')as tmp:
 tmp=Path(tmp)
 for source in sorted(art.glob('*.aseprite')):
  png=art/(source.stem+'.png')if source.stem.startswith('held-')else asset/'items/inventory-ground'/(source.stem+'.png')
  target=tmp/(source.stem+'.png');subprocess.run(['aseprite','-b',str(source),'--save-as',str(target)],check=True,stdout=subprocess.DEVNULL)
  a,b=Image.open(png).convert('RGBA'),Image.open(target).convert('RGBA');assert a.size==b.size and a.tobytes()==b.tobytes(),'editable Aseprite differs from packed PNG'
 with zipfile.ZipFile(ROOT/'Client_Base/Open_RSC_Client.jar')as jar:
  for source in sorted(art.glob('*.aseprite')):
   png=art/(source.stem+'.png')if source.stem.startswith('held-')else asset/'items/inventory-ground'/(source.stem+'.png')
   if not source.stem.startswith('held-'):assert jar.read('myworld-assets/sprites/'+str(png.relative_to(asset)))==png.read_bytes()
 subprocess.run(['javac','-cp',str(ROOT/'Client_Base/Open_RSC_Client.jar'),'-d',str(tmp),str(ROOT/'tests/myworld/BattleaxeCoatingClientFixture.java')],check=True)
 for custom in ['true','false']:
  subprocess.run(['java','-Xmx384m','-Djava.awt.headless=true','-cp',str(tmp)+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar'),'orsc.BattleaxeCoatingClientFixture',str(ROOT),custom],cwd=tmp,check=True)
print('PASS coating assets:15 inventory forms,34 Aseprite sources,18 native frames,all original archive entries identical,paired packaged client modes')
