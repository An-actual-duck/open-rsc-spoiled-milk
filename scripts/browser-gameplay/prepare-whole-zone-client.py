#!/usr/bin/env python3
"""Stage an exact complete whole-overworld private client; never start any service."""
import argparse,hashlib,json,os,re,shutil,subprocess,sys
from pathlib import Path
from urllib.parse import urlsplit,urlunsplit,parse_qsl,urlencode
ROOT=Path(__file__).resolve().parents[2]
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parent))
from private_scene_urls import scene_urls
ASSET_PATHS=['Cache/video/'+n for n in ['Authentic_Landscape.orsc','Authentic_Sprites.orsc','Custom_Sprites.osar','library.orsc','models.orsc']]
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--http-port',type=int,required=True);p.add_argument('--server-run',type=Path,required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True);p.add_argument('--bake-run',type=Path,required=True);p.add_argument('--gpu-residency',choices=['full','bounded'],default='full');p.add_argument('--static-scene',choices=['on','off'],default='off');p.add_argument('--gpu-budget-mib',type=int,choices=[256,512],default=256);a=p.parse_args()
 if not re.fullmatch('[a-zA-Z0-9_-]+',a.name):p.error('Simple fresh stage name required')
 build=json.loads(a.client_receipt.read_text());jar=a.client_jar.resolve();jar_sha=sha(jar)
 if build.get('jarSha256')!=jar_sha or not build.get('buildSucceeded')or not build.get('cleanCheckoutAtBuild')or build.get('singleProductionCommit')is not True:p.error('Exact clean single-source client artifact required')
 bake_run=a.bake_run.resolve();receipt=json.loads((bake_run/'bake-receipt.json').read_text());bake=(bake_run/'whole-zone').resolve();manifest=bake/'manifest.json';m=json.loads(manifest.read_text())
 if not(receipt.get('passed')and receipt.get('complete')and receipt.get('fullRequested')and receipt.get('requestedSectorLimit')==1330 and receipt.get('clientJarSha256')==jar_sha and m.get('complete')is True and m.get('preparedSectors')==1330 and m.get('preparedPlacements')==24580 and m.get('clientJarSha256')==jar_sha and sha(manifest)==receipt['manifestSha256']):p.error('Complete source-bound1330-sector/24580-object bake required; pilots cannot be staged')
 if [v.get('path')for v in m.get('externalAssets',[])]!=ASSET_PATHS:p.error('Missing/excess external Cache asset roster')
 for asset in m['externalAssets']:
  source=ROOT/'Client_Base'/asset['path']
  if not source.is_file()or source.stat().st_size!=asset['bytes']or sha(source)!=asset['sha256']:p.error('Baked/current external cache asset mismatch')
 # Execute actual frontend validator before creating a stage or installing files.
 check="import fs from'node:fs';import{validateWholeZoneManifest,validateWholeZoneSource}from"+json.dumps((ROOT/'tools/browser-gameplay/web/browser-whole-zone.js').as_uri())+";const m=validateWholeZoneManifest(JSON.parse(fs.readFileSync(process.argv[1])));validateWholeZoneSource(m,JSON.parse(fs.readFileSync(process.argv[2])));"
 subprocess.run(['node','--input-type=module','-e',check,str(manifest),str(bake/'source-manifest.json')],check=True,cwd=ROOT)
 files=[{'path':'manifest.json','bytes':manifest.stat().st_size,'sha256':sha(manifest)},{'path':'source-manifest.json','bytes':(bake/'source-manifest.json').stat().st_size,'sha256':sha(bake/'source-manifest.json')}]+[{k:r[k]for k in ['path','bytes','sha256']}for r in m['chunks']+m['textures']]
 unique={}
 for r in files:
  relative=Path(r['path'])
  if relative.is_absolute()or'..'in relative.parts:raise ValueError('Unsafe resource path')
  source=bake/relative
  if not source.is_file()or source.is_symlink()or source.stat().st_size!=r['bytes']or sha(source)!=r['sha256']:raise ValueError('Resource source mismatch: '+r['path'])
  if r['path']in unique and unique[r['path']]!=r:raise ValueError('Conflicting resource reuse')
  unique[r['path']]=r
 subprocess.run(['python3',str(ROOT/'scripts/browser-gameplay/prepare-repeatable-client.py'),'--name',a.name,'--http-port',str(a.http_port),'--server-run',str(a.server_run.resolve()),'--dependency-jar',str(jar),'--client-source-commit',build['clientSourceCommit']],check=True,stdout=subprocess.DEVNULL,cwd=ROOT)
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-gameplay-spike','--mkdir'],text=True).strip());run=category/a.name;web=run/'web';destination=web/'whole-zone';destination.mkdir();linked=copied=0
 for r in unique.values():
  source=bake/r['path'];target=destination/r['path'];target.parent.mkdir(parents=True,exist_ok=True)
  try:os.link(source,target);linked+=1
  except OSError:shutil.copyfile(source,target);copied+=1
  if sha(target)!=r['sha256']:raise ValueError('Staged resource mismatch')
 cache=web/'Cache/whole-zone';cache.mkdir();shutil.copyfile(manifest,cache/'manifest.json')
 if sha(cache/'manifest.json')!=sha(manifest):raise ValueError('Java/Worker catalog bytes differ')
 for asset in m['externalAssets']:
  target=web/asset['path']
  if target.stat().st_size!=asset['bytes']or sha(target)!=asset['sha256']:raise ValueError('Staged runtime Cache asset differs from bake')
 config={'schemaVersion':1,'manifestBytes':manifest.stat().st_size,'manifestSha256':sha(manifest),'clientJarSha256':jar_sha,'externalAssets':m['externalAssets']};(web/'browser-whole-zone-config.js').write_text('/* Immutable private stage; exact complete bake only. */\nexport default Object.freeze('+json.dumps(config,separators=(',',':'))+');\n')
 bootstrap=web/'bootstrap-files.txt';entries=bootstrap.read_text().splitlines();entries.append('Cache/whole-zone/manifest.json');bootstrap.write_text('\n'.join(sorted(set(entries)))+'\n')
 stage=json.loads((run/'stage.json').read_text());stage['frontendSha256']={f.name:sha(f)for f in web.iterdir()if f.is_file()and f.suffix in ['.js','.html','.css']};stage['wholeZone']={'enabled':True,'complete':True,'bakeReceiptSha256':sha(bake_run/'bake-receipt.json'),'catalog':config,'resources':len(unique),'resourceFileBytes':sum(r['bytes']for r in unique.values()),'hardLinkedFiles':linked,'copiedFiles':copied,'initialGpuCompletion':'Pending actual private browser startup fence; staging is not residency proof'};(run/'stage.json').write_text(json.dumps(stage,indent=2)+'\n')
 prepared=json.loads((run/'repeatable-preparation.json').read_text());parts=urlsplit(prepared['url']);query=dict(parse_qsl(parts.query));common={'renderworker':'on','loadprofile':'on','regionbuild':'on','modelbatch':'on','terrainquery':'on','overlaydelta':'off','overlayrect':'off'};query.update(common);query['wholezone']='on';prepared['urlOn']=urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode(query),parts.fragment));query['wholezone']='off';prepared['urlOff']=urlunsplit((parts.scheme,parts.netloc,parts.path,urlencode(query),parts.fragment));prepared['url']=prepared['urlOn'];prepared['comparisonFlags']=common;prepared['comparisonScope']='Same staged Java/frontend/physical quality/accepted private settings; only wholezone on/off changes. Staging is not measured performance.';prepared['wholeZone']=stage['wholeZone'];prepared.update(scene_urls(prepared['urlOn'],a.gpu_residency,a.static_scene,a.gpu_budget_mib));prepared['urlOn']=prepared['url'];prepared['comparisonScope']='Same exact staged Java/frontend/assets/settings. Named URL controls vary GPU residency or static CPU reuse independently; urlOff disables whole-zone and its dependent candidates.';prepared.update(preparedOnly=True,processesStarted=False,publicAccessed=False);(run/'whole-zone-preparation.json').write_text(json.dumps(prepared,indent=2)+'\n');(run/'whole-zone-resources.json').write_text(json.dumps(list(unique.values()),indent=2)+'\n');print(json.dumps(prepared,indent=2))
if __name__=='__main__':main()
