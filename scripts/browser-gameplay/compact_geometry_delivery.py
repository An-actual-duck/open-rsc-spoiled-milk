"""Strict source-bound compact derivative admission; never starts a service."""
import hashlib,json,struct,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def sha(path):
 h=hashlib.sha256()
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):h.update(block)
 return h.hexdigest()

def admit_compact_geometry(compact_run,canonical_root,canonical):
 run=Path(compact_run).resolve();data=run/'compact-zone';receipt_path=run/'compact-receipt.json'
 r=json.loads(receipt_path.read_text());manifest=data/'compact-manifest.json';m=json.loads(manifest.read_text())
 if not(r.get('passed')is True and r.get('complete')is True and r.get('CPUOnly')is True and r.get('driverOwnedSourceMatchesCommit')is True and r.get('canonicalCatalogSha256')==sha(Path(canonical_root)/'manifest.json') and r.get('clientJarSha256')==canonical['clientJarSha256'] and r.get('manifestSha256')==sha(manifest) and r.get('manifestBytes')==manifest.stat().st_size and r.get('geometryLayout')=='compact48-v1'):
  raise ValueError('Exact complete compact derivative receipt required')
 expected=['tools/browser-gameplay/whole-zone/compact.mjs','tools/browser-gameplay/web/browser-geometry-layout.js','tools/browser-gameplay/web/browser-compact-catalog.js','tools/browser-gameplay/web/browser-whole-zone.js']
 if set(r.get('driverFiles',{}))!=set(expected)or any(sha(ROOT/name)!=r['driverFiles'][name]for name in expected):raise ValueError('Compact converter/decoder source changed; produce a fresh verified derivative')
 if r.get('vertices')!=sum(c['vertexCount']for c in canonical['chunks'])or r.get('attributeFloat32Comparisons')!=r['vertices']*23:raise ValueError('Incomplete compact float32 equivalence proof')
 check="import fs from'node:fs';import{validateWholeZoneManifest}from"+json.dumps((ROOT/'tools/browser-gameplay/web/browser-whole-zone.js').as_uri())+";import{selectCompactCatalog}from"+json.dumps((ROOT/'tools/browser-gameplay/web/browser-compact-catalog.js').as_uri())+";const a=validateWholeZoneManifest(JSON.parse(fs.readFileSync(process.argv[1])));selectCompactCatalog(a,JSON.parse(fs.readFileSync(process.argv[2])),process.argv[3]);"
 subprocess.run(['node','--input-type=module','-e',check,str(Path(canonical_root)/'manifest.json'),str(manifest),r['canonicalCatalogSha256']],check=True,cwd=ROOT)
 if r['resources']!=m['resources']or r['compactFileBytes']!=m['resourceFileBytes']or r['compactGeometryGpuBytes']!=m['geometryGpuBytes']:raise ValueError('Compact receipt/manifest inventory mismatch')
 files=[{'path':'compact-manifest.json','bytes':manifest.stat().st_size,'sha256':sha(manifest)}]
 for c in m['resources']:
  path=Path(c['path']);source=data/path
  if path.is_absolute()or'..'in path.parts or source.is_symlink()or not source.is_file()or source.stat().st_size!=c['bytes']or sha(source)!=c['sha256']:raise ValueError('Compact resource payload mismatch')
  with source.open('rb')as stream:header=stream.read(24)
  if struct.unpack('<6I',header)!=(0x575a4332,1,48,c['vertexCount'],c['indexCount'],c['batchIntCount']):raise ValueError('Compact packed header mismatch')
  files.append({k:c[k]for k in ['path','bytes','sha256']})
 return {'root':data,'files':files,'receiptSha256':sha(receipt_path),'config':{'schemaVersion':1,'geometryLayout':'compact48-v1','canonicalCatalogSha256':r['canonicalCatalogSha256'],'manifestSha256':sha(manifest),'manifestBytes':manifest.stat().st_size}}
