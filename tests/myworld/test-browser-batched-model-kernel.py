#!/usr/bin/env python3
"""Frozen real Scene/model batch differential. No browser/server/GPU/FPS claim."""
import argparse,datetime,hashlib,json,pathlib,re,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
BASE='8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline-jar',required=True,type=pathlib.Path);a=p.parse_args();jar=a.baseline_jar.resolve()
    category=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-batched-model-kernel','--mkdir'],text=True).strip());run=category/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();old=run/'baseline';current=run/'current';old.mkdir();current.mkdir()
    def execute(name,argv):
        with(run/(name+'.log')).open('w')as log:subprocess.run(argv,cwd=ROOT,check=True,stdout=log,stderr=subprocess.STDOUT)
    model='Client_Base/src/orsc/graphics/three/RSModel.java';scene='Client_Base/src/orsc/graphics/three/Scene.java';frozen=[]
    for relative in [model,scene]:
        out=old/pathlib.Path(relative).name;out.write_bytes(subprocess.check_output(['git','show',BASE+':'+relative],cwd=ROOT));frozen.append(out)
    def method(data):
        start=data.index('final void rotate1024(');opening=data.index('{',start);depth=0
        for n in range(opening,len(data)):
            if data[n]=='{':depth+=1
            elif data[n]=='}':
                depth-=1
                if depth==0:return data[start:n+1]
        raise AssertionError('No method end')
    assert method((ROOT/model).read_text())==method((old/'RSModel.java').read_text()),'Literal RSModel rotation changed'
    fixtures=[ROOT/'tests/myworld'/n for n in ['BrowserOwnershipGraphics.java','ClericWellSpotClientTest.java','BrowserWallRoofOwnershipFixture.java','NativeModelKernelReferenceFixture.java','BrowserBatchedModelKernelFixture.java']]
    sources=[ROOT/model,ROOT/scene,ROOT/'Client_Base/src/orsc/graphics/three/BrowserBatchedModelKernel.java',*fixtures]
    execute('compile-baseline',['javac','-source','8','-target','8','-cp',str(jar),'-d',str(old),*map(str,frozen)])
    execute('compile-current',['javac','-source','8','-target','8','-cp',str(jar),'-d',str(current),*map(str,sources)])
    outputs={}
    for name,cp,mode,flags in [
        ('scene-baseline',str(old)+':'+str(current)+':'+str(jar),'scene',['baseline','on']),
        ('scene-off',str(current)+':'+str(jar),'scene',['current','off']),
        ('scene-on',str(current)+':'+str(jar),'scene',['current','on']),
        ('scene-rejected',str(current)+':'+str(jar),'scene',['current','on','reject']),
        ('edge-baseline',str(old)+':'+str(current)+':'+str(jar),'edges',['baseline']),
        ('edge-on',str(current)+':'+str(jar),'edges',['current']),
        ('edge-rejected',str(current)+':'+str(jar),'edges',['current','reject'])]:
        execute(name,['java','-Xmx768m','-Djava.awt.headless=true','-cp',cp,'orsc.graphics.three.BrowserBatchedModelKernelFixture',mode,*flags]);text=(run/(name+'.log')).read_text();outputs[name]=re.search(r'(?:SCENE|EDGE)_DIGEST=([0-9a-f]{64})',text).group(1)
    assert len({outputs[n]for n in outputs if n.startswith('scene-')})==1,outputs
    assert len({outputs[n]for n in outputs if n.startswith('edge-')})==1,outputs
    execute('failure-cleanup',['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(current)+':'+str(jar),'orsc.graphics.three.BrowserBatchedModelKernelFixture','cleanup'])
    for kind,flags in [('random',[]),('batch',['batch'])]:
        execute('reference-'+kind,['java','-Xmx768m','-Djava.awt.headless=true','-cp',str(old)+':'+str(current)+':'+str(jar),'orsc.graphics.three.NativeModelKernelReferenceFixture',str(run/('reference-'+kind+'.bin')),'6000',*flags])
        execute('js-'+kind,['node','--experimental-default-type=module',str(ROOT/'tests/myworld/test-browser-batched-model-kernel.mjs'),str(ROOT/'tools/browser-gameplay/web/browser-batched-model-kernel.js'),str(run/('reference-'+kind+'.bin'))])
    result={'passed':True,'cpuOnly':True,'baselineSource':BASE,'baselineJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'outputs':outputs,'references':{kind:hashlib.sha256((run/('reference-'+kind+'.bin')).read_bytes()).hexdigest()for kind in ['random','batch']},'sourceSha256':{str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest()for x in sources},'scope':'Frozen actual Scene/model parity, full group/metadata/native gate/proxy ordering, 13 exception/alias/trig/near cases, rejected-packet replay and finally reference cleanup. Actual JNI and FPS remain manager acceptance.'}
    (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'run':str(run),**result},indent=2))
if __name__=='__main__':main()
