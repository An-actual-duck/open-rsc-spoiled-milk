#!/usr/bin/env python3
"""Early refusal and conditional file-delivery tests; never a valid RAM/GPU run."""
import hashlib,importlib.util,json,subprocess,sys,zipfile
from datetime import datetime
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def module(path,name):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-whole-ram-staging','--mkdir'],text=True).strip());run=base/('refusal-'+datetime.now().strftime('%Y%m%dT%H%M%S%f'));run.mkdir();stager=module(ROOT/'scripts/browser-gameplay/prepare-whole-ram-client.py','ram_stager');checks=0
 data=run/'delivery-plan-only';data.mkdir()
 for name,payload in [('manifest.json',b'{}'),('source.bin',b'NOT REAL SOURCE'),('prepared.bin',b'NOT REAL PREPARED DATA')]: (data/name).write_bytes(payload)
 m={'sourceBundle':{'bytes':14,'sha256':sha(data/'source.bin')},'prepared':{'bytes':22,'sha256':sha(data/'prepared.bin'),'retainedPrimitiveBytes':999,'hiddenRoofs':False}}
 web=run/'new-web';(web/'Cache').mkdir(parents=True);ordinary=b'Cache/config.txt\nCache/whole-zone/manifest.json\n';(web/'bootstrap-files.txt').write_bytes(ordinary)
 delivery=stager.install_ram_delivery(web,data,m,'a'*64,'b'*64)
 assert(web/'bootstrap-files.txt').read_bytes()==ordinary;checks+=1
 assert(web/'bootstrap-whole-ram-files.txt').read_text().splitlines()==['Cache/whole-ram/manifest.json','Cache/whole-ram/source.bin','Cache/whole-ram/prepared.bin'];checks+=1
 for name in ['manifest.json','source.bin','prepared.bin']:assert sha(web/'Cache/whole-ram'/name)==sha(data/name);checks+=1
 assert delivery['runtimeReadiness'].startswith('Pending actual Java');checks+=1
 try:stager.install_ram_delivery(web,data,m,'a'*64,'b'*64)
 except ValueError:checks+=1
 else:raise AssertionError('Existing delivery must be preserved')
 bad=run/'bad-web';(bad/'Cache').mkdir(parents=True);(bad/'bootstrap-files.txt').write_text('Cache/whole-ram/source.bin\n')
 try:stager.install_ram_delivery(bad,data,m,'a'*64,'b'*64)
 except ValueError:checks+=1
 else:raise AssertionError('Ordinary preload must not include RAM files')
 assert not(bad/'Cache/whole-ram').exists();checks+=1
 jar=run/'synthetic-not-executable.jar'
 with zipfile.ZipFile(jar,'w')as z:z.writestr('no-class.txt','No JVM execution may occur in these refusal tests')
 sources=run/'source-manifest.json';sources.write_text('{}');build={'jarSha256':sha(jar),'singleProductionCommit':True,'cleanCheckoutAtBuild':True,'buildSucceeded':True,'clientSourceCommit':'0'*40,'sourceManifestSha256':sha(sources)};receipt=run/'receipt.json';receipt.write_text(json.dumps(build));gpu=run/'gpu/whole-zone';gpu.mkdir(parents=True);(gpu/'manifest.json').write_text(json.dumps({'clientJarSha256':sha(jar),'settings':[0,0,1,0],'externalAssets':[]}));ram=run/'ram/whole-ram';ram.mkdir(parents=True);(ram.parent/'ram-bake-receipt.json').write_text(json.dumps({'passed':True,'complete':False,'fullRequested':False,'requestedRecipes':3,'clientJarSha256':sha(jar),'gpuCatalogSha256':sha(gpu/'manifest.json')}));(ram/'manifest.json').write_text('{"complete":false}')
 name='ram-negative-'+run.name;stage_path=ROOT/'output/tests/browser-gameplay-spike'/name;results=[]
 def reject(label,command,text):
  nonlocal checks
  p=subprocess.run(command,cwd=ROOT,text=True,capture_output=True,timeout=30);(run/(label+'.stdout')).write_text(p.stdout);(run/(label+'.stderr')).write_text(p.stderr)
  assert p.returncode!=0 and text in p.stderr,(label,p.stderr);assert not stage_path.exists();checks+=2;results.append({'label':label,'exit':p.returncode,'command':command,'stderrSha256':sha(run/(label+'.stderr'))})
 stage=['python3',str(ROOT/'scripts/browser-gameplay/prepare-whole-ram-client.py'),'--name',name,'--http-port','8299','--server-run',str(run/'NO-SERVER'),'--client-jar',str(jar),'--client-receipt',str(receipt),'--bake-run',str(gpu.parent),'--ram-run',str(ram.parent)]
 reject('incomplete-source-only',stage,'Complete useful445-recipe')
 bake=['python3',str(ROOT/'tools/browser-gameplay/whole-ram/bake.py'),'--name',name,'--client-jar',str(jar),'--client-receipt',str(receipt),'--gpu-bake-run',str(gpu.parent),'--full','--recipe-limit','445']
 reject('nonintegrated-full-cook',bake,'manager-only')
 pilot=bake[:-3]+['--recipe-limit','3'];reject('no-real-baker',pilot,'Actual prepared CPU baker missing')
 wrong=dict(build,jarSha256='f'*64);receipt.write_text(json.dumps(wrong));reject('artifact-mismatch',stage,'Exact clean single-source')
 report={'passed':True,'checks':checks,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'testSha256':sha(Path(__file__)),'stagerSha256':sha(ROOT/'scripts/browser-gameplay/prepare-whole-ram-client.py'),'bakerDriverSha256':sha(ROOT/'tools/browser-gameplay/whole-ram/bake.py'),'results':results,'scope':'Conditional file-plan installation and early CLI refusals using explicitly non-runtime synthetic bytes/JAR. No valid source/data acceptance, Java, game, server, browser, GPU, network process or performance test.'};(run/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({'passed':True,'checks':checks,'run':str(run)}))
if __name__=='__main__':main()
