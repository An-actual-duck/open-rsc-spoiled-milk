#!/usr/bin/env python3
"""Compile only fixtures against the full maintained client; compare actual baseline output."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
jar=a.jar.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);classes=out/'classes';classes.mkdir()
def run(args,label):
 r=subprocess.run(args,cwd=ROOT,text=True,capture_output=True);(out/(label+'.stdout')).write_text(r.stdout);(out/(label+'.stderr')).write_text(r.stderr);r.check_returncode();print(label,r.stdout.strip());return r.stdout
fixture=ROOT/'tests/myworld/browser-baseline-reuse/BaselineReuseFixture.java'
run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(fixture)],'compile')
traces={};runs={}
for label,benchmark,reuse in [('off',True,False),('on',True,True),('ordinary',False,True)]:
 props={'java.awt.headless':'true','spoiledmilk.browserBenchmark':str(benchmark).lower(),'spoiledmilk.browserWholeZone':'true','spoiledmilk.browserWholeRam':'true','spoiledmilk.browserStaticSceneReuse':'true','spoiledmilk.browserBaselineReuse':str(reuse).lower()}
 args=['java',*[f'-D{k}={v}'for k,v in props.items()],'-cp',str(classes)+':'+str(jar),'orsc.BaselineReuseFixture',str(ROOT)]
 text=run(args,label);traces[label]=next(x for x in text.splitlines()if x.startswith('TRACE '));runs[label]=args
assert len(set(traces.values()))==1,'ON/OFF/default output differs'
run(['node','--experimental-default-type=module','tests/myworld/browser-baseline-reuse/policy.mjs'],'policy')
files=['Client_Base/src/orsc/BrowserBaselineObjectReuse.java','Client_Base/src/orsc/PacketHandler.java','Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/mudclient.java',str(fixture.relative_to(ROOT))]
result={'passed':True,'fullJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'fullJar':str(jar),'productionClassOverlay':False,'testOnlyClassesCompiled':True,'browserStarted':False,'gpuExecuted':False,'serverStarted':False,'traces':traces,'runs':runs,'sourceSha256':{f:hashlib.sha256((ROOT/f).read_bytes()).hexdigest()for f in files}}
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
