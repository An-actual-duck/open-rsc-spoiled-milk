#!/usr/bin/env python3
"""Independent bounded packed-file integrity; does not create GL or prove geometry parity."""
import argparse,hashlib,importlib.util,json,math,struct
from pathlib import Path
_s=importlib.util.spec_from_file_location('zone_package',Path(__file__).with_name('verify-package.py'));package=importlib.util.module_from_spec(_s);_s.loader.exec_module(package)
def fail(s):raise ValueError(s)
def f32(n):return struct.unpack('<f',struct.pack('<f',n))[0]
def check_chunk(raw,c,material_ids):
 if len(raw)!=c['bytes']or package.sha(raw)!=c['sha256']:fail('Packed file bytes/hash mismatch')
 if len(raw)<16 or struct.unpack_from('<4I',raw)!=(0x575a4331,c['vertexCount'],c['indexCount'],c['batchIntCount']):fail('Packed header mismatch')
 v,i,b=c['vertexCount'],c['indexCount'],c['batchIntCount']
 if min(v,i,b)<0 or v>524288 or i>1572864 or i%3 or b%5 or len(raw)!=16+v*112+i*4+b*4:fail('Packed dimensions')
 floats=memoryview(raw)[16:16+v*112].cast('f');indices=memoryview(raw)[16+v*112:16+v*112+i*4].cast('i');batches=memoryview(raw)[16+v*112+i*4:].cast('i')
 # Explicitly reject nonfinite fields even when attacker also edits the declared SHA.
 for x in floats:
  if not math.isfinite(x):fail('Nonfinite packed vertex')
 for index in indices:
  if not 0<=index<v:fail('Packed index out of range')
 ranges=[];drawn=0
 for at in range(0,b,5):
  material,start,count,color,alpha=batches[at:at+5]
  if start<0 or count<0 or start+count>i or start%3 or count%3 or alpha not in(0,1):fail('Packed material range')
  if material>=0 and material not in material_ids:fail('Packed material ID absent from actual textures')
  if count:ranges.append((start,start+count));drawn+=count
 ranges.sort()
 if any(ranges[n][0]<ranges[n-1][1]for n in range(1,len(ranges))):fail('Overlapping material draw ranges')
 bounds=[0.0]*6
 if v:
  bounds=[float('inf')]*3+[float('-inf')]*3
  for at in range(0,len(floats),28):
   p=(f32(floats[at]+c['originX']),floats[at+1],f32(floats[at+2]+c['originZ']))
   for n in range(3):bounds[n]=min(bounds[n],p[n]);bounds[n+3]=max(bounds[n+3],p[n])
 if c['bbox']!=bounds:fail('Packed global bounds mismatch')
 return {'vertices':v,'indices':i,'drawnIndices':drawn,'bytes':len(raw)}
def check_assets(catalogue,root):
 expected=['Cache/video/'+n for n in ['Authentic_Landscape.orsc','Authentic_Sprites.orsc','Custom_Sprites.osar','library.orsc','models.orsc']]
 rows=catalogue.get('externalAssets');total=0
 if not isinstance(rows,list)or len(rows)!=5:fail('External asset roster')
 for name,row in zip(expected,rows):
  if row.get('path')!=name or type(row.get('bytes'))is not int or not 1<=row['bytes']<=16*1024*1024:fail('External asset identity/budget')
  raw=package.safe(root,name).read_bytes()
  if len(raw)!=row['bytes']or package.sha(raw)!=row['sha256']:fail('External asset bytes/hash mismatch')
  total+=len(raw)
 if total>64*1024*1024:fail('Aggregate external asset budget')
 return {'externalAssetsVerified':5,'externalAssetBytes':total}
def verify(catalogue,root,asset_root=None):
 if struct.pack('=I',1)!=b'\x01\0\0\0':fail('Little endian proof host required')
 textures=catalogue['textures'];material_ids=set();total=0;resources=0
 for t in textures:
  if type(t['id'])is not int or t['id']in material_ids:fail('Texture ID duplication')
  material_ids.add(t['id']);raw=package.safe(root,t['path']).read_bytes()
  if not 1<=t['width']<=16384 or not 1<=t['height']<=16384 or t['width']*t['height']>262144 or t['bytes']!=t['width']*t['height']*4 or len(raw)!=t['bytes']or package.sha(raw)!=t['sha256']:fail('Texture file hash/shape mismatch')
  total+=len(raw);resources+=1
 vertices=indices=drawn=0;paths=set()
 for c in catalogue['chunks']:
  if c['path']in paths:fail('Packed resource path reused')
  paths.add(c['path']);r=check_chunk(package.safe(root,c['path']).read_bytes(),c,material_ids);vertices+=r['vertices'];indices+=r['indices'];drawn+=r['drawnIndices'];total+=r['bytes'];resources+=1
 result={'passed':True,'completeDeclared':catalogue.get('complete'),'resources':resources,'chunkResources':len(catalogue['chunks']),'textureResources':len(textures),'vertices':vertices,'indices':indices,'materialDrawnIndices':drawn,'verifiedResourceBytes':total,'scope':'Exact actual packed file hashes/header/finite fields/index/material/bounds consistency only. No ordinary-source geometry equality, GPU allocation, draw use, image parity or performance proof.'}
 if asset_root is not None:result.update(check_assets(catalogue,asset_root))
 return result
def main():
 a=argparse.ArgumentParser(description=__doc__);a.add_argument('--catalogue',type=Path,required=True);a.add_argument('--out',type=Path,required=True);a.add_argument('--asset-root',type=Path,help='Explicit source/stage root containing Cache/video; independently verify exact external archives');o=a.parse_args()
 if o.out.exists():fail('Preserve prior proof')
 if o.catalogue.stat().st_size>32*1024*1024:fail('Catalogue JSON budget')
 raw=o.catalogue.read_bytes();r=verify(package.load(raw),o.catalogue.parent,o.asset_root);r['catalogueSha256']=package.sha(raw);r['verifierSha256']=package.sha(Path(__file__).read_bytes());o.out.parent.mkdir(parents=True,exist_ok=True);o.out.write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r,indent=2))
if __name__=='__main__':main()
