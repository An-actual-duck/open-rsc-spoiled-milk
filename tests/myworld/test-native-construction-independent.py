#!/usr/bin/env python3
"""Exact full-JAR baseline/native 2x2/browser-isolation construction proof."""
import argparse, hashlib, json, subprocess
from pathlib import Path
from datetime import datetime, timezone
ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ['BrowserConstructionIndependentFixture', 'BrowserSignatureIndependentFixture', 'BrowserRegionBuildEdgeIndependentFixture']
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--baseline-jar',type=Path,required=True);p.add_argument('--candidate-jar',type=Path,required=True)
    p.add_argument('--candidate-commit',required=True);a=p.parse_args()
    base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','native-construction-independent','--mkdir'],text=True).strip())
    run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
    results={}; jars={'baseline':a.baseline_jar.resolve(),'candidate':a.candidate_jar.resolve()}
    for version,jar in jars.items():
        folder=run/version;folder.mkdir();classes=folder/'classes';classes.mkdir()
        names=FIXTURES+(['NativeConstructionPolicyFixture'] if version=='candidate' else [])
        sources=[]
        for name in names:
            target=folder/(name+'.java');target.write_bytes((ROOT/'tests/myworld'/(name+'.java')).read_bytes());sources.append(str(target))
        subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*sources],check=True)
        modes=[('native00',False,False,False,False,False)]
        if version=='candidate':
            modes += [(f'native{i}{j}',False,False,False,bool(i),bool(j)) for i,j in [(1,0),(0,1),(1,1)]]
            modes += [(f'browser{r}-native{i}{j}',True,False,bool(r),bool(i),bool(j)) for r in [0,1] for i,j in [(0,0),(1,1)]]
            modes += [('public-browser-native11',False,True,False,True,True)]
        for label,browser,public,region,index,primitive in modes:
            key=version+'/'+label;results[key]={}
            props={'spoiledmilk.browserBenchmark':browser,'spoiledmilk.browserPublicAlpha':public,'spoiledmilk.browserRegionBuild':region,'spoiledmilk.nativeConstructionVertexIndex':index,'spoiledmilk.nativePrimitiveWorldBuild':primitive,'fixture.testConstructionHelpers':version=='candidate','fixture.testLimb':region}
            for name in names:
                output=folder/(label+'-'+name+'.bin')
                cmd=['java','-Xmx768m','-Djava.awt.headless=true',*[f'-D{k}={str(v).lower()}' for k,v in props.items()],'-cp',str(classes)+':'+str(jar),'orsc.graphics.three.'+name,str(output)]
                r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                (folder/(label+'-'+name+'.log')).write_bytes(r.stdout+r.stderr);r.check_returncode()
                results[key][name]={'sha256':sha(output) if output.exists() else None,'stdout':r.stdout.decode(),'command':cmd}
            print(key,'PASS',flush=True)
    for name in FIXTURES:
        assert len({v[name]['sha256'] for v in results.values()})==1, 'Raw byte mismatch: '+name
    receipt={'passed':True,'candidateCommit':a.candidate_commit,'jarHashes':{k:sha(v) for k,v in jars.items()},'fixtureHashes':{n:sha(ROOT/'tests/myworld'/(n+'.java')) for n in FIXTURES+['NativeConstructionPolicyFixture']},'results':results,'gpuExecuted':False}
    (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(run/'result.json')
if __name__=='__main__': main()
