#!/usr/bin/env python3
"""Independent full-JAR typed writer proof and optional synthetic packing comparison."""
import argparse, hashlib, json, os, re, subprocess, time, statistics
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline-jar',type=Path,required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--java21',type=Path);p.add_argument('--gpu',action='store_true');p.add_argument('--benchmark',action='store_true');a=p.parse_args()
    jars={'baseline':a.baseline_jar.resolve(strict=True),'candidate':a.client_jar.resolve(strict=True)}
    base=Path(subprocess.check_output(['python3','scripts/workspace-layout.py','path','tests','native-typed-writer','--mkdir'],cwd=ROOT,text=True).strip());out=base/str(time.time_ns());out.mkdir();classes=out/'classes';classes.mkdir();results=[];hashes={};timings=[]
    sources=[ROOT/'tests/myworld'/(n+'.java') for n in ['NativeTypedWriterOracle','NativeTypedWriterBoundaryFixture','NativeTypedCacheRoundTripFixture','NativeCompactCacheGpuFixture','NativeCompactGeometryFixture']]
    def run(label,cmd):
        with (out/(label+'.log')).open('w') as log:r=subprocess.run(list(map(str,cmd)),cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
        results.append(dict(label=label,command=list(map(str,cmd)),exitCode=r.returncode));r.check_returncode();return (out/(label+'.log')).read_text()
    try:
        for name,jar in jars.items():
            receipt=json.loads((jar.parent/'receipt.json').read_text());assert receipt['jarSha256']==sha(jar),'Full JAR receipt mismatch';hashes[name]=dict(jar=str(jar),sha256=sha(jar),sourceCommit=receipt.get('sourceCommit',receipt.get('commit')),receiptSha256=sha(jar.parent/'receipt.json'))
        run('compile',['javac','-source','8','-target','8','-cp',jars['candidate'],'-d',classes,*sources])
        javas=[('java8','java')]+([] if a.java21 is None else [('java21',str(a.java21))]);digests={}
        variants={'default':{},'off':{'nativeCompactGeometry':False,'nativeCompactTypedWriter':False},'typed-only':{'nativeCompactTypedWriter':True},'roundtrip':{'nativeCompactGeometry':True,'nativeCompactTypedWriter':False},'typed':{'nativeCompactGeometry':True,'nativeCompactTypedWriter':True},'browserBenchmark':{'browserBenchmark':True,'nativeCompactGeometry':True,'nativeCompactTypedWriter':True},'browserPublicAlpha':{'browserPublicAlpha':True,'nativeCompactGeometry':True,'nativeCompactTypedWriter':True}}
        for jlabel,java in javas:
            for version,jar in jars.items():
                for mode,flags in variants.items():
                    label=jlabel+'-'+version+'-'+mode;cmd=[java,'-Xmx1g',*[f'-Dspoiledmilk.{k}={str(v).lower()}' for k,v in flags.items()],'-cp',str(classes)+os.pathsep+str(jar),'orsc.NativeTypedWriterOracle'];log=run(label,cmd);match=re.search(r'native=(\w+) backend=(\w+) cases=(\d+) compact=(\d+)',log);assert match,label;digests[label]=match.groups();assert match[3]=='3072';assert int(match[4])==(768 if mode in ['roundtrip','typed'] else 0)
            for mode in variants:
                assert digests[jlabel+'-baseline-'+mode][:2]==digests[jlabel+'-candidate-'+mode][:2],f'Independent full-JAR parity failed: {jlabel}/{mode}'
            for mode in ['off','typed-only','roundtrip','typed','browserBenchmark']:
                assert digests[jlabel+'-candidate-default'][:2]==digests[jlabel+'-candidate-'+mode][:2],mode
            for style in ['raw','projected','baked']:
                oracle=None
                for version,jar in jars.items():
                    for mode in ['default','typed']:
                        flags=variants[mode];label=f'{jlabel}-{version}-{style}-{mode}'
                        log=run(label,[java,'-Xmx1g',f'-Dfixture.{style}=true',*[f'-Dspoiledmilk.{k}={str(v).lower()}' for k,v in flags.items()],'-cp',str(classes)+os.pathsep+str(jar),'orsc.NativeTypedWriterOracle'])
                        match=re.search(r'native=(\w+) backend=(\w+) cases=(\d+) compact=(\d+)',log);assert match and match[4]=='0',label
                        if oracle is None:oracle=match.groups()[:2]
                        else:assert match.groups()[:2]==oracle,'Independent unsupported-path parity: '+label
                        digests[label]=match.groups()
            run(jlabel+'-boundaries',[java,'-cp',str(classes)+os.pathsep+str(jars['candidate']),'orsc.NativeTypedWriterBoundaryFixture'])
            run(jlabel+'-actual-typed',[java,'-Dspoiledmilk.nativeCompactTypedWriter=true','-cp',str(classes)+os.pathsep+str(jars['candidate']),'orsc.NativeCompactGeometryFixture'])
        if a.gpu:
            common=(ROOT/subprocess.check_output(['git','rev-parse','--git-common-dir'],cwd=ROOT,text=True).strip()).resolve();lock=common/'ai-workspaces/renderer-gpu-benchmark.lock'
            run('production-gpu',['flock',lock,'java','-Xmx1g','-cp',str(classes)+os.pathsep+str(jars['candidate']),'orsc.NativeTypedCacheRoundTripFixture'])
            run('typed-cache-matrix',['flock',lock,'java','-Dspoiledmilk.nativeCompactTypedWriter=true','-cp',str(classes)+os.pathsep+str(jars['candidate']),'orsc.NativeCompactCacheGpuFixture'])
        if a.benchmark:
            for jlabel,java in javas:
                for repeat in range(3):
                    modes=['baseline','off','roundtrip','typed'];modes=modes[repeat:]+modes[:repeat]
                    for mode in modes:
                        jar=jars['baseline' if mode=='baseline' else 'candidate'];flags=variants.get(mode,{})
                        label=f'bench-{jlabel}-{repeat}-{mode}';log=run(label,[java,'-Xmx1g',*[f'-Dspoiledmilk.{k}={str(v).lower()}' for k,v in flags.items()],'-cp',str(classes)+os.pathsep+str(jar),'orsc.NativeTypedWriterOracle','benchmark']);rows=[list(map(int,line.split(',')[1:])) for line in log.splitlines() if line.startswith('pack,')];assert len(rows)==32
                        timings.append(dict(java=jlabel,repeat=repeat,mode=mode,vertices=rows[0][1],medianNanos=statistics.median(r[2] for r in rows),medianAllocatedBytes=statistics.median(r[3] for r in rows),rows=rows))
        (out/'proof.json').write_text(json.dumps(dict(digests=digests,timings=timings,passed=True),indent=2)+'\n')
    finally:
        (out/'receipt.json').write_text(json.dumps(dict(jars=hashes,fixtureHashes={str(s.relative_to(ROOT)):sha(s) for s in sources},results=results,scope='Full independent JARs, no production class substitution. Synthetic packing/allocation does not establish game FPS or transition latency.'),indent=2)+'\n')
    print('PASS independent typed writer:',out)
if __name__=='__main__':main()
