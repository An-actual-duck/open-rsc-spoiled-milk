#!/usr/bin/env python3
"""Independent CPU-only review against a clean exact UI checkpoint and full JAR."""
import argparse,hashlib,json,pathlib,subprocess,zipfile
p=argparse.ArgumentParser()
for name in ('source-root','commit','jar','receipt','out'):
    p.add_argument('--'+name,required=True,type=str if name=='commit' else pathlib.Path)
a=p.parse_args()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def git(*args):return subprocess.check_output(['git','-C',str(a.source_root),*args],text=True)
assert git('rev-parse','HEAD').strip()==a.commit
assert not git('status','--porcelain').strip(),'Candidate must be preserved and clean'
receipt=json.loads(a.receipt.read_text());assert receipt['singleProductionCommit'] and receipt['cleanCheckoutAtBuild'] and receipt['buildSucceeded'] and not receipt['classesTransplanted']
assert sha(a.jar)==receipt['jarSha256']
manifest=json.loads(a.receipt.with_name('source-manifest.json').read_text())
assert all(sha(a.source_root/path)==value for path,value in manifest.items()),'JAR source must still match exact checkpoint'
with zipfile.ZipFile(a.jar) as z:
    classes=json.loads(a.receipt.with_name('compiled-classes.json').read_text())
    assert all(hashlib.sha256(z.read(path)).hexdigest()==value for path,value in classes.items())
assert not a.out.exists(),'Preserve previous evidence'
a.out.mkdir(parents=True);commands=[];results=[]
def run(name,cmd):
    commands.append(cmd);r=subprocess.run(cmd,text=True,capture_output=True,timeout=120)
    (a.out/(name+'.log')).write_text(r.stdout+r.stderr)
    assert r.returncode==0,name+' failed; inspect preserved log'
    results.append(name)
try:
    tests=a.source_root/'tests/myworld'
    run('display-generation',['node',str(pathlib.Path(__file__).with_name('display-generation.mjs').resolve()),str(a.source_root.resolve())])
    run('display',['node',str(tests/'test-browser-display.mjs')])
    run('session',['node','--experimental-vm-modules',str(tests/'test-browser-session-stop-receipt.mjs')])
    run('world-ready',['node',str(tests/'test-browser-world-ready.mjs')])
    run('menu-source-scope',['python3',str(tests/'browser-alpha-interface/verify-scope.py'),'--source-root',str(a.source_root),'--commit',a.commit])
    classes=a.out/'classes';classes.mkdir()
    run('fixture-compile',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve()),str(tests/'BrowserControlsSettingsFixture.java')])
    run('full-jar-controls',['java','-Djava.awt.headless=true','-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.BrowserControlsSettingsFixture'])
finally:
    (a.out/'result.json').write_text(json.dumps(dict(passed=len(results)==7,sourceCommit=a.commit,receipt=receipt,runnerSha256=sha(pathlib.Path(__file__)),independentFixtureSha256=sha(pathlib.Path(__file__).with_name('display-generation.mjs')),commands=commands,passedCases=results,limits='Offline only. Full canonical JAR plus test fixture classes; no production class overlay. Node dependency/event models are not browser hardware/real-user-activation proof. No browser/GPU/listener/server launched.'),indent=2)+'\n')
print(json.dumps(dict(passed=True,cases=len(results),output=str(a.out))))
