#!/usr/bin/env python3
"""Source-bound startup policy/initialization proof; no browser/server/GPU."""
from pathlib import Path
from datetime import datetime,timezone
import subprocess,hashlib,json,argparse
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args()
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-optimized-render-defaults','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
def call(name,cmd):
 x=subprocess.run(cmd,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/(name+'.stdout')).write_bytes(x.stdout);(run/(name+'.stderr')).write_bytes(x.stderr);x.check_returncode();return x.stdout.decode()
folders=[]
for variant in ['new','old','wrong']:
 folder=run/variant;folder.mkdir();folders.append(folder);files=[]
 for owner,sentinel in [('orsc.graphics.three.Scene','scene'),('orsc.graphics.three.BrowserBatchedModelKernel','kernel'),('orsc.NativeLayeredTerrainSnapshot','terrain'),('orsc.NativeLayeredTerrainChunk','chunk')]:
  package,cls=owner.rsplit('.',1);method=''
  if variant=='new':method={'scene':'public boolean isBrowserBatchedModelKernelEnabled(){return true;}','kernel':'private static native boolean transformPacket(int n,int[][]r,int[]a,int[]b,int[]c,int[]d);','terrain':'public static boolean isBrowserTerrainQueryIndexEnabled(){return initializedValue;}','chunk':'public int getSize(){return 48;}'}[sentinel]
  if variant=='wrong':method={'scene':'public static boolean isBrowserBatchedModelKernelEnabled(){return true;}','kernel':'private static boolean transformPacket(int n,int[][]r,int[]a,int[]b,int[]c,int[]d){return true;}','terrain':'public boolean isBrowserTerrainQueryIndexEnabled(){return true;}','chunk':'public static int getSize(){return 48;}'}[sentinel]
  f=folder/(cls+'.java');f.write_text('package '+package+'; public final class '+cls+' { public static boolean initializedValue=Boolean.getBoolean("spoiledmilk.browserTerrainQueryIndex"); static {System.setProperty("sentinel.'+sentinel+'","yes");} '+method+' }');files.append(f)
 call('compile-'+variant,['javac','-source','8','-target','8','-d',str(folder),*map(str,files)])
classes=run/'classes';classes.mkdir();sources=sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))+[ROOT/'tests/myworld/BrowserOptimizedDefaultsFixture.java']
call('compile',['javac','-source','8','-target','8','-cp',str(a.client_jar.resolve()),'-d',str(classes),*map(str,sources)])
summary=call('java',['java','-cp',str(classes)+':'+str(a.client_jar.resolve()),'orsc.browser.BrowserOptimizedDefaultsFixture',*map(str,folders)])
call('policy',['node','tests/myworld/test-browser-optimized-render-defaults.mjs'])
for n,flags in [('normal',[]),('benchmark',['--benchmark-stream']),('benchmark-batch',['--model-batch']),('benchmark-combat',['--combat'])]:call('frontend-'+n,['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs',*flags])
inputs=[*sources,ROOT/'tools/browser-gameplay/web/browser-optimized-render-defaults.js',ROOT/'tools/browser-gameplay/web/browser.js',ROOT/'tools/browser-gameplay/web/browser-repeatable-policy.js',Path(__file__),ROOT/'tests/myworld/test-browser-optimized-render-defaults.mjs',ROOT/'tests/myworld/test-browser-webgl-frontend.mjs']
receipt=dict(passed=True,cpuOnly=True,sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),sourceHashes={str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest()for f in inputs},clientJarSha256=hashlib.sha256(a.client_jar.read_bytes()).hexdigest(),summary=summary,browserStarted=False,serverStarted=False,gpuExecuted=False,scope='Metadata-only class loading sentinels, old/new/incompatible stubs, canonical properties and mocked frontend; actual Firefox startup/game acceptance remains separate.')
(run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(run/'result.json')
