#!/usr/bin/env python3
"""Actual original carrier publication versus decoded adapters/complete reader."""
import argparse,hashlib,json,pathlib,subprocess
p=argparse.ArgumentParser()
for name in ('jar','receipt','root','package','mini-cache','delivery','out'):p.add_argument('--'+name,type=pathlib.Path,required=True)
p.add_argument('--hidden-roofs',choices=('true','false'),required=True);p.add_argument('--prepared-only',action='store_true',help='Use actual decoded product APIs only; explicitly NO reader/JAR-binding/readiness proof')
a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
receipt=json.loads(a.receipt.read_text());manifest=json.loads((a.delivery/'manifest.json').read_text());assert receipt['singleProductionCommit']and receipt['cleanCheckoutAtBuild']and receipt['classesTransplanted']is False and receipt['buildSucceeded'];assert sha(a.jar)==receipt['jarSha256'];assert manifest['complete']is True
if not a.prepared_only:assert receipt['jarSha256']==manifest['clientJarSha256']
if a.out.exists():raise ValueError('Preserve previous evidence')
a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir();sources=[pathlib.Path(__file__).with_name(n)for n in ('WholeRamBasisFixture.java','WholeRamWorldState.java','WholeRamActivationFixture.java','WholeRamModelFieldFixture.java')];commands=[];result={};guard={}
def run(name,cmd):
 commands.append(cmd);r=subprocess.run(cmd,text=True,capture_output=True,timeout=90);(a.out/(name+'.log')).write_text(r.stdout+r.stderr)
 if r.returncode:raise AssertionError(name+' failed; inspect preserved log')
 return r.stdout
try:
 guard=json.loads(run('query-consumer-guard',['python3',str(pathlib.Path(__file__).with_name('verify-query-light-consumers.py').resolve()),'--source-root',str(a.root.resolve()),'--commit',receipt['clientSourceCommit']]).strip().splitlines()[-1])
 run('javac',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve())]+[str(s.resolve())for s in sources])
 flags=['browserBenchmark','browserWholeZone','browserWholeRam','browserRegionBuild'];cmd=['java','-Xmx1536m','-Djava.awt.headless=true','-Dindependent.wholeRam.hiddenRoofs='+a.hidden_roofs,'-Dindependent.wholeRam.preparedOnly='+str(a.prepared_only).lower()]+['-Dspoiledmilk.'+n+'=true'for n in flags]+['-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.WholeRamBasisFixture']+[str(v.resolve())for v in (a.root,a.package,a.mini_cache,a.delivery,a.out/'world')];result=json.loads(run('java',cmd).strip().splitlines()[-1]);assert result['passed']is True
finally:
 (a.out/'result.json').write_text(json.dumps({'passed':result.get('passed',False),'preparedOnly':a.prepared_only,'productionReceipt':receipt,'dataJarSha256':manifest['clientJarSha256'],'manifestSha256':sha(a.delivery/'manifest.json'),'preparedSha256':sha(a.delivery/'prepared.bin'),'fixtureSources':{str(p):sha(p)for p in sources+[pathlib.Path(__file__),pathlib.Path(__file__).with_name('verify-query-light-consumers.py'),pathlib.Path(__file__).with_name('query-light-consumers.json')]},'commands':commands,'queryConsumerGuard':guard,'result':result},indent=2)+'\n')
print(json.dumps({'passed':True,'checks':result['checks'],'preparedOnly':a.prepared_only,'output':str(a.out)}))
