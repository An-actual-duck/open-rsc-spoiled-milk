#!/usr/bin/env python3
"""Full-JAR native compact parity; optional locked real-GPU proofs, no game server."""
import argparse,hashlib,json,os,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--gpu',action='store_true');p.add_argument('--java21',type=Path);a=p.parse_args();jar=a.client_jar.resolve(strict=True)
    base=Path(subprocess.check_output(['python3','scripts/workspace-layout.py','path','tests','native-compact-geometry','--mkdir'],cwd=ROOT,text=True).strip());out=base/str(time.time_ns());out.mkdir();classes=out/'classes';classes.mkdir()
    names=['NativeCompactGeometryFixture','NativeMaterialBatchReuseFixture','NativeCompactGpuFixture','NativeCompactCacheGpuFixture','GeometryShadowGpuFixture','GeometryShadowRoofMaskFixture'];sources=[ROOT/'tests/myworld'/(n+'.java') for n in names];results=[]
    def run(label,command,env=None):
        with (out/(label+'.log')).open('w') as log:r=subprocess.run(list(map(str,command)),cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,env=env)
        results.append(dict(label=label,command=list(map(str,command)),exitCode=r.returncode));r.check_returncode()
    try:
        run('compile',['javac','-source','8','-target','8','-cp',jar,'-d',classes,*sources])
        for label,java in [('java8','java')]+([] if a.java21 is None else [('java21',a.java21)]):
            for name in names[:2]:run(label+'-'+name,[java,'-Xmx1g','-cp',str(classes)+os.pathsep+str(jar),'orsc.'+name])
        if a.gpu:
            common=Path(subprocess.check_output(['git','rev-parse','--git-common-dir'],cwd=ROOT,text=True).strip());common=(ROOT/common).resolve();lock=common/'ai-workspaces/renderer-gpu-benchmark.lock'
            for name in names[2:4]:run(name,['flock',lock,'java','-cp',str(classes)+os.pathsep+str(jar),'orsc.'+name])
            for compact in ['false','true']:run('shadow-'+compact,['flock',lock,'java','-Dspoiledmilk.nativeCompactGeometry='+compact,'-cp',str(classes)+os.pathsep+str(jar),'orsc.GeometryShadowGpuFixture',out/('shadow-'+compact)])
            hashes=lambda d:{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in d.glob('*.png')}
            first,second=hashes(out/'shadow-false'),hashes(out/'shadow-true');assert len(first)>=24 and first==second,'Shadow image parity'
            (out/'shadow-parity.json').write_text(json.dumps(dict(exact=True,count=len(first),hashes=first),indent=2))
            run('unsupported',['flock',lock,'java','-cp',str(classes)+os.pathsep+str(jar),'orsc.GeometryShadowGpuFixture',out/'unsupported','unsupported'],dict(os.environ,MESA_GL_VERSION_OVERRIDE='2.1'))
    finally:
        (out/'receipt.json').write_text(json.dumps(dict(clientJar=str(jar),jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),fixtureHashes={str(s.relative_to(ROOT)):hashlib.sha256(s.read_bytes()).hexdigest() for s in sources},results=results,scope='Fixtures only are compiled; all production classes come from the full client JAR. GPU output/readback proofs are not performance measurements.'),indent=2))
    print('PASS native compact full-JAR CPU'+('/GPU' if a.gpu else '')+' proof:',out)
if __name__=='__main__':main()
