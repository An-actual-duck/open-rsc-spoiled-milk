#!/usr/bin/env python3
"""Compile independent fixtures against ONE unified immutable production JAR."""
import argparse,hashlib,json,pathlib,subprocess
p=argparse.ArgumentParser();p.add_argument('--jar',type=pathlib.Path,required=True);p.add_argument('--receipt',type=pathlib.Path,required=True);p.add_argument('--root',type=pathlib.Path,required=True);p.add_argument('--package',type=pathlib.Path,required=True);p.add_argument('--mini-cache',type=pathlib.Path,required=True);p.add_argument('--prepared',type=pathlib.Path,required=True);p.add_argument('--out',type=pathlib.Path,required=True);p.add_argument('--hidden-roofs',choices=('true','false'),default='false');a=p.parse_args()
def require(v,m):
 if not v:raise AssertionError(m)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
r=json.loads(a.receipt.read_text());require(r['singleProductionCommit'] and r['cleanCheckoutAtBuild'] and r['classesTransplanted'] is False and r['buildSucceeded'],'Unified clean production artifact required');require(sha(a.jar)==r['jarSha256'],'Exact receipt JAR hash required')
require(not a.out.exists(),'Preserve previous fixture results');a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir();fixtures=[pathlib.Path(__file__).with_name(n)for n in ('WholeRamWorldState.java','WholeRamModelFieldFixture.java','WholeRamActivationFixture.java','WholeRamDefaultsFixture.java')]
commands=[];results={};policies=[];source_guard={}
def run(name,cmd):
 commands.append(cmd);s=subprocess.run(cmd,capture_output=True,text=True,timeout=90);(a.out/(name+'.log')).write_text(s.stdout+s.stderr);require(s.returncode==0,name+' failed; inspect preserved log');return s.stdout
try:
 guard=pathlib.Path(__file__).with_name('verify-flat-light-readers.py');source_guard=json.loads(run('source-guard',['python3',str(guard.resolve()),'--source-root',str(a.root.resolve()),'--commit',r['clientSourceCommit']]))
 run('javac',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve())]+[str(f.resolve())for f in fixtures]);cp=str(classes.resolve())+':'+str(a.jar.resolve())
 common=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true','-Dindependent.wholeRam.hiddenRoofs='+a.hidden_roofs,'-cp',cp]
 for mask in range(8):
  props=['-Dspoiledmilk.'+name+'='+str(bool(mask&(1<<i))).lower()for i,name in enumerate(('browserBenchmark','browserWholeZone','browserWholeRam'))];policies.append(json.loads(run('policy-'+str(mask),['java','-Xmx128m','-Djava.awt.headless=true']+props+['-cp',cp,'orsc.WholeRamDefaultsFixture']).strip().splitlines()[-1]))
 (a.out/'policies.json').write_text(json.dumps(policies,indent=2)+'\n')
 results['models']=json.loads(run('models',common+['orsc.WholeRamModelFieldFixture']).strip().splitlines()[-1])
 results['activation']=json.loads(run('activation',common+['orsc.WholeRamActivationFixture']+[str(v.resolve())for v in (a.root,a.package,a.mini_cache,a.prepared,a.out/'world')]).strip().splitlines()[-1]);require(results['activation']['passed'] and results['models']['passed'],'Nonvacuous proof required')
finally:
 (a.out/'result.json').write_text(json.dumps({'passed':len(results)==2 and len(policies)==8 and source_guard.get('passed')is True,'requestedHiddenRoofs':a.hidden_roofs=='true','productionReceipt':r,'preparedSha256':sha(a.prepared),'fixtureSources':{str(f):sha(f)for f in fixtures+[pathlib.Path(__file__),pathlib.Path(__file__).with_name('verify-flat-light-readers.py')]},'commands':commands,'sourceGuard':source_guard,'policies':policies,'results':results},indent=2)+'\n')
print(json.dumps({'passed':True,'out':str(a.out),'checks':sum(x['checks']for x in results.values())+sum(x['checks']for x in policies)}))
