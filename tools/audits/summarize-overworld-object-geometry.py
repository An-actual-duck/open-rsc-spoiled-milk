#!/usr/bin/env python3
"""Reconcile baseline-frame object geometry and hypothetical prototype sharing separately."""
from pathlib import Path
import argparse,json,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('run',type=Path);p.add_argument('--placement-counts',type=Path,required=True);args=p.parse_args();run=args.run.resolve();rows=[json.loads(s) for s in(run/'models.jsonl').read_text().splitlines()];by={(r['family'],r['definitionId']):r for r in rows};counts=json.loads(args.placement_counts.read_text());out={};all_unique={}
for level in [0,1,2]:
 summary={'staticPlacements':0,'animatedBaselinePlacements':0,'staticPackedBytes':0,'animatedBaselinePackedBytes':0,'staticVertices':0,'animatedBaselineVertices':0,'families':{}}
 for family in ['scenery','boundaries']:
  f={'placements':0,'resolved':0,'packedVertexIndexBytes':0,'sourceVertices':0,'sourceFaces':0}
  for id,count in counts.get(f'global:{level}:{family}',{}).items():
   r=by[(family,int(id))];f['placements']+=count
   if not r['resolved']:continue
   f['resolved']+=count;size=r['packedVertexBytes']+r['packedIndexBytes'];f['packedVertexIndexBytes']+=size*count;f['sourceVertices']+=r['sourceVertices']*count;f['sourceFaces']+=r['sourceFaces']*count
   k='animatedBaseline' if r['animatedResident'] else 'static';summary[k+'Placements']+=count;summary[k+'PackedBytes']+=size*count;summary[k+'Vertices']+=r['vertices']*count;all_unique[r['geometrySha256']]=r
  summary['families'][family]=f
 out[str(level)]=summary
result={'perLevel':out,'allThreeLevelsUniqueGeometryPrototypes':len(all_unique),'allThreeLevelsUniquePrototypePackedBytes':sum(r['packedVertexBytes']+r['packedIndexBytes'] for r in all_unique.values()),'modelsSha256':hashlib.sha256((run/'models.jsonl').read_bytes()).hexdigest(),'placementCountsSha256':hashlib.sha256(args.placement_counts.read_bytes()).hexdigest(),'scope':'Current transformed copies static vs baseline animation separated. Prototype byte total is hypothetical instancing lower bound, not supported current residency or per-instance sharing proof; excludes animation variants/texture/sprite resources.'}
(run/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
