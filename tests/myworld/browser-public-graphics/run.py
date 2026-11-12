#!/usr/bin/env python3
"""Full-JAR public/private mode, persistence, menu, shader and catalogue-tuple checks."""
import argparse,hashlib,json,pathlib,subprocess
p=argparse.ArgumentParser()
for key in ('root','jar','receipt','catalogue','out'):p.add_argument('--'+key,type=pathlib.Path,required=True)
a=p.parse_args();root=a.root.resolve();assert not a.out.exists();a.out.mkdir(parents=True);out=a.out.resolve()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
receipt=json.loads(a.receipt.read_text());assert receipt['singleProductionCommit']and receipt['cleanCheckoutAtBuild']and receipt['buildSucceeded']and not receipt['classesTransplanted']and sha(a.jar)==receipt['jarSha256']
manifest=json.loads(a.receipt.with_name('source-manifest.json').read_text());assert all(sha(root/key)==value for key,value in manifest.items()),'Production source differs from frozen JAR'
classes=out/'classes';classes.mkdir();fixture=root/'tests/myworld/browser-public-graphics/PublicGraphicsFixture.java';commands=[];passed=[]
def run(name,cmd):
 commands.append(cmd);r=subprocess.run(cmd,cwd=root,text=True,capture_output=True,timeout=90);(out/(name+'.log')).write_text(r.stdout+r.stderr);assert r.returncode==0,name+' failed';passed.append(name)
try:
 run('compile',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes),str(fixture)])
 for mode in ('public','private'):
  flags=['-Dspoiledmilk.browserPublicAlpha=true']if mode=='public'else[]
  run(mode,['java','-Djava.awt.headless=true']+flags+['-cp',str(classes)+':'+str(a.jar.resolve()),'orsc.PublicGraphicsFixture',str(a.catalogue.resolve())])
finally:
 (out/'result.json').write_text(json.dumps(dict(passed=len(passed)==3,cases=passed,receipt=receipt,commands=commands,fixtureSha256=sha(fixture),catalogueSha256=sha(a.catalogue),limits='The old catalogue is inspected only for its accepted settings tuple, never rebound/executed with this JAR. Actual production packing/menu/uniform and persistence APIs execute on the complete fresh source JAR. No GL/browser/server or FPS claim.'),indent=2)+'\n')
print(json.dumps(dict(passed=True,output=str(out))))
