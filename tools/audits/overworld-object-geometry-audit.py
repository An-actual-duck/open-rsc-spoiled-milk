#!/usr/bin/env python3
"""Join a bounded production model census with explicit manifest placement counts."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True);p.add_argument('--placement-counts',type=Path,required=True);p.add_argument('--limit',type=int,default=2048);p.add_argument('--name',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'));args=p.parse_args()
 if not 1<=args.limit<=2048:p.error('Unique definition cap is1..2048')
 build=json.loads(args.client_receipt.read_text());jar=args.client_jar.resolve()
 if sha(jar)!=build['jarSha256']:p.error('JAR/receipt mismatch')
 sources=json.loads((args.client_receipt.parent/'source-manifest.json').read_text())
 if any(sha(ROOT/path)!=value for path,value in sources.items()):p.error('Current production sources differ from selected artifact')
 counts=json.loads(args.placement_counts.read_text());roster=sorted(set((family,int(id)) for level in [0,1,2] for family in ['scenery','boundaries'] for id in counts.get(f'global:{level}:{family}',{})))
 if len(roster)>args.limit:p.error('Unique definition limit exceeded')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','overworld-zone-geometry-audit','--mkdir'],text=True).strip());run=base/args.name;run.mkdir();classes=run/'classes';classes.mkdir()
 request=run/'request.csv';request.write_text(''.join(f'{family},{id}\n' for family,id in roster));fixture=ROOT/'tests/myworld/OverworldObjectGeometryAudit.java'
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(fixture)],check=True)
 command=['/usr/bin/time','-v','java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true','-cp',str(classes)+':'+str(jar),'orsc.graphics.three.OverworldObjectGeometryAudit',str(ROOT),str(request),str(args.limit)]
 started=time.monotonic()
 with(run/'models.jsonl').open('w')as out,(run/'resource.txt').open('w')as err:subprocess.run(command,stdout=out,stderr=err,check=True,timeout=300)
 rows=[json.loads(line) for line in (run/'models.jsonl').read_text().splitlines()];by_key={(r['family'],r['definitionId']):r for r in rows};assert set(by_key)==set(roster)
 totals={}
 keys=['vertices','triangles','packedVertexBytes','packedIndexBytes','primitiveMeshLowerBoundBytes','sourceVertices','sourceFaces','shadowCasters','glowEmitters']
 for level in [0,1,2]:
  summary={'manifestPlacements':0,'resolvedPlacements':0,'unresolvedPlacements':0,'animatedCurrentFramePlacements':0,'interactionOnlyPlacements':0,'uniqueGeometryPrototypes':0,'uniquePrototypePackedBytes':0,**{k:0 for k in keys}}
  unique={};unresolved=[]
  for family in ['scenery','boundaries']:
   for definition,count in counts.get(f'global:{level}:{family}',{}).items():
    r=by_key[(family,int(definition))];summary['manifestPlacements']+=count
    if not r['resolved']:summary['unresolvedPlacements']+=count;unresolved.append({'family':family,'definitionId':int(definition),'count':count,'modelName':r['modelName'],'reason':r['resolution']});continue
    summary['resolvedPlacements']+=count
    for k in keys:summary[k]+=r[k]*count
    summary['animatedCurrentFramePlacements']+=count if r['animatedResident'] else 0
    summary['interactionOnlyPlacements']+=count if r['interactionOnly'] else 0
    unique[r['geometrySha256']]=r
  summary['uniqueGeometryPrototypes']=len(unique);summary['uniquePrototypePackedBytes']=sum(r['packedVertexBytes']+r['packedIndexBytes'] for r in unique.values());summary['unresolved']=unresolved;totals[str(level)]=summary
 result={'passed':True,'auditOnly':True,'cpuOnly':True,'uniqueDefinitions':len(roster),'resolvedDefinitions':sum(r['resolved'] for r in rows),'elapsedWallSeconds':time.monotonic()-started,'heapLimitMiB':512,'clientJarSha256':sha(jar),'clientReceiptSha256':sha(args.client_receipt),'placementCounts':str(args.placement_counts.resolve()),'placementCountsSha256':sha(args.placement_counts),'modelArchiveSha256':sha(ROOT/'Client_Base/Cache/video/models.orsc'),'auditSourceSha256':sha(fixture),'scriptSha256':sha(Path(__file__)),'modelsSha256':sha(run/'models.jsonl'),'resourceSha256':sha(run/'resource.txt'),'perLevel':totals,'scope':'Manifest scenery/boundary subtotal only; no NPC/summon/player/sprite/VFX/texture/shadow-map framebuffers or runtime additions. Source-model topology normalized via actual mudclient hooks; elevations/rotations change positions not triangle count. Animated object baseline-frame only, not animation-library residency. Weighted geometry is current transformed-copy representation; distinct primitive-array geometry hashes are a hypothetical instancing lower bound, not existing sharing eligibility.'}
 (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'run':str(run),**result},indent=2))
if __name__=='__main__':main()
