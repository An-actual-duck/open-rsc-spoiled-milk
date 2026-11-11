#!/usr/bin/env python3
"""Read-only bounded production geometry census; never starts a game/server/GPU."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True)
 p.add_argument('--limit',type=int,default=4,help='Explicit maximum sectors, at most2048; full run needs manager resource approval')
 p.add_argument('--levels',default='0');p.add_argument('--sector',action='append',default=[],help='Explicit level:x:y; otherwise sorted selected levels')
 p.add_argument('--name',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'));args=p.parse_args()
 if not 1<=args.limit<=2048:p.error('limit must be1..2048')
 levels=[int(x) for x in args.levels.split(',')]
 if any(x not in [0,1,2] for x in levels):p.error('scope is L0, with separate L1/L2 dependencies')
 cfg_path=ROOT/'server/world-builder-configs/primary.json';cfg=json.loads(cfg_path.read_text());package=(ROOT/cfg['serverMapRelativePath']).resolve();manifest_path=package/'manifest.json';manifest=json.loads(manifest_path.read_text())
 paired=(ROOT/cfg['clientMapRelativePath']).resolve()
 if sha(manifest_path)!=sha(paired/'manifest.json'):p.error('server/client manifests differ')
 jar=args.client_jar.resolve();build=json.loads(args.client_receipt.read_text())
 if sha(jar)!=build['jarSha256']:p.error('JAR/build receipt mismatch')
 source_manifest=args.client_receipt.parent/'source-manifest.json';sources=json.loads(source_manifest.read_text())
 if any(sha(ROOT/path)!=value for path,value in sources.items()):p.error('Current production sources differ from selected full-source artifact')
 entries={(e['level'],e['sectorX'],e['sectorY']):e for e in manifest['terrainSectors'] if e['worldSpace']=='global'}
 requests=[tuple(map(int,x.split(':'))) for x in args.sector] if args.sector else sorted(k for k in entries if k[0] in levels)[:args.limit]
 if len(requests)>args.limit or len(set(requests))!=len(requests) or any(k not in entries or k[0] not in levels for k in requests):p.error('Invalid, duplicate, out-of-scope or over-limit requests')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','overworld-zone-geometry-audit','--mkdir'],text=True).strip());run=base/args.name;run.mkdir();classes=run/'classes';classes.mkdir()
 fixture=ROOT/'tests/myworld/OverworldGeometryAudit.java';(run/'request.csv').write_text(''.join(','.join(map(str,k))+'\n' for k in requests))
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(fixture)],check=True)
 command=['/usr/bin/time','-v','java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true','-cp',str(classes)+':'+str(jar),'orsc.graphics.three.OverworldGeometryAudit',str(ROOT),str(package),str(run/'request.csv'),str(args.limit)]
 started=time.monotonic()
 with (run/'cells.jsonl').open('w') as out,(run/'resource.txt').open('w') as err:subprocess.run(command,stdout=out,stderr=err,check=True,timeout=300)
 rows=[json.loads(x) for x in (run/'cells.jsonl').read_text().splitlines()]
 assert len(rows)==len(requests)
 totals={}
 for row in rows:
  level=str(row['level']);total=totals.setdefault(level,{'sectors':0,'cells':0,'vertices':0,'triangles':0,'terrainTriangles':0,'wallTriangles':0,'roofTriangles':0,'packedVertexBytes':0,'packedIndexBytes':0,'primitiveMeshLowerBoundBytes':0,'rawMaterialGroupBatchBytes':0,'actualPositiveStitchCellVertices':0,'actualPositiveStitchCellTriangles':0,'actualPositiveStitchPackedBytes':0,'missingNeighborPages':0})
  total['sectors']+=1;total['cells']+=4
  for key in ['actualPositiveStitchCellVertices','actualPositiveStitchCellTriangles','actualPositiveStitchPackedBytes','missingNeighborPages']:total[key]+=row[key]
  for cell in row['cells']:
   for key in ['vertices','triangles','terrainTriangles','wallTriangles','roofTriangles','packedVertexBytes','packedIndexBytes','primitiveMeshLowerBoundBytes','rawMaterialGroupBatchBytes']:total[key]+=cell[key]
 receipt={'passed':True,'auditOnly':True,'cpuOnly':True,'processesStarted':'one bounded headless CPU harness; no game/browser/server/GPU','heapLimitMiB':512,'elapsedWallSeconds':time.monotonic()-started,'sectorLimit':args.limit,'requestedSectors':len(requests),'package':str(package),'manifestSha256':sha(manifest_path),'packageFingerprint':package.parent.name,'selectedConfigSha256':sha(cfg_path),'clientJar':str(jar),'clientJarSha256':sha(jar),'clientReceiptSha256':sha(args.client_receipt),'clientSourceManifestSha256':sha(source_manifest),'auditSourceSha256':sha(fixture),'scriptSha256':sha(Path(__file__)),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'totalsByLevel':totals,'rawCellsSha256':sha(run/'cells.jsonl'),'resourceReceiptSha256':sha(run/'resource.txt'),'scope':'Canonical24tile ownership from one production3x3 build per48sector, centroid assign central48 once. Positive-stitch48cell costs separately. Upper wall/roof floor-local topology counts, no upper terrain. GPU112Bvertex+4Bindex excludes texture/shadow/sprite/framebuffers. Primitive mesh bytes lower bound excludes headers/enum refs/roofbits/effects. Material batches conditional raw-key count, not actual texture-atlas resolved batches.'}
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'run':str(run),**receipt},indent=2))
if __name__=='__main__':main()
