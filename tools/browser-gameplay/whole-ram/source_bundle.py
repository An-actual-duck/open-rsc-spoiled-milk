#!/usr/bin/env python3
"""Exact bounded source delivery for the private prepared-RAM prototype.

This produces source-only data. It never declares useful CPU preparation ready.
"""
import argparse,hashlib,importlib.util,json,re,struct,subprocess,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
MAGIC=0x57524131
MAX_BUNDLE=64*1024*1024
MAX_INDEX=4*1024*1024
FINGERPRINT='7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc'
SOURCE_SHA='42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b'
ASSET_PATHS=['Cache/video/'+n for n in ['Authentic_Landscape.orsc','Authentic_Sprites.orsc','Custom_Sprites.osar','library.orsc','models.orsc']]
def refuse(message):raise ValueError(message)
def unique(pairs):
 result={}
 for k,v in pairs:
  if k in result:refuse('Duplicate JSON member: '+k)
  result[k]=v
 return result

def document(path,limit=MAX_INDEX):
 if path.is_symlink()or not path.is_file()or path.stat().st_size>limit:refuse('Unsafe/oversize RAM metadata')
 return json.loads(path.read_bytes(),object_pairs_hook=unique,parse_constant=lambda _:refuse('Nonfinite JSON'))
def sha(path):
 h=hashlib.sha256()
 with path.open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def check_hash(value,label):
 if not isinstance(value,str)or not re.fullmatch('[a-f0-9]{64}',value):refuse('Invalid '+label+' SHA256')
 return value
def integer(value,minimum,maximum,label):
 if type(value)is not int or not minimum<=value<=maximum:refuse('Invalid '+label+' bounds')
 return value
def safe_path(value):
 if not isinstance(value,str)or len(value)>512 or not re.fullmatch(r'[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*',value)or any(p in ['.','..']for p in value.split('/')):refuse('Unsafe source path')
 return value

def payload_roster(source):
 if not isinstance(source,dict)or source.get('storage')!={'sectorSize':48,'presentationChunkSize':24}:refuse('Source storage contract')
 terrain=source.get('terrainSectors');placements=source.get('placementSets')
 if not isinstance(terrain,list)or len(terrain)!=1787 or not isinstance(placements,list)or len(placements)!=9:refuse('Full selected source roster required')
 rows={'manifest.json':{'kind':'manifest'}}
 for kind,items in [('terrain',terrain),('placements',placements)]:
  for item in items:
   path=safe_path(item['path'])
   if path in rows:refuse('Duplicate source payload path')
   level=integer(item['level'],-2147483648,2147483647,'signed level');world=item['worldSpace']
   if not isinstance(world,str)or not re.fullmatch('[A-Za-z0-9_-]+',world):refuse('Invalid source world space')
   row={'kind':kind,'worldSpace':world,'level':level,'sha256':check_hash(item['sha256'],'source payload'),'encoding':item['encoding']}
   if kind=='terrain':
    row.update(sectorX=integer(item['sectorX'],-2147483648,2147483647,'sector X'),sectorY=integer(item['sectorY'],-2147483648,2147483647,'sector Y'))
    if row['encoding']not in ['raw-layered-sector-v1','raw-layered-sector-v2-u16']:refuse('Unsupported selected raw encoding')
   rows[path]=row
 return rows

def fingerprint(rows):
 h=hashlib.sha256()
 for r in rows:
  for value in [r['path'],r['bytes'],r['sha256']]:h.update(str(value).encode()+b'\0')
 return h.hexdigest()

def validate_source(directory,expected=None,verify_payloads=True):
 directory=Path(directory);m=document(directory/'manifest.json')
 if m.get('schemaVersion')!=1 or m.get('type')!='spoiled-milk-whole-overworld-ram' or m.get('packageFingerprintSha256')!=FINGERPRINT or m.get('sourceManifestSha256')!=SOURCE_SHA:refuse('RAM source identity')
 for key in ['clientJarSha256','gpuCatalogSha256']:check_hash(m.get(key),key)
 assets=m.get('externalAssets');
 if not isinstance(assets,list)or [v.get('path')for v in assets]!=ASSET_PATHS:refuse('RAM external asset roster')
 for asset in assets:integer(asset.get('bytes'),1,16*1024*1024,'external asset bytes');check_hash(asset.get('sha256'),'external asset')
 scope=m.get('scope',{});expected_scope={'rawPackageFiles':1797,'rawTerrainSectors':1787,'rawPlacementSets':9,'preparedWorldSpace':'global','preparedLevel':0,'preparedRecipes':445,'preparedQueryLevels':[0,1,2],'preparedQueryCells':1330}
 if not isinstance(scope,dict)or any(scope.get(k)!=v for k,v in expected_scope.items()):refuse('RAM source/prepared scope contract')
 if not isinstance(m.get('settings'),list)or len(m['settings'])!=4:refuse('RAM packing settings')
 for x in m['settings']:integer(x,-2147483648,2147483647,'packing word')
 if expected:
  for key,value in expected.items():
   if m.get(key)!=value:refuse('RAM binding mismatch: '+key)
 b=m.get('sourceBundle');files=m.get('files')
 if not isinstance(b,dict)or b.get('path')!='source.bin'or b.get('headerBytes')!=16 or b.get('fileCount')!=1797 or b.get('rawBytes')!=46101718 or b.get('bytes')!=46101734:refuse('Complete RAM source bundle declaration')
 check_hash(b.get('sha256'),'bundle');bundle=directory/'source.bin'
 if bundle.is_symlink()or not bundle.is_file()or bundle.stat().st_size!=b['bytes']or b['bytes']>MAX_BUNDLE:refuse('RAM source file bounds')
 if not isinstance(files,list)or len(files)!=1797:refuse('RAM source file roster')
 offset=16;previous=None
 for r in files:
  path=safe_path(r.get('path'));integer(r.get('offset'),16,MAX_BUNDLE,'source offset');integer(r.get('bytes'),1,MAX_BUNDLE,'source size');check_hash(r.get('sha256'),'payload')
  if previous is not None and path<=previous:refuse('RAM source paths must be unique canonical order')
  if r['offset']!=offset or offset+r['bytes']>b['bytes']:refuse('RAM source interval gap/overlap/outside bundle')
  offset+=r['bytes'];previous=path
 if offset!=b['bytes']or fingerprint(files)!=FINGERPRINT:refuse('RAM full package fingerprint/length')
 source=None;whole_hash=hashlib.sha256()
 with bundle.open('rb')as stream:
  header=stream.read(16);whole_hash.update(header)
  if len(header)!=16 or struct.unpack('<4I',header)!=(MAGIC,1,1797,46101718):refuse('RAM WRA1 header')
  for r in files:
   remaining=r['bytes'];h=hashlib.sha256();raw=bytearray()if r['path']=='manifest.json'else None
   while remaining:
    data=stream.read(min(remaining,1048576))
    if not data:refuse('Truncated RAM source payload')
    remaining-=len(data);h.update(data);whole_hash.update(data)
    if raw is not None:
     if len(raw)+len(data)>MAX_INDEX:refuse('Source manifest size')
     raw.extend(data)
   if verify_payloads and h.hexdigest()!=r['sha256']:refuse('RAM payload hash mismatch: '+r['path'])
   if raw is not None:
    if h.hexdigest()!=SOURCE_SHA:refuse('RAM original source manifest hash')
    source=json.loads(raw,object_pairs_hook=unique,parse_constant=lambda _:refuse('Nonfinite source JSON'))
  if stream.read(1):refuse('RAM source trailing bytes')
 if verify_payloads and whole_hash.hexdigest()!=b['sha256']:refuse('RAM source bundle hash')
 roster=payload_roster(source)
 if set(roster)!={r['path']for r in files}:refuse('RAM/source manifest membership')
 for r in files:
  reference=roster[r['path']]
  for key,value in reference.items():
   if r.get(key)!=value:refuse('RAM source typed metadata mismatch: '+r['path']+':'+key)
  if r['kind']=='terrain'and r['bytes']!=2304*(11 if r['encoding']=='raw-layered-sector-v2-u16'else 10):refuse('RAM terrain raw wire length')
 return m

def validate_ram(directory,expected=None,verify_payloads=True):
 m=validate_source(directory,expected,verify_payloads);p=m.get('prepared')
 if m.get('complete')is not True or not isinstance(p,dict)or p.get('schemaVersion')!=1 or p.get('path')!='prepared.bin'or p.get('recipes')!=445 or p.get('queryCells')!=1330 or p.get('levels')!=[0,1,2]:refuse('Complete useful CPU preparation is required; raw source alone is not RAM ready')
 integer(p.get('bytes'),1,1536*1024*1024,'prepared bytes');check_hash(p.get('sha256'),'prepared data');integer(p.get('pageCount'),1,2147483647,'prepared page count');integer(p.get('modelTemplateCount'),0,2147483647,'model template count');integer(p.get('retainedPrimitiveBytes'),1,9007199254740991,'retained primitive bytes')
 file=Path(directory)/'prepared.bin'
 if file.is_symlink()or not file.is_file()or file.stat().st_size!=p['bytes']:refuse('Prepared CPU file bounds')
 if verify_payloads and sha(file)!=p['sha256']:refuse('Prepared CPU file hash')
 return m

def build_source(root,output,client_jar_sha,gpu_catalog_sha,settings,external_assets):
 root,output=Path(root).resolve(),Path(output)
 check_hash(client_jar_sha,'client JAR');check_hash(gpu_catalog_sha,'GPU catalogue')
 if not isinstance(settings,list)or len(settings)!=4:refuse('Four packing settings required')
 for x in settings:integer(x,-2147483648,2147483647,'setting')
 if [v.get('path')for v in external_assets]!=ASSET_PATHS:refuse('External asset binding roster')
 for asset in external_assets:
  file=root/'Client_Base'/asset['path']
  if file.stat().st_size!=asset['bytes']or sha(file)!=asset['sha256']:refuse('External source asset mismatch')
 spec=importlib.util.spec_from_file_location('ram_selected',root/'scripts/installed-map-selection.py');selected=importlib.util.module_from_spec(spec);spec.loader.exec_module(selected);selection=selected.select(root)
 if selection['packageFingerprintSha256']!=FINGERPRINT or selection['manifestSha256']!=SOURCE_SHA:refuse('Use exact full selected paired source')
 package=Path(selection['serverPackage']);inventory,actual=selected.inventory(package);source=document(package/'manifest.json');typed=payload_roster(source)
 if actual!=FINGERPRINT or len(inventory)!=1797 or sum(size for _,size,_ in inventory)!=46101718:refuse('Unexpected full source inventory')
 if output.exists():refuse('Preserve previous source bundles; choose a new output directory')
 output.mkdir(parents=True);files=[];offset=16
 with(output/'source.bin').open('xb')as bundle:
  bundle.write(struct.pack('<4I',MAGIC,1,1797,46101718))
  for path,size,expected_sha in inventory:
   metadata=typed.get(path)
   if metadata is None:refuse('Unclassified package input')
   h=hashlib.sha256();copied=0
   with(package/path).open('rb')as stream:
    for data in iter(lambda:stream.read(1048576),b''):bundle.write(data);h.update(data);copied+=len(data)
   if copied!=size or h.hexdigest()!=expected_sha:refuse('Source changed while bundling')
   files.append(dict(metadata,path=path,offset=offset,bytes=size,sha256=expected_sha));offset+=size
 m={'schemaVersion':1,'type':'spoiled-milk-whole-overworld-ram','complete':False,'packageFingerprintSha256':FINGERPRINT,'sourceManifestSha256':SOURCE_SHA,'clientJarSha256':client_jar_sha,'gpuCatalogSha256':gpu_catalog_sha,'settings':settings,'externalAssets':external_assets,'sourceBundle':{'path':'source.bin','bytes':offset,'sha256':sha(output/'source.bin'),'headerBytes':16,'fileCount':1797,'rawBytes':46101718},'files':files,'scope':{'rawPackageFiles':1797,'rawTerrainSectors':1787,'rawPlacementSets':9,'preparedWorldSpace':'global','preparedLevel':0,'preparedRecipes':445,'preparedQueryLevels':[0,1,2],'preparedQueryCells':1330},'prepared':None,'sourceOnlyNotice':'Exact source delivery only; useful CPU preparation and atomic runtime readiness are not established.'}
 data=(json.dumps(m,indent=2,sort_keys=True)+'\n').encode()
 if len(data)>MAX_INDEX:refuse('RAM source index exceeds4MiB')
 (output/'manifest.json').write_bytes(data);validate_source(output);return m

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--gpu-catalog',type=Path,required=True);p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args();gpu=document(a.gpu_catalog,32*1024*1024);jar_sha=sha(a.client_jar)
 if not gpu.get('complete')or gpu.get('clientJarSha256')!=jar_sha:refuse('Use matching complete GPU catalogue/client artifact; no silent rebinding')
 m=build_source(a.source_root,a.output,jar_sha,sha(a.gpu_catalog),gpu['settings'],gpu['externalAssets']);print(json.dumps({'sourceOnly':True,'complete':False,'output':str(a.output),'files':len(m['files']),'bundleBytes':m['sourceBundle']['bytes'],'manifestSha256':sha(a.output/'manifest.json')}))
if __name__=='__main__':main()
