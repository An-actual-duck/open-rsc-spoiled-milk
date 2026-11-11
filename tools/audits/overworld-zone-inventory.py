#!/usr/bin/env python3
"""Audit selected paired native map one payload at a time; never render or edit it."""
import argparse,collections,hashlib,importlib.util,json,struct,subprocess,sys,zipfile,zlib
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
EXPECTED='7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc'
MANIFEST='42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b'
def sha(b):return hashlib.sha256(b).hexdigest()
def digest_file(p):
 h=hashlib.sha256()
 with p.open('rb')as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def canonical(raw,encoding):
 if encoding=='raw-layered-sector-v2-u16':assert len(raw)==48*48*11;return raw
 assert encoding=='raw-layered-sector-v1'and len(raw)==48*48*10
 return b''.join(b'\x00'+raw[i:i+10]for i in range(0,len(raw),10))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--output',type=Path);a=p.parse_args();root=a.root.resolve()
 out=a.output or Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','diagnostics','overworld-zone-inventory','--mkdir'],text=True).strip());out.mkdir(parents=True,exist_ok=True)
 spec=importlib.util.spec_from_file_location('selected',root/'scripts/installed-map-selection.py');selected=importlib.util.module_from_spec(spec);spec.loader.exec_module(selected);selection=selected.select(root);assert selection['packageFingerprintSha256']==EXPECTED and selection['manifestSha256']==MANIFEST
 package=Path(selection['serverPackage']);manifest=json.loads((package/'manifest.json').read_text());assert manifest['storage']=={'sectorSize':48,'presentationChunkSize':24};(out/'selection.json').write_text(json.dumps(selection,indent=2)+'\n')
 # The maintained client literal tile table supplies overlay type4; verify scalar literals only.
 import re
 client=(root/'Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java').read_text();tile_literals=re.findall(r'tiles\.add\(new TileDef\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)\);',client);water_ids=[n+1 for n,(_color,t,_obj)in enumerate(tile_literals)if int(t)==4];assert water_ids
 sectors=[];levels={};seen_raw={};seen_canonical={};file_hashes={};placement_counts=collections.Counter();family_ids={};placement_rows=[];placed_cells=collections.Counter()
 for item in manifest['placementSets']:
  path=package/item['path'];raw=path.read_bytes();assert sha(raw)==item['sha256'];file_hashes[item['path']]=sha(raw);j=json.loads(raw);assert j['level']==item['level']and j['worldSpace']==item['worldSpace'];level=item['level'];world=item['worldSpace']
  for family,key in [('scenery','sceneryId'),('boundaries','boundaryId'),('npcs','npcId'),('groundItems','itemId')]:
   for row in j.get(family,[]):
    value=row.get(key)
    if value is None:raise ValueError('Unrecognized '+family+' definition field: '+str(row))
    position=row['start']if family=='npcs'else row['position'];x,y=position['x'],position['y'];placement_counts[(world,level,family)]+=1;family_ids.setdefault((world,level,family),collections.Counter())[value]+=1;placed_cells[(world,level,x//48,y//48)]+=1;placement_rows.append([world,level,family,value,row.get('direction',''),x,y,row['placementId']])
 for item in manifest['terrainSectors']:
  path=package/item['path'];raw=path.read_bytes();actual=sha(raw);assert actual==item['sha256'];file_hashes[item['path']]=actual;normal=canonical(raw,item['encoding']);semantic=sha(normal);world,level=item['worldSpace'],item['level'];key=world+':'+str(level);s=levels.setdefault(key,{'worldSpace':world,'level':level,'sectors':0,'rawBytes':0,'zlib9IndependentBytes':0,'encodings':collections.Counter(),'rawHashes':{},'canonicalHashes':{},'classes':collections.Counter(),'tiles':0,'waterlikeTiles':0,'structureTiles':0,'zeroTiles':0,'overlayHistogram':collections.Counter()});compressed=len(zlib.compress(raw,9));s['sectors']+=1;s['tiles']+=2304;s['rawBytes']+=len(raw);s['zlib9IndependentBytes']+=compressed;s['encodings'][item['encoding']]+=1;s['rawHashes'][actual]=len(raw);s['canonicalHashes'][semantic]=len(normal);seen_raw[actual]=len(raw);seen_canonical[semantic]=len(normal)
  row=dict(worldSpace=world,level=level,sectorX=item['sectorX'],sectorY=item['sectorY'],path=item['path'],encoding=item['encoding'],sha256=actual,canonicalU16Sha256=semantic,rawBytes=len(raw),zlib9Bytes=compressed,uniform=normal==normal[:11]*2304,rawAllZero=not any(raw),placements=placed_cells[(world,level,item['sectorX'],item['sectorY'])],uniformTile=list(struct.unpack('>H5BI',normal[:11])) if normal==normal[:11]*2304 else None)
  if world=='global'and level in [0,1,2]:
   water=structure=zeros=0;hist=collections.Counter();nonzero_bounds=None
   for n,tile in enumerate(struct.iter_unpack('>H5BI',normal)):
    elevation,texture,overlay,roof,vertical,horizontal,diagonal=tile;water+=overlay in water_ids;structure+=bool(roof or vertical or horizontal or diagonal);zeros+=not any(tile);hist[overlay]+=1
    if any(tile):
     x=item['sectorX']*48+n//48;y=item['sectorY']*48+n%48
     if nonzero_bounds is None:nonzero_bounds=[x,y,x,y]
     else:nonzero_bounds=[min(nonzero_bounds[0],x),min(nonzero_bounds[1],y),max(nonzero_bounds[2],x),max(nonzero_bounds[3],y)]
   row.update(waterlikeTiles=water,structureTiles=structure,zeroTiles=zeros,nonzeroTileBounds=nonzero_bounds);s['waterlikeTiles']+=water;s['structureTiles']+=structure;s['zeroTiles']+=zeros;s['overlayHistogram'].update(hist)
   cls='all-zero'if row['rawAllZero']else 'uniform-waterlike'if water==2304 and row['uniform']else 'all-waterlike'if water==2304 else 'uniform-overlay-250'if row['uniform']and row['uniformTile'][2]==250 else 'other-source-terrain';row['classification']=cls;s['classes'][cls]+=1
  sectors.append(row)
 # Report bounds/presence; holes are absent storage, never inferred authored walkability.
 for key,s in levels.items():
  rows=[r for r in sectors if r['worldSpace']==s['worldSpace']and r['level']==s['level']];xx=[r['sectorX']for r in rows];yy=[r['sectorY']for r in rows];bounds=[min(xx),min(yy),max(xx),max(yy)];s['sectorBounds']=bounds;s['tileBoundsInclusive']=[bounds[0]*48,bounds[1]*48,(bounds[2]+1)*48-1,(bounds[3]+1)*48-1];s['missingSectorsInsideBounds']=(bounds[2]-bounds[0]+1)*(bounds[3]-bounds[1]+1)-len(rows);s['uniqueRawPayloads']=len(s['rawHashes']);s['deduplicatedRawBytes']=sum(s.pop('rawHashes').values());s['uniqueCanonicalU16Payloads']=len(s['canonicalHashes']);s['deduplicatedCanonicalU16Bytes']=sum(s.pop('canonicalHashes').values())
  s['placements']={f:placement_counts[(s['worldSpace'],s['level'],f)]for f in ['scenery','boundaries','npcs','groundItems']};s['uniqueDefinitionIds']={f:len(family_ids.get((s['worldSpace'],s['level'],f),{}))for f in s['placements']}
 for name,rows in [('sectors.json',sectors),('placements.json',placement_rows)]: (out/name).write_text(json.dumps(rows,indent=2)+'\n')
 l0=[r for r in sectors if r['worldSpace']=='global'and r['level']==0];cells=[dict(worldSpace='global',level=0,cellSize=size,cellX=r['sectorX']*(48//size)+dx,cellY=r['sectorY']*(48//size)+dy,storageSectorX=r['sectorX'],storageSectorY=r['sectorY'])for size in [24,48]for r in l0 for dx in range(48//size)for dy in range(48//size)];(out/'l0-cells.json').write_text(json.dumps(cells,indent=2)+'\n')
 def_counts={str(k[0])+':'+str(k[1])+':'+k[2]:dict(sorted(v.items()))for k,v in family_ids.items()};(out/'placement-definition-counts.json').write_text(json.dumps(def_counts,indent=2)+'\n')
 report=dict(auditOnly=True,sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),selection=selection,storage=manifest['storage'],waterlikeOverlayIds=water_ids,waterClassificationScope='Raw current client TileDef tileValue4 literals only; overlay250 is separately retained because World.applyBridgeDecorations resolves it to2/9 depending on neighbors. Not navigability or a geographic ocean/island classification; no discard permission.',levels=levels,terrainFiles=len(sectors),allUniqueRawPayloads=len(seen_raw),allDeduplicatedRawBytes=sum(seen_raw.values()),allUniqueCanonicalU16Payloads=len(seen_canonical),allDeduplicatedCanonicalU16Bytes=sum(seen_canonical.values()),allManifestPayloadHashesVerified=True,manifestPayloadHashCount=len(file_hashes),l0CellCounts={'storage48':len(l0),'presentation24':len(l0)*4},limits=['Stored bounds include islands/member/free/wilderness without membership/geographic tags; every L0 sector retained.','All-zero/raw-uniform are source-byte facts, not permission to omit sectors or assume void.','zlib9IndependentBytes is an audit compression simulation, not current network/release size.','No production Oracle host inspected; installed source and paired files only.','Geometry/VRAM/draw counts require independent production census; raw terrain bytes are not packed geometry bytes.'])
 (out/'manifest-payload-hashes.json').write_text(json.dumps(file_hashes,indent=2,sort_keys=True)+'\n');(out/'inventory.json').write_text(json.dumps(report,indent=2,sort_keys=True)+'\n');print(json.dumps({'passed':True,'output':str(out),'terrainFiles':len(sectors),'l0':levels['global:0'],'payloadHashes':len(file_hashes)},default=dict))
if __name__=='__main__':main()
