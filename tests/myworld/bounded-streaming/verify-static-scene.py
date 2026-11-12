#!/usr/bin/env python3
"""Compile fixture-only classes against one complete, receipt-bound production JAR."""
import argparse, hashlib, json, pathlib, subprocess, zipfile
p=argparse.ArgumentParser(description=__doc__)
for n in ('jar','catalogue','package','cache','out'):p.add_argument('--'+n,type=pathlib.Path,required=True)
p.add_argument('--halo',action='store_true',help='Run original-versus-cached halo canonical identity oracle')
a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
receipt=json.loads((a.jar.parent/'receipt.json').read_text())
assert receipt['singleProductionCommit'] and receipt['cleanCheckoutAtBuild'] and receipt['classesTransplanted'] is False and receipt['buildSucceeded']
assert sha(a.jar)==receipt['jarSha256']
for name,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:assert sha(a.jar.parent/name)==receipt[key]
assert not a.out.exists(),'Preserve prior evidence'
a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir()
sources=[pathlib.Path(__file__).with_name('StaticSceneReuseOracle.java'),pathlib.Path(__file__).parents[1]/'whole-zone/WholeZoneDynamicFixture.java']
if a.halo:sources.append(pathlib.Path(__file__).with_name('HaloIdentityOracle.java'))
compile_command=['javac','-source','8','-target','8','-encoding','UTF-8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve())]+[str(s.resolve())for s in sources]
run_command=['java','-Xmx256m','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserWholeZone=true','-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.HaloIdentityOracle'if a.halo else'orsc.StaticSceneReuseOracle']+[str(v.resolve())for v in (a.catalogue,a.package,a.cache)]
result={}
try:
 for name,command in [('javac',compile_command),('java',run_command)]:
  r=subprocess.run(command,text=True,capture_output=True,timeout=45);(a.out/(name+'.log')).write_text(r.stdout+r.stderr);assert r.returncode==0,name+' failed; inspect preserved log'
  if name=='java':result=json.loads(r.stdout.strip().splitlines()[-1]);assert result['passed'] and result['checks']>=100 and result['frames']>=40
finally:
 compiled={v.relative_to(classes).as_posix():sha(v)for v in classes.rglob('*.class')}
 assert all(v.startswith('orsc/StaticSceneReuseOracle')or v.startswith('orsc/WholeZoneDynamicFixture')or v.startswith('orsc/HaloIdentityOracle')for v in compiled),'Production classes must never be overlaid'
 (a.out/'result.json').write_text(json.dumps({'passed':result.get('passed',False),'productionReceipt':receipt,'catalogueSha256':sha(a.catalogue),'fixtureSources':{str(s):sha(s)for s in sources},'executedFixtureClasses':compiled,'commands':[compile_command,run_command],'result':result},indent=2)+'\n')
print(json.dumps({'passed':True,'checks':result['checks'],'frames':result['frames'],'output':str(a.out)}))
