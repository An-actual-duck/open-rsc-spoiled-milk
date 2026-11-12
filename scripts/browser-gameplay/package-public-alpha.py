#!/usr/bin/env python3
"""Build a versioned, source-bound public browser directory. Never starts or publishes services."""
import argparse,hashlib,importlib.util,json,os,re,shutil,subprocess,sys,zipfile
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).resolve().parent))
from compact_geometry_delivery import admit_compact_geometry
ORIGIN='https://play.rscspoiledmilk.com'
WEBSOCKET='wss://play.rscspoiledmilk.com:43505/'
PRESET='alpha-v1'
DENIED_NAMES={'credentials.txt','client_settings.txt','client-settings.json','private-route.js','repeatable-fixture.json','repeatable-preparation.json','BROWSER-PRIVATE.json'}
SECRET_TOKENS=[b'BrowserProof13',b'browserdemo']

def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb')as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block)
 return h.hexdigest()
def unique(pairs):
 d={}
 for k,v in pairs:
  if k in d:raise ValueError('Duplicate metadata key: '+k)
  d[k]=v
 return d
def document(path,maximum=32*1024*1024):
 path=Path(path)
 if path.is_symlink()or not path.is_file()or path.stat().st_size>maximum:raise ValueError('Regular bounded metadata required')
 return json.loads(path.read_bytes(),object_pairs_hook=unique,parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Nonfinite metadata')))
def safe_path(value):
 if not isinstance(value,str)or not re.fullmatch(r'[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*',value)or any(p in ('.','..')for p in value.split('/')):raise ValueError('Unsafe resource path')
 return value
def regular(path):
 path=Path(path)
 if path.is_symlink()or not path.is_file():raise ValueError('Regular immutable input required: '+str(path))
 return path
def copy_checked(source,target,expected=None):
 source=regular(source);target=Path(target)
 if target.exists():raise ValueError('Duplicate output path: '+str(target))
 if expected and(source.stat().st_size!=expected['bytes']or sha(source)!=expected['sha256']):raise ValueError('Input payload does not match receipt: '+str(source))
 target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
 if sha(source)!=sha(target):raise ValueError('Copy changed bytes')
def load_ram_module():
 path=ROOT/'tools/browser-gameplay/whole-ram/source_bundle.py';spec=importlib.util.spec_from_file_location('public_ram_admission',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def admit_build(jar,receipt,head):
 jar=regular(jar);receipt=Path(receipt);b=document(receipt)
 if not(b.get('singleProductionCommit')is True and b.get('cleanCheckoutAtBuild')is True and b.get('buildSucceeded')is True and b.get('classesTransplanted')is False and b.get('clientSourceCommit')==head and b.get('jarSha256')==sha(jar)):raise ValueError('Exact fresh clean single-source full client receipt required')
 for name,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:
  if sha(regular(receipt.parent/name))!=b[key]:raise ValueError('Build manifest binding mismatch')
 sources=document(receipt.parent/'source-manifest.json')
 if any(sha(regular(ROOT/safe_path(p)))!=digest for p,digest in sources.items()):raise ValueError('Client source differs from frozen build')
 classes=document(receipt.parent/'compiled-classes.json');assets=document(receipt.parent/'asset-manifest.json')
 if len(classes)!=b['compiledClasses']or len(assets)!=b['packagedOwnAssets']:raise ValueError('Full build roster mismatch')
 with zipfile.ZipFile(jar)as z:
  if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate archive entry')
  for path,digest in {**classes,**assets}.items():
   data=z.read(safe_path(path))
   if hashlib.sha256(data).hexdigest()!=digest:raise ValueError('Class/asset receipt mismatch')
  for name in z.namelist():
   if not name.endswith('/')and any(token in z.read(name)for token in SECRET_TOKENS):raise ValueError('Test account data in client archive')
 return b

def admit_gpu(bake,compact_run,jar_sha):
 bake=Path(bake);r=document(bake/'bake-receipt.json');data=bake/'whole-zone';m=document(data/'manifest.json');source=data/'source-manifest.json'
 if not(r.get('passed')is True and r.get('complete')is True and r.get('fullRequested')is True and r.get('requestedSectorLimit')==1330 and r.get('clientJarSha256')==jar_sha and r.get('manifestSha256')==sha(data/'manifest.json')and m.get('clientJarSha256')==jar_sha and m.get('complete')is True and m.get('preparedSectors')==1330 and m.get('preparedPlacements')==24580):raise ValueError('Complete matching GPU catalogue required')
 # Use the actual frontend validators, not just a packaging-only approximation.
 module=(ROOT/'tools/browser-gameplay/web/browser-whole-zone.js').as_uri()
 script="import fs from'node:fs';import{validateWholeZoneManifest,validateWholeZoneSource}from"+json.dumps(module)+";const m=validateWholeZoneManifest(JSON.parse(fs.readFileSync(process.argv[1])));validateWholeZoneSource(m,JSON.parse(fs.readFileSync(process.argv[2])));"
 subprocess.run(['node','--input-type=module','-e',script,str(data/'manifest.json'),str(source)],check=True,cwd=ROOT)
 compact=admit_compact_geometry(compact_run,data,m)
 # Canonical geometry is metadata-only in this deployment. The complete verified
 # compact derivative replaces EVERY resource before any chunk fetch. Textures,
 # source/catalogue metadata and all compact geometry remain delivered.
 records=[({'path':'manifest.json','bytes':(data/'manifest.json').stat().st_size,'sha256':sha(data/'manifest.json')},data),({'path':'source-manifest.json','bytes':source.stat().st_size,'sha256':sha(source)},data)]
 records += [(r,data)for r in m['textures']]
 records += [(r,compact['root'])for r in compact['files']]
 seen=set()
 for row,base in records:
  p=safe_path(row['path'])
  if p in seen:raise ValueError('Duplicate delivery resource')
  seen.add(p);file=regular(base/p)
  if file.stat().st_size!=row['bytes']or sha(file)!=row['sha256']:raise ValueError('Delivery resource mismatch')
 if any(c['path']in seen for c in m['chunks']):raise ValueError('Canonical geometry accidentally included')
 return m,compact,records

def executable_jar(classes,destination):
 with zipfile.ZipFile(destination,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
  for file in sorted(Path(classes).rglob('*.class')):
   data=file.read_bytes()
   if any(token in data for token in SECRET_TOKENS):raise ValueError('Test account data in bootstrap class')
   info=zipfile.ZipInfo(file.relative_to(classes).as_posix(),date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for flag in ['client-jar','client-receipt','bake-run','compact-run','ram-run']:p.add_argument('--'+flag,type=Path,required=True)
 p.add_argument('--build-id',required=True);a=p.parse_args()
 if not re.fullmatch('[a-z0-9][a-z0-9-]{6,63}',a.build_id):p.error('Lowercase versioned build ID required')
 head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():p.error('Checkpoint all source before packaging')
 build=admit_build(a.client_jar,a.client_receipt,head);jar_sha=sha(a.client_jar)
 gpu,compact,resources=admit_gpu(a.bake_run,a.compact_run,jar_sha);gpu_manifest=Path(a.bake_run)/'whole-zone/manifest.json';gpu_sha=sha(gpu_manifest)
 lib=load_ram_module();ram_data=Path(a.ram_run)/'whole-ram';ram_receipt=document(Path(a.ram_run)/'ram-bake-receipt.json')
 if not(ram_receipt.get('passed')is True and ram_receipt.get('complete')is True and ram_receipt.get('fullRequested')is True and ram_receipt.get('requestedRecipes')==445 and ram_receipt.get('clientJarSha256')==jar_sha and ram_receipt.get('gpuCatalogSha256')==gpu_sha):raise ValueError('Complete source-bound RAM bake required')
 ram=lib.validate_ram(ram_data,{'clientJarSha256':jar_sha,'gpuCatalogSha256':gpu_sha,'settings':gpu['settings'],'externalAssets':gpu['externalAssets']})
 if sha(ram_data/'manifest.json')!=ram_receipt['manifestSha256']or ram['prepared']!=ram_receipt['prepared']or ram_receipt.get('hiddenRoofs')is not ram['prepared']['hiddenRoofs']:raise ValueError('RAM receipt binding mismatch')
 for asset in gpu['externalAssets']:
  source=regular(ROOT/'Client_Base'/safe_path(asset['path']))
  if source.stat().st_size!=asset['bytes']or sha(source)!=asset['sha256']:raise ValueError('Original external asset binding mismatch')
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','releases','browser-alpha','--mkdir'],text=True).strip());run=category/a.build_id
 if run.exists():raise ValueError('Fresh output name required; prior bundles preserved')
 run.mkdir();web=run/'web';prefix='assets/'+a.build_id;asset_root=web/prefix;asset_root.mkdir(parents=True)
 for file in sorted((ROOT/'tools/browser-gameplay/web').iterdir()):
  if file.suffix in ('.js','.html','.css')and file.name not in ('index.html','browser-deployment-config.js'):
   copy_checked(file,asset_root/file.name)
 classes=run/'bootstrap-classes';classes.mkdir()
 sources=sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))
 subprocess.run(['javac','-source','8','-target','8','-cp',str(a.client_jar.resolve()),'-d',str(classes),*map(str,sources)],check=True,cwd=ROOT)
 executable_jar(classes,asset_root/'browser-spike.jar');copy_checked(a.client_jar,asset_root/'client.jar')
 cache=asset_root/'Cache';cache.mkdir()
 for directory in ('audio','video'):
  base=ROOT/'Client_Base/Cache'/directory
  for file in sorted(base.rglob('*')):
   if file.is_file():copy_checked(file,cache/directory/file.relative_to(base))
 (cache/'config.txt').write_bytes(subprocess.check_output(['git','show','HEAD:Client_Base/Cache/config.txt'],cwd=ROOT))
 (cache/'ip.txt').write_text('132.145.137.142\n');(cache/'port.txt').write_text('43605\n')
 (cache/'discord_inuse.txt').write_text('1\n');(cache/'uid.dat').write_bytes(bytes(8))
 profile='world-builder-configs/installed-client.json';descriptor=document(ROOT/'Client_Base'/profile);relative=safe_path(descriptor['packageRelativePath'])
 copy_checked(ROOT/'Client_Base'/profile,asset_root/profile);copy_checked(ROOT/'Client_Base'/relative/'manifest.json',asset_root/relative/'manifest.json')
 if sha(asset_root/relative/'manifest.json')!=gpu['manifestSha256']:raise ValueError('Installed client package differs from cooked source')
 for row,base in resources:copy_checked(base/row['path'],asset_root/'whole-zone'/row['path'],row)
 copy_checked(gpu_manifest,cache/'whole-zone/manifest.json')
 config={'schemaVersion':1,'manifestBytes':gpu_manifest.stat().st_size,'manifestSha256':gpu_sha,'clientJarSha256':jar_sha,'externalAssets':gpu['externalAssets'],'compact':compact['config']}
 (asset_root/'browser-whole-zone-config.js').unlink();(asset_root/'browser-whole-zone-config.js').write_text('export default Object.freeze('+json.dumps(config,separators=(',',':'))+');\n')
 entries=sorted(f.relative_to(asset_root).as_posix()for f in asset_root.rglob('*')if f.is_file()and f.relative_to(asset_root).parts[0]in('Cache','world-builder','world-builder-configs'))
 (asset_root/'bootstrap-files.txt').write_text('\n'.join(entries)+'\n')
 # The ordinary bootstrap roster must not accidentally absorb retained-RAM files.
 for name in ['manifest.json','source.bin','prepared.bin']:copy_checked(ram_data/name,cache/'whole-ram'/name)
 (asset_root/'bootstrap-whole-ram-files.txt').write_text('Cache/whole-ram/manifest.json\nCache/whole-ram/source.bin\nCache/whole-ram/prepared.bin\n')
 rc={'schemaVersion':1,'manifestBytes':(ram_data/'manifest.json').stat().st_size,'manifestSha256':sha(ram_data/'manifest.json'),'clientJarSha256':jar_sha,'gpuCatalogSha256':gpu_sha,'sourceBundleBytes':ram['sourceBundle']['bytes'],'sourceBundleSha256':ram['sourceBundle']['sha256'],'preparedBytes':ram['prepared']['bytes'],'preparedSha256':ram['prepared']['sha256'],'retainedPrimitiveBytes':ram['prepared']['retainedPrimitiveBytes'],'rawFiles':1797,'recipes':445,'queryCells':1330,'hiddenRoofs':ram['prepared']['hiddenRoofs']}
 (asset_root/'browser-whole-ram-config.js').unlink();(asset_root/'browser-whole-ram-config.js').write_text('export default Object.freeze('+json.dumps(rc,separators=(',',':'))+');\n')
 deployment={'schemaVersion':1,'origin':ORIGIN,'websocket':WEBSOCKET,'preset':PRESET,'buildId':a.build_id,'assetPrefix':'/'+prefix+'/','clientJarSha256':jar_sha,'clientJarBytes':a.client_jar.stat().st_size,'bootstrapJarSha256':sha(asset_root/'browser-spike.jar'),'bootstrapJarBytes':(asset_root/'browser-spike.jar').stat().st_size}
 (asset_root/'browser-deployment-config.js').write_text('export default Object.freeze('+json.dumps(deployment,separators=(',',':'))+');\n')
 copy_checked(ROOT/'LICENSE',web/'LICENSE')
 (web/'SOURCE.txt').write_text('Source code for this build: https://github.com/An-actual-duck/open-rsc-spoiled-milk/tree/'+head+'\nJava runtime: CheerpJ by Leaning Technologies, https://cheerpj.com/ (loaded from its official CDN; not redistributed here).\n')
 html=(ROOT/'tools/browser-gameplay/web/index.html').read_text().replace('src="./session.js"','src="/'+prefix+'/session.js"')
 if html.count('/'+prefix+'/session.js')!=1:raise ValueError('Versioned entry module missing')
 (web/'index.html').write_text(html)
 inventory=[]
 for file in sorted(web.rglob('*')):
  if not file.is_file():continue
  relative=file.relative_to(web).as_posix()
  if file.name in DENIED_NAMES:raise ValueError('Private/user data in delivery')
  if file.suffix in ('.js','.html','.css','.txt','.json')and file.stat().st_size<16*1024*1024 and any(token in file.read_bytes()for token in SECRET_TOKENS):raise ValueError('Test login in public delivery')
  inventory.append({'path':relative,'bytes':file.stat().st_size,'sha256':sha(file)})
 sums=''.join(row['sha256']+'  '+row['path']+'\n'for row in inventory);(web/'SHA256SUMS').write_text(sums)
 release={'schemaVersion':1,'buildId':a.build_id,'frontendSourceCommit':head,'clientSourceCommit':build['clientSourceCommit'],'jarSha256':jar_sha,'origin':ORIGIN,'websocket':WEBSOCKET,'preset':PRESET,'assetPrefix':deployment['assetPrefix'],'files':inventory,'sha256SumsSha256':sha(web/'SHA256SUMS'),'canonicalGpuManifestSha256':gpu_sha,'compactManifestSha256':compact['config']['manifestSha256'],'ramManifestSha256':sha(ram_data/'manifest.json'),'omittedCanonicalGeometryFiles':len(gpu['chunks']),'deliveredCompactGeometryFiles':len(compact['files'])-1,'sourceAndCookBindingsVerified':True,'credentialsCopied':False,'processesStarted':False}
 (web/'public-release.json').write_text(json.dumps(release,indent=2)+'\n')
 receipt={**release,'web':str(web),'publicReleaseSha256':sha(web/'public-release.json'),'clientReceiptSha256':sha(a.client_receipt),'gpuReceiptSha256':sha(Path(a.bake_run)/'bake-receipt.json'),'compactReceiptSha256':compact['receiptSha256'],'ramReceiptSha256':sha(Path(a.ram_run)/'ram-bake-receipt.json'),'bootstrapSources':{str(f.relative_to(ROOT)):sha(f)for f in sources},'deployment':deployment,'published':False}
 (run/'package-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'web':str(web),'receipt':str(run/'package-receipt.json'),'publicReleaseSha256':receipt['publicReleaseSha256'],'files':len(inventory),'bytes':sum(row['bytes']for row in inventory),'published':False}))
if __name__=='__main__':main()
