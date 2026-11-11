#!/usr/bin/env python3
"""Independent whole-zone source proof. Reads only; never connects or launches."""
import argparse,collections,hashlib,json,os,re,stat
from pathlib import Path,PurePosixPath
MAX_FILE=64*1024*1024;MAX_TOTAL=512*1024*1024;MAX_FILES=65536

def refuse(message):raise ValueError(message)
def unique(pairs):
 out={}
 for k,v in pairs:
  if k in out:refuse('Duplicate JSON key '+k)
  out[k]=v
 return out
def load(raw):return json.loads(raw,object_pairs_hook=unique,parse_constant=lambda x:refuse('Nonfinite JSON '+x))
def sha(raw):return hashlib.sha256(raw).hexdigest()
def safe(root,name):
 if not isinstance(name,str):refuse('Package path must be text')
 p=PurePosixPath(name)
 if not name or p.is_absolute()or '..'in p.parts or '\\'in name or str(p)!=name:refuse('Noncanonical package path')
 here=root
 for part in p.parts:
  here/=part
  if here.is_symlink():refuse('Symlink in package payload')
 if not here.is_file():refuse('Missing package payload '+name)
 if here.stat().st_size>MAX_FILE:refuse('Oversized package payload')
 return here

def verify(root,fingerprint,manifest_sha):
 root=Path(root).absolute()
 if root!=root.resolve()or not root.is_dir():refuse('Redirected/missing package')
 if not all(re.fullmatch('[0-9a-f]{64}',s)for s in [fingerprint,manifest_sha]):refuse('Exact expected package/manifest SHA required')
 raw=safe(root,'manifest.json').read_bytes()
 if sha(raw)!=manifest_sha:refuse('Manifest identity mismatch')
 m=load(raw);declared={'manifest.json':manifest_sha};coords=set();roster=[];placements=[]
 for family in ['terrainSectors','placementSets']:
  entries=m.get(family)
  if not isinstance(entries,list)or len(entries)>MAX_FILES:refuse('Bounded manifest arrays required')
  for e in entries:
   path=e['path'];expected=e['sha256']
   if path in declared or not re.fullmatch('[0-9a-f]{64}',expected):refuse('Duplicate payload path/invalid digest')
   data=safe(root,path).read_bytes()
   if sha(data)!=expected:refuse('Declared payload hash mismatch '+path)
   declared[path]=expected
   if family=='terrainSectors':
    key=(e['worldSpace'],e['level'],e['sectorX'],e['sectorY'])
    if key in coords:refuse('Duplicate sector ownership')
    coords.add(key)
    if any(type(e[k])!=int for k in ['level','sectorX','sectorY']):refuse('Signed integer sector ownership required')
    stride={'raw-layered-sector-v1':10,'raw-layered-sector-v2-u16':11}.get(e['encoding'])
    if stride is None or len(data)!=48*48*stride:refuse('Unsupported/invalid exact sector encoding')
    roster.append({'worldSpace':key[0],'level':key[1],'sectorX':key[2],'sectorY':key[3],'path':path,'sha256':expected,'bytes':len(data)})
   else:
    p=load(data)
    if p['worldSpace']!=e['worldSpace']or p['level']!=e['level']:refuse('Placement world/level mismatch')
    ids=set()
    for kind in ['scenery','boundaries','npcs','groundItems']:
     for row in p.get(kind,[]):
      pid=row['placementId']
      if pid in ids:refuse('Duplicate placement identity')
      ids.add(pid);pos=row['start']if kind=='npcs'else row['position']
      placements.append({'worldSpace':e['worldSpace'],'level':e['level'],'family':kind,'placementId':pid,'x':pos['x'],'y':pos['y']})
 records=[];total=0
 for parent,dirs,files in os.walk(root,followlinks=False):
  for n in dirs:
   if not stat.S_ISDIR((Path(parent)/n).lstat().st_mode):refuse('Symlink/non-directory in package inventory')
  for n in files:
   p=Path(parent)/n;relative=p.relative_to(root).as_posix()
   if relative not in declared:refuse('Undeclared package file '+relative)
   payload=safe(root,relative).read_bytes();digest=sha(payload)
   if digest!=declared[relative]:refuse('Inventory content mismatch')
   total+=len(payload);records.append((relative,len(payload),digest))
   if total>MAX_TOTAL or len(records)>MAX_FILES:refuse('Bounded inventory exceeded')
 if {r[0]for r in records}!=set(declared):refuse('Incomplete actual package inventory')
 h=hashlib.sha256()
 for record in sorted(records):
  for field in record:h.update(str(field).encode()+b'\0')
 if h.hexdigest()!=fingerprint:refuse('Full package fingerprint mismatch')
 levels=collections.Counter((r['worldSpace'],r['level'])for r in roster)
 return {'passed':True,'package':str(root),'packageFingerprint':fingerprint,'manifestSHA256':manifest_sha,'files':len(records),'fileBytes':total,'sectorCounts':{f'{w}:{l}':n for(w,l),n in sorted(levels.items())},'sectorRoster':roster,'manifestPlacements':placements,'scope':'Exact payload/inventory/ownership source proof only. No running-server binary, CPU geometry retention, GPU allocation/catalog use or performance proof.'}

def main():
 a=argparse.ArgumentParser(description=__doc__);a.add_argument('--package',type=Path,required=True);a.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3]);a.add_argument('--out',type=Path,required=True);o=a.parse_args();root=o.root.resolve()
 primary=load((root/'server/world-builder-configs/primary.json').read_bytes());profile=load((root/'Client_Base/world-builder-configs/installed-client.json').read_bytes());expected=profile['packageFingerprintSha256'];manifest_sha=profile['manifestSha256']
 if expected not in PurePosixPath(primary['serverMapRelativePath']).parts or profile.get('active')is not True:refuse('Current paired source selection does not bind expected package')
 if sha((root/primary['serverMapRelativePath']/'manifest.json').read_bytes())!=manifest_sha:refuse('Current selected manifest disagrees with client profile')
 result=verify(o.package,expected,manifest_sha)
 if o.out.exists():refuse('Refuse replacing prior source proof')
 o.out.parent.mkdir(parents=True,exist_ok=True);o.out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items()if k not in ['sectorRoster','manifestPlacements']},indent=2))
if __name__=='__main__':main()
