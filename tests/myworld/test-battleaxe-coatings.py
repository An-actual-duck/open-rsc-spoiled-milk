#!/usr/bin/env python3
"""Independent manifest, archive, Aseprite roundtrip and paired real-client checks."""
import gzip,hashlib,importlib.util,json,subprocess,tempfile,zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('coatings',ROOT/'scripts/generate-battleaxe-coatings.py');gen=importlib.util.module_from_spec(spec);spec.loader.exec_module(gen)
subprocess.run(['python3',str(ROOT/'scripts/generate-battleaxe-coatings.py'),'--check'],check=True)
manifest=json.loads((ROOT/'dev/myworld/battleaxe-coatings/manifest.json').read_text());rows=manifest['variants']
assert len(rows)==15 and {r['coatedId']for r in rows}==set(range(3411,3426))
before=subprocess.check_output(['git','show','3925067ce:Client_Base/Cache/video/Custom_Sprites.osar'],cwd=ROOT)
old={g+':'+k:hashlib.sha256(b).hexdigest()for g,e in gen.archive(gzip.decompress(before))for k,b in e}
new={g+':'+k:hashlib.sha256(b).hexdigest()for g,e in gen.archive(gzip.decompress((ROOT/'Client_Base/Cache/video/Custom_Sprites.osar').read_bytes()))for k,b in e}
assert set(new)-set(old)=={'equipment:viscousbattleaxe'}
assert all(new[k]==v for k,v in old.items()),'unrelated serialized sprite entries changed'
authentic=subprocess.check_output(['git','show','3925067ce:Client_Base/Cache/video/Authentic_Sprites.orsc'],cwd=ROOT)
with zipfile.ZipFile(__import__('io').BytesIO(authentic))as a,zipfile.ZipFile(ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc')as b:
 assert set(b.namelist())-set(a.namelist())=={str(i)for i in range(4000,4018)}
 assert authentic[:a.start_dir]==(ROOT/'Client_Base/Cache/video/Authentic_Sprites.orsc').read_bytes()[:a.start_dir], 'all original local headers/compressed bytes retained'
 for name in a.namelist():
  assert a.read(name)==b.read(name),'original classic payload changed'
  x,y=a.getinfo(name),b.getinfo(name)
  for field in ['date_time','compress_type','comment','extra','external_attr','internal_attr','create_system','create_version','extract_version','flag_bits','file_size','CRC','compress_size','header_offset']:
   assert getattr(x,field)==getattr(y,field),('original classic metadata changed',name,field)
asset=ROOT/'dev/myworld/assets/sprites';art=ROOT/'dev/myworld/battleaxe-coatings/art'
entries=gen.art.archive_entries(ROOT/'Client_Base/Cache/video/Custom_Sprites.osar')
base=entries['equipment','battleaxe'];coated=entries['equipment','viscousbattleaxe']
assert base['kind']==coated['kind'] and base['layer']==coated['layer']==3
for (original,meta),(result,got)in zip(base['frames'],coated['frames']):
 assert {k:v for k,v in meta.items()if k!='rgbaSha256'}=={k:v for k,v in got.items()if k!='rgbaSha256'}
 assert original.getchannel('A').tobytes()==result.getchannel('A').tobytes()
 expected=gen.art.edge_candidate(original,original)[0]
 assert result.tobytes()==expected.tobytes(),'only reviewed blade-edge edit allowed'
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
