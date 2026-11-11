#!/usr/bin/env python3
"""Compile exact frozen terrain sources and independent ordered geometry oracle; no GPU."""
import argparse,hashlib,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='38bbb1c34f7f6b41fa369f25d122d343b33bbdb3'
SOURCES=['Client_Base/src/orsc/NativeLayeredTerrainChunk.java','Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java','Client_Base/src/com/openrsc/client/model/Tile.java']
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);args=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',args.candidate_commit):p.error('Exact candidate commit required')
 candidate=args.candidate_root.resolve();base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-terrain-query-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
 fixture=ROOT/'tests/myworld/NativeTerrainQueryIndependentFixture.java';hashes={};snapshots={};logs={}
 def execute(name,command):
  r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/(name+'.stdout')).write_bytes(r.stdout);(run/(name+'.stderr')).write_bytes(r.stderr);r.check_returncode();logs[name]=dict(stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr));return r.stdout
 for label,commit in [('baseline',BASE),('candidate',args.candidate_commit)]:
  folder=run/label;folder.mkdir();classes=folder/'classes';classes.mkdir();src=folder/'source';src.mkdir();hashes[label]={}
  for path in SOURCES:
   data=subprocess.check_output(['git','show',commit+':'+path],cwd=candidate);(src/Path(path).name).write_bytes(data);hashes[label][path]=sha(data)
  (src/fixture.name).write_bytes(fixture.read_bytes())
  execute('compile-'+label,['javac','-source','8','-target','8','-d',str(classes),*[str(src/Path(n).name)for n in SOURCES],str(src/fixture.name)])
  for flag in ['false','true']:
   name=label+'-'+flag;command=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserTerrainQueryIndex='+flag,'-cp',str(classes),'orsc.NativeTerrainQueryIndependentFixture']+(['metadata']if label=='candidate'else[])
   snapshots[name]=execute(name,command)
 reference=snapshots['baseline-false'];assert reference.startswith(b'SNAPSHOT ') and len(reference.strip())==73,'Missing frozen snapshot'
 for name,data in snapshots.items():assert data==reference,'Frozen terrain result mismatch: '+name
 receipt=dict(passed=True,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,baselineCommit=BASE,candidateCommit=args.candidate_commit,sourceSha256=hashes,fixtureSha256=sha(fixture.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),logs=logs,snapshot=reference.decode().strip(),scope='Independent ordered long-bounds reference and frozen all Tile/scalar/exception results, protocols3-10, mixed slots, edge ints and source ownership; no performance claim.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS independent terrain query review:',run/'result.json')
if __name__=='__main__':main()
