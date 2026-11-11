#!/usr/bin/env python3
"""Frozen writer-prefix comparisons and independent scope obligations; CPU only."""
import argparse,hashlib,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='c7ed5960abf0bc6a514852b998151b7a5856a6fc'
SOURCES=['Client_Base/src/orsc/graphics/two/'+n for n in ['GraphicsController.java','WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java']]
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',type=Path,required=True);p.add_argument('--candidate-commit',required=True);p.add_argument('--client-jar',type=Path,required=True);args=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',args.candidate_commit):p.error('Exact candidate commit required')
 jar=args.client_jar.resolve();candidate=args.candidate_root.resolve()
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-writer-span-independent','--mkdir'],text=True).strip())
 run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();hashes={};fixtureHashes={}
 def execute(label,command):
  result=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  (run/(label+'.stdout')).write_bytes(result.stdout);(run/(label+'.stderr')).write_bytes(result.stderr)
  result.check_returncode();return result.stdout
 literal=ROOT/'tests/myworld/WorldOverlayWriterSpanIndependentFixture.java';scope=ROOT/'tests/myworld/WorldOverlayWriterScopeIndependentFixture.java'
 for fixture in [literal,scope]:fixtureHashes[str(fixture.relative_to(ROOT))]=sha(fixture.read_bytes())
 classes={}
 for label,commit in [('baseline',BASE),('candidate',args.candidate_commit)]:
  folder=run/label;folder.mkdir();src=folder/'source';src.mkdir();out=folder/'classes';out.mkdir();classes[label]=out;hashes[label]={}
  for path in SOURCES:
   data=subprocess.check_output(['git','show',commit+':'+path],cwd=candidate);(src/Path(path).name).write_bytes(data);hashes[label][path]=sha(data)
  own=[literal]+([scope]if label=='candidate'else[])
  for fixture in own:(src/fixture.name).write_bytes(fixture.read_bytes())
  execute('compile-'+label,['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),*[str(src/Path(n).name)for n in SOURCES],*[str(src/q.name)for q in own]])
 snapshots={}
 for label,compiled in classes.items():
  for flag,delta,rect in [('off','true','packed'),('on','true','packed'),('on','false','packed'),('on','true','off')]:
   name=label+'-'+flag+'-'+delta+'-'+rect
   command=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserOverlayWriterSpans='+str(flag=='on').lower(),'-Dspoiledmilk.browserOverlayDeltaTransport='+delta,'-Dspoiledmilk.browserOverlayRectMode='+rect,'-cp',str(compiled)+':'+str(jar),'orsc.graphics.two.WorldOverlayWriterSpanIndependentFixture']
   snapshots[name]=execute(name,command)
 reference=snapshots['baseline-off-true-packed']
 assert reference and len(reference.splitlines())>=200,'vacuous writer corpus'
 for name,data in snapshots.items():assert data==reference,'Literal prefix/exception mismatch: '+name
 execute('scope',['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserOverlayWriterSpans=true','-Dspoiledmilk.browserOverlayDeltaTransport=true','-Dspoiledmilk.browserOverlayRectMode=packed','-cp',str(classes['candidate'])+':'+str(jar),'orsc.graphics.two.WorldOverlayWriterScopeIndependentFixture'])
 receipt=dict(passed=True,cpuOnly=True,browserStarted=False,gpuExecuted=False,serverStarted=False,baselineCommit=BASE,candidateCommit=args.candidate_commit,sourceSha256=hashes,fixtureSha256=fixtureHashes,runnerSha256=sha(Path(__file__).read_bytes()),dependencyJarSha256=sha(jar.read_bytes()),snapshotCases=len(reference.splitlines()),snapshotSha256={k:sha(v)for k,v in snapshots.items()},scope='Exact literal C/raw-T, partial exception/catch snapshots and independently asserted scope/owner/ACK/footprint lifecycle. No browser, real GPU or FPS proof.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS independent writer span review:',run/'result.json')
if __name__=='__main__':main()
