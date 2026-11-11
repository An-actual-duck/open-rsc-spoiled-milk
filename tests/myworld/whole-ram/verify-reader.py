#!/usr/bin/env python3
"""Run the production complete RAM reader against actual complete cooked data."""
import argparse,hashlib,json,pathlib,subprocess
p=argparse.ArgumentParser();
for name in ('jar','receipt','root','package','mini-cache','delivery','out'):p.add_argument('--'+name,type=pathlib.Path,required=True)
p.add_argument('--hidden-roofs',choices=('true','false'),default='false')
a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
receipt=json.loads(a.receipt.read_text());manifest=json.loads((a.delivery/'manifest.json').read_text());assert receipt['singleProductionCommit']and receipt['cleanCheckoutAtBuild']and receipt['classesTransplanted']is False and receipt['buildSucceeded'];assert sha(a.jar)==receipt['jarSha256']==manifest['clientJarSha256'];assert manifest['complete']is True
if a.out.exists():raise ValueError('Preserve previous evidence')
a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir();source=pathlib.Path(__file__).with_name('WholeRamReaderFixture.java');commands=[];result={}
def run(name,cmd):
 commands.append(cmd);r=subprocess.run(cmd,text=True,capture_output=True,timeout=90);(a.out/(name+'.log')).write_text(r.stdout+r.stderr)
 if r.returncode:raise AssertionError(name+' failed; inspect preserved log')
 return r.stdout
try:
 run('javac',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve()),str(source.resolve())])
 flags=['browserBenchmark','browserWholeZone','browserWholeRam','browserRegionBuild'];cmd=['java','-Xmx1536m','-Djava.awt.headless=true','-Dindependent.wholeRam.hiddenRoofs='+a.hidden_roofs]+['-Dspoiledmilk.'+name+'=true'for name in flags]+['-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.WholeRamReaderFixture']+[str(v.resolve())for v in (a.root,a.package,a.mini_cache,a.delivery,a.out/'world')];result=json.loads(run('java',cmd).strip().splitlines()[-1]);assert result['passed']is True
finally:
 (a.out/'result.json').write_text(json.dumps({'passed':result.get('passed',False),'requestedHiddenRoofs':a.hidden_roofs=='true','productionReceipt':receipt,'manifestSha256':sha(a.delivery/'manifest.json'),'fixtureSources':{str(p):sha(p)for p in (source,pathlib.Path(__file__))},'commands':commands,'result':result},indent=2)+'\n')
print(json.dumps({'passed':True,'checks':result['checks'],'output':str(a.out)}))
