#!/usr/bin/env node
/** Offline immutable derivative. No Java/runtime/source rebinding or GPU work. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {validateWholeZoneManifest,validateWholeZoneSource,decodeWholeZoneChunk} from '../web/browser-whole-zone.js';
import {COMPACT_MAGIC,COMPACT_CONTRACT,COMPACT_GEOMETRY,packCompactVertices,verifyCompactVertices} from '../web/browser-geometry-layout.js';
import {selectCompactCatalog} from '../web/browser-compact-catalog.js';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const args=process.argv.slice(2);if(args.length!==4||args[0]!=='--name'||args[2]!=='--bake-run'||!/^[A-Za-z0-9_-]+$/.test(args[1]))throw new Error('Usage: compact.mjs --name FRESH_NAME --bake-run COMPLETE_CANONICAL_BAKE');
const bake=path.resolve(args[3]),canonical=path.join(bake,'whole-zone'),rawManifest=fs.readFileSync(path.join(canonical,'manifest.json')),catalogHash=sha(rawManifest);
const receipt=JSON.parse(fs.readFileSync(path.join(bake,'bake-receipt.json'))),m=validateWholeZoneManifest(JSON.parse(rawManifest));
if(!receipt.passed||!receipt.complete||!receipt.fullRequested||receipt.requestedSectorLimit!==1330||receipt.manifestSha256!==catalogHash||receipt.clientJarSha256!==m.clientJarSha256)throw new Error('Use an exact complete canonical bake receipt');
const sourceRaw=fs.readFileSync(path.join(canonical,'source-manifest.json'));if(sha(sourceRaw)!==m.manifestSha256)throw new Error('Canonical original source manifest hash');validateWholeZoneSource(m,JSON.parse(sourceRaw));
const category=spawnSync('python3',[path.join(root,'scripts/workspace-layout.py'),'path','builds','browser-compact-geometry','--mkdir'],{encoding:'utf8',cwd:root});if(category.status!==0)throw new Error(category.stderr);
const run=path.join(category.stdout.trim(),args[1]);fs.mkdirSync(run);const out=path.join(run,'compact-zone');fs.mkdirSync(out);fs.mkdirSync(path.join(out,'compact-chunks'));
const result={passed:false,complete:false,CPUOnly:true,canonicalBakeReceiptSha256:sha(fs.readFileSync(path.join(bake,'bake-receipt.json'))),canonicalCatalogSha256:catalogHash,clientJarSha256:m.clientJarSha256,driverSourceCommit:spawnSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).stdout.trim(),driverFiles:{},sourceManifestSha256:m.manifestSha256,canonicalFileBytes:0,compactFileBytes:0,canonicalGeometryGpuBytes:0,compactGeometryGpuBytes:0,vertices:0,attributeFloat32Comparisons:0,resources:[],scope:'Exact source-bound browser geometry derivative. No FPS, GPU, totalVRAM or browserRAM claim. Canonical GPU catalogue and CPU recipe bytes unchanged.'};
for(const name of ['tools/browser-gameplay/whole-zone/compact.mjs','tools/browser-gameplay/web/browser-geometry-layout.js','tools/browser-gameplay/web/browser-compact-catalog.js','tools/browser-gameplay/web/browser-whole-zone.js']){
 const actual=sha(fs.readFileSync(path.join(root,name))),committed=spawnSync('git',['show','HEAD:'+name],{cwd:root});if(committed.status!==0||sha(committed.stdout)!==actual)throw new Error('Checkpoint exact compact driver source before full conversion: '+name);result.driverFiles[name]=actual;
}
result.driverOwnedSourceMatchesCommit=true;
const start=performance.now();
try{
 for(const c of m.chunks){
  const file=path.join(canonical,c.path);if(fs.lstatSync(file).isSymbolicLink())throw new Error('Canonical input must be a regular file');const raw=fs.readFileSync(file);if(raw.length!==c.bytes||sha(raw)!==c.sha256)throw new Error('Canonical chunk mismatch '+c.id);
  const owned=raw.buffer.slice(raw.byteOffset,raw.byteOffset+raw.byteLength),planes=decodeWholeZoneChunk(owned,c),vertices=packCompactVertices(planes.vertices);result.attributeFloat32Comparisons+=verifyCompactVertices(planes.vertices,vertices);
  const data=Buffer.alloc(24+vertices.byteLength+planes.indices.byteLength+planes.batches.byteLength);[COMPACT_MAGIC,1,48,c.vertexCount,c.indexCount,c.batchIntCount].forEach((word,n)=>data.writeUInt32LE(word,n*4));data.set(vertices,24);data.set(new Uint8Array(planes.indices.buffer,planes.indices.byteOffset,planes.indices.byteLength),24+vertices.byteLength);data.set(new Uint8Array(planes.batches.buffer,planes.batches.byteOffset,planes.batches.byteLength),24+vertices.byteLength+planes.indices.byteLength);
  const relative='compact-chunks/c'+c.id+'.bin';fs.writeFileSync(path.join(out,relative),data,{flag:'wx',mode:0o444});
  const row={id:c.id,geometryLayout:COMPACT_GEOMETRY,canonicalSha256:c.sha256,canonicalBytes:c.bytes,vertexCount:c.vertexCount,indexCount:c.indexCount,batchIntCount:c.batchIntCount,bytes:data.byteLength,sha256:sha(data),path:relative};result.resources.push(row);result.vertices+=c.vertexCount;result.canonicalFileBytes+=raw.length;result.compactFileBytes+=data.length;result.canonicalGeometryGpuBytes+=planes.vertices.byteLength+planes.indices.byteLength;result.compactGeometryGpuBytes+=vertices.byteLength+planes.indices.byteLength;
 }
 const sidecar={schemaVersion:1,type:'spoiled-milk-compact-geometry',complete:true,canonicalCatalogSha256:catalogHash,clientJarSha256:m.clientJarSha256,sourceManifestSha256:m.manifestSha256,packageFingerprintSha256:m.packageFingerprintSha256,settings:m.settings,layout:COMPACT_CONTRACT,vertexCount:result.vertices,geometryGpuBytes:result.compactGeometryGpuBytes,resourceFileBytes:result.compactFileBytes,resources:result.resources};selectCompactCatalog(m,sidecar,catalogHash);
 const text=JSON.stringify(sidecar,null,2)+'\n';fs.writeFileSync(path.join(out,'compact-manifest.json'),text,{flag:'wx',mode:0o444});
 Object.assign(result,{passed:true,complete:true,manifestSha256:sha(text),manifestBytes:Buffer.byteLength(text),geometryLayout:COMPACT_GEOMETRY,output:out});
}finally{result.elapsedSeconds=(performance.now()-start)/1000;fs.writeFileSync(path.join(run,'compact-receipt.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});}
console.log(JSON.stringify({...result,resources:result.resources.length},null,2));
