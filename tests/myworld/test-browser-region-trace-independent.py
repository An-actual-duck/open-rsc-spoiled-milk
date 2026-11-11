#!/usr/bin/env python3
"""Frozen bridge raw-byte/cache/event parity; recording native boundary, no GPU/server."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[2]
BASELINE='7934b77c277cdc88b5a29c6815159b8f779c69fa'
BRIDGE='PC_Client/src/orsc/BrowserWebGLBridge.java'
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',type=Path,required=True);p.add_argument('--candidate-commit',required=True);p.add_argument('--dependency-jar',type=Path,required=True);p.add_argument('--support',action='append',default=[]);a=p.parse_args()
 if not re.fullmatch('[0-9a-f]{40}',a.candidate_commit):p.error('Exact candidate commit required')
 jar=a.dependency_jar.resolve();base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-region-trace-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();fixture=ROOT/'tests/myworld/BrowserRegionTraceIndependentFixture.java';frozen=run/fixture.name;frozen.write_bytes(fixture.read_bytes());hashes={};runs={}
 for label,commit,profile in [('frozen',BASELINE,False),('off',a.candidate_commit,False),('on',a.candidate_commit,True)]:
  folder=run/label;folder.mkdir();classes=folder/'classes';classes.mkdir();sources=[];hashes[label]={}
  for path in [BRIDGE]+([] if label=='frozen' else a.support):
   data=subprocess.check_output(['git','show',commit+':'+path],cwd=a.candidate_root);dest=folder/Path(path).name;dest.write_bytes(data);sources.append(str(dest));hashes[label][path]=sha(data)
  cmd=['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*sources,str(frozen)];r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(folder/'compile.stdout').write_bytes(r.stdout);(folder/'compile.stderr').write_bytes(r.stderr);r.check_returncode()
  cmd=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserLoadProfile='+str(profile).lower(),'-Dfixture.enableProfile='+str(profile).lower(),'-cp',str(classes)+':'+str(jar),'orsc.BrowserRegionTraceIndependentFixture'];r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(folder/'stdout.log').write_bytes(r.stdout);(folder/'stderr.log').write_bytes(r.stderr);r.check_returncode();runs[label]=dict(command=cmd,stdout=r.stdout.decode(),stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr));print(label,r.stdout.decode().strip())
 traces={k:v['stdout'].split('TRACE ',1)[1].splitlines()[0] for k,v in runs.items()};passed=len(set(traces.values()))==1
 receipt=dict(passed=passed,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,baselineCommit=BASELINE,candidateCommit=a.candidate_commit,dependencyJar=str(jar),dependencyJarSha256=sha(jar.read_bytes()),sourceSha256=hashes,fixtureSha256=sha(frozen.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),runs=runs)
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('Receipt:',run/'result.json');assert passed,'Frozen raw-byte/cache/event traces differ'
if __name__=='__main__':main()
