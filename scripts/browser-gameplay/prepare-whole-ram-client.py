#!/usr/bin/env python3
"""Stage source-bound prepared system RAM alongside GPU ON; never start services."""
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,sys
from pathlib import Path
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode
ROOT=Path(__file__).resolve().parents[2]
sys.dont_write_bytecode=True

def load_module():
 path=ROOT/'tools/browser-gameplay/whole-ram/source_bundle.py';spec=importlib.util.spec_from_file_location('ram_bundle',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def digest(path):
 h=hashlib.sha256()
 with path.open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()

def install_ram_delivery(web,data,m,jar_sha,gpu_sha):
 """Install a previously validated data set in a NEW stage, with ON-only list."""
 web,data=Path(web),Path(data);default=web/'bootstrap-files.txt';ordinary=default.read_bytes()
 if any(path.startswith('Cache/whole-ram/')for path in ordinary.decode().splitlines()):raise ValueError('RAM files must not be copied/fetched by ordinary or RAM-off bootstrap')
 destination=web/'Cache/whole-ram'
 if destination.exists():raise ValueError('Preserve existing RAM stage; choose a fresh stage')
 destination.mkdir();links=copies=0;inventory=[]
 for name in ['manifest.json','source.bin','prepared.bin']:
  source=data/name;target=destination/name
  if source.is_symlink()or not source.is_file():raise ValueError('RAM delivery source must be a regular immutable file')
  try:os.link(source,target);links+=1
  except OSError:shutil.copyfile(source,target);copies+=1
  sha=digest(target)
  if sha!=digest(source):raise ValueError('Staged RAM payload differs: '+name)
  inventory.append({'path':'Cache/whole-ram/'+name,'bytes':target.stat().st_size,'sha256':sha})
 (web/'bootstrap-whole-ram-files.txt').write_text('\n'.join(row['path']for row in inventory)+'\n')
 if default.read_bytes()!=ordinary:raise ValueError('Ordinary bootstrap changed while installing conditional RAM delivery')
 config={'schemaVersion':1,'manifestBytes':(data/'manifest.json').stat().st_size,'manifestSha256':digest(data/'manifest.json'),'clientJarSha256':jar_sha,'gpuCatalogSha256':gpu_sha,'sourceBundleBytes':m['sourceBundle']['bytes'],'sourceBundleSha256':m['sourceBundle']['sha256'],'preparedBytes':m['prepared']['bytes'],'preparedSha256':m['prepared']['sha256'],'retainedPrimitiveBytes':m['prepared']['retainedPrimitiveBytes'],'rawFiles':1797,'recipes':445,'queryCells':1330,'hiddenRoofs':m['prepared']['hiddenRoofs']}
 (web/'browser-whole-ram-config.js').write_text('/* Private complete CPU data; same exact client/GPU/source bindings. */\nexport default Object.freeze('+json.dumps(config,separators=(',',':'))+');\n')
 return {'preparedOnly':True,'declaredComplete':True,'config':config,'files':inventory,'hardLinkedFiles':links,'copiedFiles':copies,'conditionalBootstrapSha256':digest(web/'bootstrap-whole-ram-files.txt'),'ordinaryBootstrapUnchanged':True,'runtimeReadiness':'Pending actual Java full verification/decode/atomic provider install and use counts; staged files alone are not RAM ready'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--http-port',type=int,required=True);p.add_argument('--server-run',type=Path,required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True);p.add_argument('--bake-run',type=Path,required=True);p.add_argument('--ram-run',type=Path,required=True);a=p.parse_args();lib=load_module()
 if not re.fullmatch('[A-Za-z0-9_-]+',a.name):p.error('Fresh simple stage name required')
 jar=a.client_jar.resolve();build=lib.document(a.client_receipt);jar_sha=lib.sha(jar)
 if build.get('jarSha256')!=jar_sha or not(build.get('cleanCheckoutAtBuild')and build.get('singleProductionCommit')is True and build.get('buildSucceeded')):p.error('Exact clean single-source client artifact required')
 ram_run=a.ram_run.resolve();r=lib.document(ram_run/'ram-bake-receipt.json');data=ram_run/'whole-ram';gpu_path=a.bake_run.resolve()/'whole-zone/manifest.json';gpu=lib.document(gpu_path,32*1024*1024)
 if not(r.get('passed')and r.get('complete')and r.get('fullRequested')and r.get('requestedRecipes')==445 and r.get('clientJarSha256')==jar_sha and r.get('gpuCatalogSha256')==lib.sha(gpu_path)and gpu.get('clientJarSha256')==jar_sha):p.error('Complete useful445-recipe/1330-query preparation matching GPU/client is required; source-only/pilots cannot be staged')
 m=lib.validate_ram(data,{'clientJarSha256':jar_sha,'gpuCatalogSha256':lib.sha(gpu_path),'settings':gpu['settings'],'externalAssets':gpu['externalAssets']})
 if lib.sha(data/'manifest.json')!=r['manifestSha256']or m['prepared']!=r['prepared']:p.error('Prepared RAM manifest/receipt mismatch')
 for asset in m['externalAssets']:
  source=ROOT/'Client_Base'/asset['path']
  if source.stat().st_size!=asset['bytes']or lib.sha(source)!=asset['sha256']:p.error('RAM/current original assets differ')
 subprocess.run(['python3',str(ROOT/'scripts/browser-gameplay/prepare-whole-zone-client.py'),'--name',a.name,'--http-port',str(a.http_port),'--server-run',str(a.server_run.resolve()),'--client-jar',str(jar),'--client-receipt',str(a.client_receipt.resolve()),'--bake-run',str(a.bake_run.resolve())],check=True,cwd=ROOT,stdout=subprocess.DEVNULL)
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-gameplay-spike','--mkdir'],text=True).strip());run=category/a.name;web=run/'web'
 delivery=install_ram_delivery(web,data,m,jar_sha,lib.sha(gpu_path));delivery['ramBakeReceiptSha256']=lib.sha(ram_run/'ram-bake-receipt.json')
 stage=lib.document(run/'stage.json');stage['frontendSha256']={f.name:lib.sha(f)for f in web.iterdir()if f.is_file()and f.suffix in ['.js','.html','.css']};stage['wholeRam']=delivery;(run/'stage.json').write_text(json.dumps(stage,indent=2)+'\n')
 base=lib.document(run/'whole-zone-preparation.json');parts=urlsplit(base['urlOn']);query=dict(parse_qsl(parts.query));query['wholezone']='on';query['wholeram']='on';on=urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode(query),parts.fragment));query['wholeram']='off';off=urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode(query),parts.fragment));base.update(url=on,urlOn=on,urlOff=off,wholeRam=stage['wholeRam'],comparisonFlags={**base['comparisonFlags'],'wholezone':'on'},comparisonScope='Same exact Java/frontend/GPU whole-zone ON/assets/quality; only wholeram on/off changes. OFF does not open/copy RAM list/payloads.',preparedOnly=True,processesStarted=False,publicAccessed=False);(run/'whole-ram-preparation.json').write_text(json.dumps(base,indent=2)+'\n');print(json.dumps(base,indent=2))
if __name__=='__main__':main()
