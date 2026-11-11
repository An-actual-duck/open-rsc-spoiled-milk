#!/usr/bin/env python3
"""Cook bounded exact-source prepared CPU data; no game/server/browser/GPU launch."""
import argparse,importlib.util,json,re,subprocess,sys,time,zipfile
from pathlib import Path
sys.dont_write_bytecode=True
from source_bundle import ROOT,sha,document,build_source,validate_source,validate_ram

def baker_properties(hidden_roofs):
 if type(hidden_roofs)is not bool:raise ValueError('Cook hidden-roof option must be Boolean')
 return ['-Dspoiledmilk.browserWholeRamCookHiddenRoofs='+str(hidden_roofs).lower()]

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True);p.add_argument('--gpu-bake-run',type=Path,required=True);p.add_argument('--recipe-limit',type=int,default=3);p.add_argument('--full',action='store_true');p.add_argument('--hidden-roofs',action='store_true',help='Cook hidden-roof CPU variant; does not change the runtime client preference');p.add_argument('--centres',help='Pilot sx:sy CSV, never allowed for full preparation');p.add_argument('--heap-mib',type=int,default=512);p.add_argument('--source-root',type=Path,default=ROOT);a=p.parse_args();root=a.source_root.resolve();jar=a.client_jar.resolve()
 if not re.fullmatch('[A-Za-z0-9_-]+',a.name):p.error('Fresh simple run name required')
 if not 1<=a.recipe_limit<=445 or not 512<=a.heap_mib<=1536:p.error('CPU bounds1..445 recipes,512..1536MiB heap')
 if a.full!=(a.recipe_limit==445)or a.full and a.centres:p.error('Full requires --full --recipe-limit445 without centre narrowing')
 if a.centres and not re.fullmatch(r'-?[0-9]+:-?[0-9]+(?:,-?[0-9]+:-?[0-9]+)*',a.centres):p.error('Pilot centres use sx:sy CSV')
 build=document(a.client_receipt);jar_sha=sha(jar);head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 if build.get('jarSha256')!=jar_sha or not(build.get('singleProductionCommit')is True and build.get('cleanCheckoutAtBuild')is True and build.get('buildSucceeded')):p.error('Exact clean single-source client artifact required')
 if a.full and(root!=ROOT or subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()!='main'or build['clientSourceCommit']!=head):p.error('Full RAM cook is manager-only at final integrated artifact commit')
 sources=a.client_receipt.parent/'source-manifest.json'
 if sha(sources)!=build['sourceManifestSha256']or any(sha(root/path)!=value for path,value in document(sources).items()):p.error('Artifact/current source manifest mismatch')
 with zipfile.ZipFile(jar)as archive:
  if 'orsc/BrowserWholeRamBaker.class'not in archive.namelist():p.error('Actual prepared CPU baker missing from artifact')
 gpu_run=a.gpu_bake_run.resolve();gpu_receipt=document(gpu_run/'bake-receipt.json');gpu_path=gpu_run/'whole-zone/manifest.json';gpu=document(gpu_path,32*1024*1024)
 if not(gpu_receipt.get('passed')and gpu_receipt.get('complete')and gpu_receipt.get('clientJarSha256')==jar_sha and gpu.get('complete')is True and gpu.get('clientJarSha256')==jar_sha and gpu_receipt['manifestSha256']==sha(gpu_path)):p.error('Fresh complete GPU bake must match this exact artifact; no rebinding')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','builds','browser-whole-ram-bake','--mkdir'],text=True).strip());run=base/a.name;run.mkdir();raw=run/'raw-source';out=run/'whole-ram';cache=run/'mini-cache'
 source=build_source(root,raw,jar_sha,sha(gpu_path),gpu['settings'],gpu['externalAssets'])
 path=ROOT/'tools/browser-gameplay/whole-zone/extract-textures.py';spec=importlib.util.spec_from_file_location('ram_textures',path);extract=importlib.util.module_from_spec(spec);spec.loader.exec_module(extract);texture=extract.extract(root/'Client_Base/Cache/video/Custom_Sprites.osar',cache/'video/Custom_Sprites.osar');(run/'texture-source.json').write_text(json.dumps(texture,indent=2)+'\n')
 selected=json.loads((root/'server/world-builder-configs/primary.json').read_text());package=root/selected['serverMapRelativePath']
 command=['/usr/bin/time','-v','java','-Xmx'+str(a.heap_mib)+'m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true',*baker_properties(a.hidden_roofs),'-cp',str(jar),'orsc.BrowserWholeRamBaker',str(root),str(package),str(cache),str(raw),str(out),jar_sha,str(a.recipe_limit)]
 if a.centres:command.append(a.centres)
 receipt={'passed':False,'complete':False,'preparedOnly':True,'cpuOnly':True,'processesStarted':'One bounded headless CPU baker, no game/server/browser/GPU','fullRequested':a.full,'requestedRecipes':a.recipe_limit,'heapLimitMiB':a.heap_mib,'timeoutSeconds':300,'hiddenRoofs':a.hidden_roofs,'clientJarSha256':jar_sha,'clientSourceCommit':build['clientSourceCommit'],'clientReceiptSha256':sha(a.client_receipt),'clientSourceManifestSha256':sha(sources),'gpuBakeReceiptSha256':sha(gpu_run/'bake-receipt.json'),'gpuCatalogSha256':sha(gpu_path),'sourceBundleManifestSha256':sha(raw/'manifest.json'),'sourceBundleSha256':source['sourceBundle']['sha256'],'driverSourceCommit':head,'driverSha256':sha(Path(__file__)),'command':command};start=time.monotonic()
 try:
  with(run/'stdout.log').open('w')as stdout,(run/'resource.log').open('w')as stderr:subprocess.run(command,cwd=root,stdout=stdout,stderr=stderr,check=True,timeout=300)
  product=validate_source(out,{'clientJarSha256':jar_sha,'gpuCatalogSha256':sha(gpu_path),'settings':gpu['settings'],'externalAssets':gpu['externalAssets']})
  if a.full:product=validate_ram(out,{'clientJarSha256':jar_sha,'gpuCatalogSha256':sha(gpu_path),'settings':gpu['settings'],'externalAssets':gpu['externalAssets']})
  prepared=product['prepared'];file=out/'prepared.bin'
  if type(prepared.get('hiddenRoofs'))is not bool or prepared['hiddenRoofs']is not a.hidden_roofs:raise ValueError('Baker CPU roof variant differs from requested cook option')
  if file.stat().st_size!=prepared['bytes']or sha(file)!=prepared['sha256']:raise ValueError('Prepared output length/hash mismatch')
  if not a.full and product.get('complete'):raise ValueError('Pilot unexpectedly claims full preparation')
  receipt.update(passed=True,complete=product.get('complete')is True,manifestSha256=sha(out/'manifest.json'),manifestBytes=(out/'manifest.json').stat().st_size,sourceBundleBytes=(out/'source.bin').stat().st_size,prepared=prepared,output=str(out))
  for name in ['source.bin','prepared.bin']: (out/name).chmod(0o444)
 except BaseException as error:receipt['error']=str(error);raise
 finally:
  receipt['elapsedSeconds']=time.monotonic()-start;receipt['stdoutSha256']=sha(run/'stdout.log');receipt['resourceLogSha256']=sha(run/'resource.log');(run/'ram-bake-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
 print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
