#!/usr/bin/env python3
"""Source-frozen construction raw arrays/signatures and literal-long FNV arithmetic proof."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[2];BASELINE='7934b77c277cdc88b5a29c6815159b8f779c69fa'
SOURCES=['Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/graphics/three/World.java']
FIXTURES=['BrowserConstructionIndependentFixture.java','BrowserSignatureIndependentFixture.java']
def sha(x):return hashlib.sha256(x).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);p.add_argument('--dependency-jar',required=True,type=Path);p.add_argument('--support',action='append',default=[]);p.add_argument('--skip-limb',action='store_true');a=p.parse_args();assert re.fullmatch('[a-f0-9]{40}',a.candidate_commit)
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-construction-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();jar=a.dependency_jar.resolve();fixtureHashes={}
 for name in FIXTURES:
  b=(ROOT/'tests/myworld'/name).read_bytes();(run/name).write_bytes(b);fixtureHashes[name]=sha(b)
 results={};hashes={}
 for label,commit,on in [('frozen',BASELINE,False),('off',a.candidate_commit,False),('on',a.candidate_commit,True)]:
  folder=run/label;folder.mkdir();classes=folder/'classes';classes.mkdir();sources=[];hashes[label]={}
  for path in SOURCES+([]if label=='frozen'else a.support):
   b=subprocess.check_output(['git','show',commit+':'+path],cwd=a.candidate_root);f=folder/Path(path).name;f.write_bytes(b);sources.append(str(f));hashes[label][path]=sha(b)
  cmd=['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*sources,*[str(run/n)for n in FIXTURES]];r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(folder/'compile.stdout').write_bytes(r.stdout);(folder/'compile.stderr').write_bytes(r.stderr);r.check_returncode();results[label]={}
  for fixture in FIXTURES:
   name=fixture[:-5];output=folder/(name+'.bin');cmd=['java','-Xmx768m','-XX:-OmitStackTraceInFastThrow','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild='+str(on).lower(),'-Dfixture.testLimb='+str(on and not a.skip_limb).lower(),'-cp',str(classes)+':'+str(jar),'orsc.graphics.three.'+name,str(output)];r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(folder/(name+'.stdout')).write_bytes(r.stdout);(folder/(name+'.stderr')).write_bytes(r.stderr);r.check_returncode();data=output.read_bytes();results[label][name]=dict(command=cmd,bytes=len(data),sha256=sha(data),stdout=r.stdout.decode(),stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr));print(label,name,r.stdout.decode().strip())
 passed=all(len({results[label][name[:-5]]['sha256']for label in results})==1 for name in FIXTURES);receipt=dict(passed=passed,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,baselineCommit=BASELINE,candidateCommit=a.candidate_commit,dependencyJar=str(jar),dependencyJarSha256=sha(jar.read_bytes()),fixtureSha256=fixtureHashes,runnerSha256=sha(Path(__file__).read_bytes()),sourceSha256=hashes,results=results,limbExecuted=not a.skip_limb);(run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('Receipt:',run/'result.json');assert passed,'Frozen construction/signature byte mismatch'
if __name__=='__main__':main()
