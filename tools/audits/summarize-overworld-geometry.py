#!/usr/bin/env python3
"""Summarize owned audit JSONL without loading or retaining world geometry."""
from pathlib import Path
import argparse,json,math,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('run',type=Path);args=p.parse_args();run=args.run.resolve()
rows=[json.loads(x) for x in (run/'cells.jsonl').read_text().splitlines()];summary={}
def stats(values):
 values=sorted(values)
 return {'minimum':values[0],'median':(values[(len(values)-1)//2]+values[len(values)//2])/2,'p95NearestRank':values[max(0,math.ceil(.95*len(values))-1)],'maximum':values[-1]}
for level in sorted(set(row['level'] for row in rows)):
 selected=[r for r in rows if r['level']==level];cells=[c for r in selected for c in r['cells']]
 packed=sum(c['packedVertexBytes']+c['packedIndexBytes'] for c in cells);stitched=sum(r['actualPositiveStitchPackedBytes'] for r in selected)
 summary[str(level)]={'sectors':len(selected),'cells':len(cells),'zeroGeometryCells':sum(c['triangles']==0 for c in cells),'terrainOnlyCells':sum(c['triangles']>0 and c['wallTriangles']==0 and c['roofTriangles']==0 for c in cells),'structuralCells':sum(c['wallTriangles']+c['roofTriangles']>0 for c in cells),'cellVertices':stats([c['vertices'] for c in cells]),'cellTriangles':stats([c['triangles'] for c in cells]),'cellPackedVertexIndexBytes':stats([c['packedVertexBytes']+c['packedIndexBytes'] for c in cells]),'sectorBuildMilliseconds':stats([r['elapsedMs'] for r in selected]),'canonicalPackedVertexIndexBytes':packed,'positiveStitchPackedVertexIndexBytes':stitched,'positiveStitchExtraPercent':100*(stitched-packed)/packed if packed else 0,'primitiveMeshLowerBoundBytes':sum(c['primitiveMeshLowerBoundBytes'] for c in cells),'conditionalRawKeyBatchBytes':sum(c['rawMaterialGroupBatchBytes'] for c in cells),'batchAbsoluteUpperBoundBytes':20*sum(c['triangles'] for c in cells),'maximumObservedPostSectorHeapBytes':max(r['usedHeapBytes'] for r in selected),'sectorsWithMissingNeighborPages':sum(r['missingNeighborPages']>0 for r in selected)}
result={'perLevel':summary,'cellsSha256':hashlib.sha256((run/'cells.jsonl').read_bytes()).hexdigest(),'scope':'Cell densities include authored sea/blank/padding; terrainOnly is geometry classification, not inferred sea. No geometry extrapolation. GPU resident vertex/index bytes exclude material batches and all non-mesh allocations; primitive arrays are a separate CPU lower bound.'}
(run/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
