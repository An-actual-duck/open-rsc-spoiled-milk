#!/usr/bin/env python3
"""Run an exact-client, bounded CPU whole-zone baker. Never starts game/server/GPU."""
import argparse,hashlib,importlib.util,json,re,shutil,struct,subprocess,sys,time,zipfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
FINGERPRINT='7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc'
ASSET_PATHS=['Cache/video/'+n for n in ['Authentic_Landscape.orsc','Authentic_Sprites.orsc','Custom_Sprites.osar','library.orsc','models.orsc']]
def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b)
 return h.hexdigest()
def module(path,name):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--client-receipt',type=Path,required=True);p.add_argument('--limit-sectors',type=int,default=4);p.add_argument('--full',action='store_true');p.add_argument('--heap-mib',type=int,default=512);p.add_argument('--pilot-position',help='Optional sx:sy pilot source position');p.add_argument('--source-root',type=Path,default=ROOT,help='Read-only source checkout for a separately owned pilot');a=p.parse_args();root=a.source_root.resolve()
 if not re.fullmatch('[a-zA-Z0-9_-]+',a.name):p.error('Simple fresh output name required')
 if not 1<=a.limit_sectors<=1330 or not 512<=a.heap_mib<=1536:p.error('Explicit CPU bounds:1..1330 sectors,512..1536MiB heap')
 if a.pilot_position and(a.full or not re.fullmatch('-?[0-9]+:-?[0-9]+',a.pilot_position)):p.error('Pilot position needs sx:sy and cannot narrow full scope')
 if (a.limit_sectors==1330)!=a.full:p.error('Full1330-sector bake requires explicit --full; pilots must stay below1330')
 jar=a.client_jar.resolve();build=json.loads(a.client_receipt.read_text());jar_sha=sha(jar)
 if not(build.get('buildSucceeded')and build.get('singleProductionCommit')is True and build.get('cleanCheckoutAtBuild')is True and build.get('jarSha256')==jar_sha):p.error('Use exact clean single-source client build receipt')
 source_commit=build['clientSourceCommit'];head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 source_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
 if a.full and(root!=ROOT or source_commit!=source_head or subprocess.check_output(['git','branch','--show-current'],cwd=root,text=True).strip()!='main'):p.error('Full bake is manager-only after final integration; current main/source artifact must match')
 source_manifest=a.client_receipt.parent/'source-manifest.json'
 if sha(source_manifest)!=build['sourceManifestSha256']:p.error('Source-manifest receipt mismatch')
 sources=json.loads(source_manifest.read_text())
 if any(sha(root/path)!=value for path,value in sources.items()):p.error('Selected artifact source files differ from current source')
 with zipfile.ZipFile(jar)as z:
  if 'orsc/BrowserWholeZoneBaker.class'not in z.namelist():p.error('Selected full-source artifact has no whole-zone baker')
 selected=module(root/'scripts/installed-map-selection.py','zone_selected').select(root)
 if selected['packageFingerprintSha256']!=FINGERPRINT:p.error('Whole-zone source package mismatch')
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','builds','browser-whole-zone-bake','--mkdir'],text=True).strip());run=category/a.name;run.mkdir();cache=run/'mini-cache';out=run/'whole-zone'
 extract=module(Path(__file__).with_name('extract-textures.py'),'zone_texture_extractor');texture=extract.extract(root/'Client_Base/Cache/video/Custom_Sprites.osar',cache/'video/Custom_Sprites.osar');(run/'texture-source.json').write_text(json.dumps(texture,indent=2)+'\n')
 command=['/usr/bin/time','-v','java','-Xmx'+str(a.heap_mib)+'m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true','-cp',str(jar),'orsc.BrowserWholeZoneBaker',str(root),selected['serverPackage'],str(cache),str(out),jar_sha,str(a.limit_sectors)]
 if a.pilot_position:command.append(a.pilot_position)
 receipt={'passed':False,'auditCPUOnly':True,'processesStarted':'One bounded headless Java CPU baker; no game/server/browser/GPU','fullRequested':a.full,'requestedSectorLimit':a.limit_sectors,'heapLimitMiB':a.heap_mib,'timeoutSeconds':600,'clientJarSha256':jar_sha,'clientSourceCommit':source_commit,'clientReceiptSha256':sha(a.client_receipt),'clientSourceManifestSha256':sha(source_manifest),'driverSourceCommit':head,'driverSha256':sha(Path(__file__)),'sourceRoot':str(root),'selection':selected,'textureSourceReceiptSha256':sha(run/'texture-source.json'),'command':command};start=time.monotonic()
 try:
  with(run/'stdout.log').open('w')as stdout,(run/'resource.log').open('w')as stderr:subprocess.run(command,stdout=stdout,stderr=stderr,check=True,timeout=600,cwd=ROOT)
  manifest=out/'manifest.json';m=json.loads(manifest.read_text());source=Path(selected['serverPackage'])/'manifest.json';source_copy=out/'source-manifest.json'
  if source_copy.exists():
   if sha(source_copy)!=selected['manifestSha256']:raise ValueError('Baked source manifest mismatch')
  else:shutil.copyfile(source,source_copy)
  if m.get('clientJarSha256')!=jar_sha or m.get('packageFingerprintSha256')!=FINGERPRINT:raise ValueError('Baked artifact/source identity mismatch')
  if [v.get('path')for v in m.get('externalAssets',[])]!=ASSET_PATHS:raise ValueError('Missing/excess external Cache asset roster')
  for asset in m['externalAssets']:
   file=root/'Client_Base'/asset['path']
   if file.stat().st_size!=asset['bytes']or sha(file)!=asset['sha256']:raise ValueError('Baked/current external Cache asset mismatch')
  if m.get('derivativeTextureSha256')!=texture['destinationSha256']:raise ValueError('Baker texture derivative differs from exact extraction receipt')
  if m.get('sourceManifestBytes')!=source_copy.stat().st_size:raise ValueError('Baker must declare actual source manifest byte size')
  if a.full:
   if m.get('complete')is not True:raise ValueError('Full baker did not declare complete')
   validator="import fs from'node:fs';import{validateWholeZoneManifest,validateWholeZoneSource}from"+json.dumps((ROOT/'tools/browser-gameplay/web/browser-whole-zone.js').as_uri())+";const m=validateWholeZoneManifest(JSON.parse(fs.readFileSync(process.argv[1])));validateWholeZoneSource(m,JSON.parse(fs.readFileSync(process.argv[2])));console.log('PASS exact full-zone catalogue/source coverage');"
   subprocess.run(['node','--input-type=module','-e',validator,str(manifest),str(source_copy)],check=True,cwd=ROOT)
  files=[];total=0
  for kind in ['chunks','textures']:
   for r in m[kind]:
    relative=Path(r['path'])
    if relative.is_absolute()or'..'in relative.parts:raise ValueError('Unsafe baked resource path')
    file=out/relative
    if not file.is_file()or file.is_symlink()or file.stat().st_size!=r['bytes']or sha(file)!=r['sha256']:raise ValueError('Baked resource length/hash mismatch')
    if kind=='chunks':
     with file.open('rb')as stream:header=stream.read(16)
     if struct.unpack('<4I',header)!=(0x575a4331,r['vertexCount'],r['indexCount'],r['batchIntCount']):raise ValueError('Baked binary header mismatch')
    files.append({'path':r['path'],'bytes':r['bytes'],'sha256':r['sha256']});total+=r['bytes'];file.chmod(0o444)
  (run/'resource-inventory.json').write_text(json.dumps(files,indent=2)+'\n');receipt.update(passed=True,complete=m.get('complete')is True,manifestSha256=sha(manifest),manifestBytes=manifest.stat().st_size,resources=len(files),resourceFileBytes=total,resourceInventorySha256=sha(run/'resource-inventory.json'),bake=str(out))
 except BaseException as error:receipt['error']=str(error);raise
 finally:
  receipt['elapsedSeconds']=time.monotonic()-start;receipt['stdoutSha256']=sha(run/'stdout.log');receipt['resourceLogSha256']=sha(run/'resource.log');(run/'bake-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
 print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
