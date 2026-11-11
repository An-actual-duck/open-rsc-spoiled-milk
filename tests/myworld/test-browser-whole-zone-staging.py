#!/usr/bin/env python3
"""CLI refusal gates before bake/stage creation; no Java/game/server/browser/GPU."""
import hashlib,json,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-whole-zone-staging','--mkdir'],text=True).strip());run=base/('negative-'+__import__('datetime').datetime.now().strftime('%Y%m%dT%H%M%S%f'));run.mkdir();jar=run/'synthetic-not-executable.jar'
 with zipfile.ZipFile(jar,'w')as z:z.writestr('orsc/BrowserWholeZoneBaker.class',b'Not executable; no JVM invocation is permitted by these tests')
 sources=run/'source-manifest.json';sources.write_text(json.dumps({'Client_Base/src/orsc/graphics/three/World.java':'f'*64}))
 build={'jarSha256':sha(jar),'buildSucceeded':True,'singleProductionCommit':True,'cleanCheckoutAtBuild':True,'clientSourceCommit':'0'*40,'sourceManifestSha256':sha(sources)};receipt=run/'receipt.json';receipt.write_text(json.dumps(build));bake=run/'bake';(bake/'whole-zone').mkdir(parents=True)
 m={'complete':False,'preparedSectors':3,'preparedPlacements':202,'clientJarSha256':sha(jar)};manifest=bake/'whole-zone/manifest.json';manifest.write_text(json.dumps(m));br={'passed':True,'complete':False,'fullRequested':False,'requestedSectorLimit':3,'clientJarSha256':sha(jar),'manifestSha256':sha(manifest)};(bake/'bake-receipt.json').write_text(json.dumps(br))
 name='whole-zone-negative-'+run.name;stage_root=ROOT/'output/tests/browser-gameplay-spike'/name
 checks=[]
 def reject(label,command,expected):
  result=subprocess.run(command,cwd=ROOT,text=True,capture_output=True,timeout=30);(run/(label+'.stdout')).write_text(result.stdout);(run/(label+'.stderr')).write_text(result.stderr)
  if result.returncode==0 or expected not in result.stderr:raise AssertionError((label,result.returncode,result.stderr))
  if stage_root.exists():raise AssertionError('Rejected input created a browser stage')
  checks.append({'label':label,'exit':result.returncode,'command':command,'stderrSha256':sha(run/(label+'.stderr'))})
 stage=['python3',str(ROOT/'scripts/browser-gameplay/prepare-whole-zone-client.py'),'--name',name,'--http-port','8299','--server-run',str(run/'NO-SERVER'),'--client-jar',str(jar),'--client-receipt',str(receipt),'--bake-run',str(bake)]
 reject('incomplete-pilot',stage,'Complete source-bound1330-sector')
 full=['python3',str(ROOT/'tools/browser-gameplay/whole-zone/bake.py'),'--name',name,'--client-jar',str(jar),'--client-receipt',str(receipt),'--limit-sectors','1330','--full']
 reject('nonintegrated-full-bake',full,'manager-only')
 pilot=full[:-3]+['--limit-sectors','4']
 reject('mismatched-build-source',pilot,'source files differ')
 wrong=dict(build,jarSha256='0'*64);receipt.write_text(json.dumps(wrong));reject('artifact-mismatch',stage,'Exact clean single-source client artifact')
 report={'passed':True,'checks':len(checks),'processesStarted':'Python refusal checks only; no Java, server, browser, GPU or network service','scope':'Synthetic non-executable artifact proves early refusal, not valid artifact/full-data acceptance','sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'testSha256':sha(Path(__file__)),'scripts':{str(p.relative_to(ROOT)):sha(p)for p in [ROOT/'tools/browser-gameplay/whole-zone/bake.py',ROOT/'scripts/browser-gameplay/prepare-whole-zone-client.py']},'results':checks};(run/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({'passed':True,'checks':len(checks),'run':str(run)}))
if __name__=='__main__':main()
