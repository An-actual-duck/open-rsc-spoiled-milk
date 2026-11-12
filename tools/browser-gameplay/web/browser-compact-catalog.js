import {COMPACT_GEOMETRY,COMPACT_CONTRACT,geometryBytes} from './browser-geometry-layout.js';
const HASH=/^[0-9a-f]{64}$/;
export const MAX_COMPACT_MANIFEST=8*1024*1024;
function integer(n,min,max,label){if(!Number.isSafeInteger(n)||n<min||n>max)throw new Error('Compact '+label+' bounds');return n;}
function hash(s){if(typeof s!=='string'||!HASH.test(s))throw new Error('Compact source hash');return s;}
export function validateCompactConfig(c,canonicalHash){
  if(!c||c.schemaVersion!==1||c.geometryLayout!==COMPACT_GEOMETRY||c.canonicalCatalogSha256!==canonicalHash)throw new Error('Compact staged source/layout binding');
  return Object.freeze({schemaVersion:1,geometryLayout:COMPACT_GEOMETRY,canonicalCatalogSha256:hash(c.canonicalCatalogSha256),manifestSha256:hash(c.manifestSha256),manifestBytes:integer(c.manifestBytes,1,MAX_COMPACT_MANIFEST,'manifest bytes')});
}
/** The canonical Java/catalogue identity stays unchanged. Only browser GPU-owned
 * resource descriptors are replaced, after exact one-to-one derivative binding. */
export function selectCompactCatalog(m,c,canonicalHash){
  if(!c||c.schemaVersion!==1||c.type!=='spoiled-milk-compact-geometry'||c.complete!==true||c.canonicalCatalogSha256!==canonicalHash||c.clientJarSha256!==m.clientJarSha256||c.sourceManifestSha256!==m.manifestSha256||c.packageFingerprintSha256!==m.packageFingerprintSha256||JSON.stringify(c.settings)!==JSON.stringify(m.settings)||JSON.stringify(c.layout)!==JSON.stringify(COMPACT_CONTRACT))throw new Error('Compact derivative identity/contract mismatch');
  hash(c.canonicalCatalogSha256);if(!Array.isArray(c.resources)||c.resources.length!==m.chunks.length)throw new Error('Compact complete resource roster');
  const expected=new Map(m.chunks.map(r=>[r.id,r])),seen=new Set();let gpuBytes=0,fileBytes=0,vertices=0;
  const resources=c.resources.map(r=>{
    const original=r&&expected.get(r.id);if(!original||seen.has(r.id))throw new Error('Compact substituted/duplicate resource');seen.add(r.id);
    if(r.canonicalSha256!==original.sha256||r.canonicalBytes!==original.bytes||r.vertexCount!==original.vertexCount||r.indexCount!==original.indexCount||r.batchIntCount!==original.batchIntCount)throw new Error('Compact original resource binding mismatch');
    if(r.geometryLayout!==COMPACT_GEOMETRY||r.path!=='compact-chunks/c'+r.id+'.bin')throw new Error('Compact resource path/layout');
    const bytes=integer(r.bytes,24,64*1024*1024,'resource bytes');if(bytes!==24+r.vertexCount*48+r.indexCount*4+r.batchIntCount*4)throw new Error('Compact resource shape');
    const out=Object.freeze({...original,path:r.path,sha256:hash(r.sha256),bytes,geometryLayout:COMPACT_GEOMETRY,canonicalSha256:original.sha256,canonicalBytes:original.bytes});
    gpuBytes+=geometryBytes(out);fileBytes+=bytes;vertices+=r.vertexCount;return out;
  });
  const declared=integer(c.vertexCount,0,2147483647,'vertex count');if(vertices!==declared||integer(c.geometryGpuBytes,0,3*1024*1024*1024,'GPU bytes')!==gpuBytes||integer(c.resourceFileBytes,0,3*1024*1024*1024,'file bytes')!==fileBytes)throw new Error('Compact aggregate accounting mismatch');
  const byId=new Map(resources.map(r=>[r.id,r]));
  return Object.freeze({...m,chunks:Object.freeze(m.chunks.map(r=>byId.get(r.id))),canonicalGpuBytes:m.gpuBytes,gpuBytes:gpuBytes+m.textures.reduce((n,t)=>n+t.bytes,0),fileBytes:fileBytes+m.textures.reduce((n,t)=>n+t.bytes,0),geometryLayout:COMPACT_GEOMETRY});
}
