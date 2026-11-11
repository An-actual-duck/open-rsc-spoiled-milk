#!/usr/bin/env python3
"""Independently reconcile bounded production census with selected raw inventory."""
import argparse,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--objects',type=Path);p.add_argument('--inventory',type=Path,default=ROOT/'output/diagnostics/overworld-zone-inventory/inventory.json');a=p.parse_args();r=json.loads((a.run/'result.json').read_text());i=json.loads(a.inventory.read_text());assert r['passed']and r['manifestSha256']==i['selection']['manifestSha256']and r['packageFingerprint']==i['selection']['packageFingerprintSha256'];raw=a.run/'cells.jsonl';assert sha(raw)==r['rawCellsSha256'];seen=set();totals={}
 for line in raw.read_text().splitlines():
  row=json.loads(line);level=row['level'];key=(level,row['sectorX'],row['sectorY']);assert key not in seen;seen.add(key);assert len(row['cells'])==4;assert {(c['cellX'],c['cellY'])for c in row['cells']}=={(row['sectorX']*2+x,row['sectorY']*2+y)for x in [0,1]for y in [0,1]};total=totals.setdefault(level,{'sectors':0,'triangles':0,'vertices':0,'packedBytes':0,'primitiveBytes':0,'stitchedBytes':0});total['sectors']+=1;total['stitchedBytes']+=row['actualPositiveStitchPackedBytes']
  for c in row['cells']:
   assert c['level']==level and c['cellX']//2==row['sectorX']and c['cellY']//2==row['sectorY'];n=c['triangles'];v=c['vertices'];assert v==3*n and n==c['terrainTriangles']+c['wallTriangles']+c['roofTriangles'];assert c['packedVertexBytes']==112*v and c['packedIndexBytes']==12*n and c['primitiveMeshLowerBoundBytes']==44*v+24*n
   for k,value in [('triangles',n),('vertices',v),('packedBytes',112*v+12*n),('primitiveBytes',44*v+24*n)]:total[k]+=value
 for level,t in totals.items():
  assert t['sectors']==i['levels']['global:'+str(level)]['sectors'];original=r['totalsByLevel'][str(level)];assert t['triangles']==original['triangles']and t['vertices']==original['vertices'];assert t['packedBytes']==original['packedVertexBytes']+original['packedIndexBytes'];assert t['stitchedBytes']==original['actualPositiveStitchPackedBytes']
 objects=None
 if a.objects:
  receipt=json.loads((a.objects/'result.json').read_text());counts_path=a.inventory.parent/'placement-definition-counts.json';assert sha(counts_path)==receipt['placementCountsSha256'];assert sha(a.objects/'models.jsonl')==receipt['modelsSha256'];counts=json.loads(counts_path.read_text());models=[json.loads(line)for line in(a.objects/'models.jsonl').read_text().splitlines()];by={(m['family'],m['definitionId']):m for m in models};assert len(by)==len(models)==receipt['uniqueDefinitions'];objects={}
  for level in [0,1,2]:
   expected=receipt['perLevel'][str(level)];n=packed=primitive=triangles=0;unique={}
   for family in ['scenery','boundaries']:
    for id,count in counts['global:'+str(level)+':'+family].items():
     model=by[(family,int(id))];assert model['resolved'];v,t=model['vertices'],model['triangles'];assert v==3*t and model['packedVertexBytes']==112*v and model['packedIndexBytes']==12*t and model['primitiveMeshLowerBoundBytes']==44*v+24*t;n+=count;packed+=count*(112*v+12*t);primitive+=count*(44*v+24*t);triangles+=count*t;unique[model['geometrySha256']]=112*v+12*t
   assert n==expected['manifestPlacements']==expected['resolvedPlacements'];assert packed==expected['packedVertexBytes']+expected['packedIndexBytes']and primitive==expected['primitiveMeshLowerBoundBytes']and triangles==expected['triangles'];assert len(unique)==expected['uniqueGeometryPrototypes']and sum(unique.values())==expected['uniquePrototypePackedBytes'];objects[level]={'placements':n,'packedBytes':packed,'primitiveBytes':primitive,'prototypeCount':len(unique),'hypotheticalPrototypePackedBytes':sum(unique.values())}
  objects={'receiptSha256':sha(a.objects/'result.json'),'modelsSha256':sha(a.objects/'models.jsonl'),'perLevel':objects}
 result={'passed':True,'auditOnly':True,'censusReceiptSha256':sha(a.run/'result.json'),'censusRawSha256':sha(raw),'inventorySha256':sha(a.inventory),'perLevel':totals,'objects':objects,'limits':['Reconciles production census output and formula/source review, not independent second rendering or GPU allocation.','112-byte vertex current bridge stride plus uint32 indices; excludes textures, atlas/material resolved batches, shadows, framebuffers, sprites, geometry wrappers, source clones and driver costs.','44-byte per-vertex primitive array+24-byte triangle is CPU lower bound including generated normals; excludes headers/enum references/roof coverage/effects.','Canonical centroid-owned24 cells avoid duplicate triangles; current positive-edge48-cell stitch measured separately, not simultaneously summed.','No FPS, all-world residency, launch/load or hardware memory acceptance.']};out=a.inventory.parent/'geometry-review.json';out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__':main()
