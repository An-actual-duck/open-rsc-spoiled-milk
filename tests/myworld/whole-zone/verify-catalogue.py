#!/usr/bin/env python3
"""Independent metadata join against exact whole-zone package payloads; no GPU."""
import argparse,collections,hashlib,importlib.util,json,re
from pathlib import Path
_s=importlib.util.spec_from_file_location('zone_package',Path(__file__).with_name('verify-package.py'));package=importlib.util.module_from_spec(_s);_s.loader.exec_module(package)
EXPECTED_KINDS=['UNCLASSIFIED','TERRAIN','WALL','ROOF','GAME_OBJECT','WALL_OBJECT']
def fail(s):raise ValueError(s)
def integer(n):
 if type(n)is not int or not -2147483648<=n<=2147483647:fail('Expected signed int32 word')
 return n
def roster_key(c):return tuple(integer(c[n])for n in ('plane','cellX','cellY'))
def join(catalog,proof,root,kind_words,partial_sectors=None):
 """No production validator/key/hash formula is imported or reimplemented."""
 if catalog['packageFingerprintSha256']!=proof['packageFingerprint']or catalog['manifestSha256']!=proof['manifestSHA256']:fail('Catalogue/package identity mismatch')
 if partial_sectors is None:
  if catalog.get('complete')is not True:fail('Full catalogue must declare complete')
 elif not partial_sectors or catalog.get('complete')is not False:fail('Explicit partial diagnostic requires incomplete catalogue')
 world={};objects={}
 for s in proof['sectorRoster']:
  if s['worldSpace']!='global'or s['level']not in(0,1,2):continue
  key=(s['level'],s['sectorX'],s['sectorY'])
  if partial_sectors is not None and key not in partial_sectors:continue
  world[key]=s['sha256']
  for dx in (0,1):
   for dy in (0,1):objects[(s['level'],s['sectorX']*2+dx,s['sectorY']*2+dy)]=collections.Counter()
 manifest=package.load((root/'manifest.json').read_bytes())
 for row in manifest['placementSets']:
  if row['worldSpace']!='global'or row['level']not in(0,1,2):continue
  raw=package.safe(root,row['path']).read_bytes()
  if package.sha(raw)!=row['sha256']:fail('Source placements changed during join')
  data=package.load(raw)
  for family,definition,kind in [('scenery','sceneryId',kind_words['GAME_OBJECT']),('boundaries','boundaryId',kind_words['WALL_OBJECT'])]:
   for p in data.get(family,[]):
    x=integer(p['position']['x']);y=integer(p['position']['y']);key=(row['level'],x//24,y//24)
    if key not in objects:
     if partial_sectors is not None:continue
     fail('Authored placement outside selected source ownership '+str(key))
    objects[key][(kind,x,y,integer(p[definition]),integer(p['direction']))]+=1
 if partial_sectors is not None and set(world)!=partial_sectors:fail('Requested partial sector absent from actual source')
 actual={}
 for cell in catalog['worldCells']:
  key=roster_key(cell)
  if key in actual:fail('Duplicate catalogue terrain owner')
  actual[key]=cell['sourceSha256']
 if actual!=world:fail('Exact terrain owner/hash join mismatch')
 actual_objects={}
 for cell in catalog['objectCells']:
  key=roster_key(cell)
  if key in actual_objects:fail('Duplicate catalogue object owner')
  words=cell['identities'];count=integer(cell['placementCount'])
  if count<0 or not isinstance(words,list)or len(words)!=count*7:fail('Authored identity shape')
  rows=collections.Counter()
  for at in range(0,len(words),7):
   r=tuple(integer(v)for v in words[at:at+7]);rows[r[:5]]+=1
   if r[1]//24!=key[1]or r[2]//24!=key[2]:fail('Object identity lies outside declared owner')
  actual_objects[key]=rows
 if actual_objects!=objects:fail('Exact authored identities/owner join mismatch')
 resources={};reverse=set();gpu_bytes=0
 for c in catalog['chunks']:
  rid=integer(c['id'])
  if rid in resources:fail('Duplicate resource ID')
  resources[rid]=c
 for family,cells,role in [('world',catalog['worldCells'],0),('objects',catalog['objectCells'],1)]:
  for cell in cells:
   key=roster_key(cell);v=integer(cell['vertexCount']);i=integer(cell['indexCount']);rid=cell['resourceId']
   if v<0 or i<0 or i%3:fail('Invalid cell geometry shape')
   mandatory=(family=='world'and key[0]==0)or(family=='objects'and cell['placementCount']>0)
   if rid is None:
    if mandatory or v or i:fail('Missing mandatory geometry resource')
    continue
   c=resources.get(integer(rid))
   if c is None or rid in reverse:fail('Missing/duplicate cell resource mapping')
   reverse.add(rid)
   if roster_key(c)!=key or c['role']!=role or c['sourceKind']!=family or c['cellSize']!=(48 if role==0 else 24)or c['vertexCount']!=v or c['indexCount']!=i:fail('Geometry resource owner/count mismatch')
   if family=='objects'and(c['canonicalContentKey']!=cell['canonicalContentKey']or c['placementCount']!=cell['placementCount']):fail('Object geometry identity mismatch')
   if mandatory and(not v or not i):fail('Mandatory source has empty geometry')
   if c['bytes']!=16+v*112+i*4+c['batchIntCount']*4 or c['batchIntCount']%5:fail('Resource byte/batch mismatch')
   gpu_bytes+=v*112+i*4
 if reverse!=set(resources):fail('Unowned extra geometry resource')
 return {'passed':True,'partialDiagnostic':partial_sectors is not None,'worldCells':len(world),'objectCells':len(objects),'authoredPlacements':sum(sum(c.values())for c in objects.values()),'resources':len(resources),'declaredGeometryGPUBytes':gpu_bytes,'scope':'Independent exact source ownership/authored first-five identity words/resource join only. Elevation values, packed geometry equality, GPU allocation/use and performance require separate gates.'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--catalogue',type=Path,required=True);p.add_argument('--package',type=Path,required=True);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3]);p.add_argument('--out',type=Path,required=True);p.add_argument('--partial-sector',action='append',default=[],help='Explicit diagnostic plane,x,y owner; repeat for each owner. Never a full readiness proof.');a=p.parse_args()
 if a.out.exists():fail('Preserve prior proof')
 profile=package.load((a.root/'Client_Base/world-builder-configs/installed-client.json').read_bytes());proof=package.verify(a.package,profile['packageFingerprintSha256'],profile['manifestSha256'])
 enum=a.root/'Client_Base/src/orsc/graphics/three/Renderer3DModelKind.java';raw=enum.read_bytes();names=re.findall(r'^\s*([A-Z_]+)\("',raw.decode(),re.M)
 if names!=EXPECTED_KINDS:fail('Unaudited model kind mapping')
 if a.catalogue.stat().st_size>32*1024*1024:fail('Catalogue JSON budget')
 raw_catalogue=a.catalogue.read_bytes();partial={tuple(map(int,s.split(',')))for s in a.partial_sector}if a.partial_sector else None
 if partial is not None and any(len(k)!=3 for k in partial):fail('Partial sector needs plane,x,y')
 result=join(package.load(raw_catalogue),proof,a.package,{n:i for i,n in enumerate(names)},partial);result.update({'catalogue':str(a.catalogue.resolve()),'catalogueSha256':package.sha(raw_catalogue),'kindSourceSha256':package.sha(raw),'packageFingerprint':proof['packageFingerprint'],'manifestSHA256':proof['manifestSHA256']})
 a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
