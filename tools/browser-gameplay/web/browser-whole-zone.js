import {WHOLE_ZONE_SOURCE,WHOLE_ZONE_GPU_BYTES,WHOLE_ZONE_MAX_CHUNKS} from './browser-whole-zone-policy.js';
/** Source-bound immutable packed resources. Only the private Worker calls this loader. */
const HASH=/^[0-9a-f]{64}$/,MAX_RESOURCE=64*1024*1024,MAX_MANIFEST=4*1024*1024,PIN_BASE=1000000;
const MAGIC=0x575a4331;
function integer(n,min,max,label){if(!Number.isSafeInteger(n)||n<min||n>max)throw new Error('Whole zone '+label+' bounds');return n;}
function hash(value,label){if(typeof value!=='string'||!HASH.test(value))throw new Error('Whole zone '+label+' hash');return value;}
function path(value){if(typeof value!=='string'||value.length>256||!/^([a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_.-]+$/.test(value)||value.split('/').some(x=>x==='.'||x==='..'))throw new Error('Whole zone resource path');return value;}
function levels(value,expected,label){if(!value||Object.keys(value).length!==3||[0,1,2].some(n=>value[n]!==expected[n]))throw new Error('Incomplete whole zone '+label);}
export function validateWholeZoneManifest(m){
  if(!m||m.schemaVersion!==1||m.type!=='spoiled-milk-whole-overworld-zone'||m.packageFingerprintSha256!==WHOLE_ZONE_SOURCE.fingerprint||m.manifestSha256!==WHOLE_ZONE_SOURCE.manifestSha256)throw new Error('Whole zone source identity mismatch');
  if(!Array.isArray(m.settings)||m.settings.length!==4||m.settings.some(x=>!Number.isSafeInteger(x)||x<-2147483648||x>2147483647))throw new Error('Whole zone settings declaration');
  hash(m.textureCatalogSha256,'texture catalog');levels(m.scope?.sectors,WHOLE_ZONE_SOURCE.sectors,'sector scope');levels(m.scope?.placements,WHOLE_ZONE_SOURCE.placements,'object scope');
  if(!Array.isArray(m.chunks)||!m.chunks.length||m.chunks.length>WHOLE_ZONE_MAX_CHUNKS-512||!Array.isArray(m.textures)||!m.textures.length||m.textures.length>2048)throw new Error('Whole zone catalog length');
  const ids=new Set(),keys=new Set();let gpuBytes=0,fileBytes=0;
  const chunks=m.chunks.map(c=>{
    if(!c||typeof c!=='object')throw new Error('Whole zone chunk declaration');
    const id=integer(c.id,PIN_BASE,2147483647,'chunk ID'),plane=integer(c.plane,0,2,'plane'),role=integer(c.role,0,1,'role'),cellX=integer(c.cellX,-100000,100000,'cellX'),cellY=integer(c.cellY,-100000,100000,'cellY');
    if(c.sourceKind!==(role===0?'world':'objects')||c.cellSize!==(role===0?48:24))throw new Error('Whole zone cell representation');
    if(role===1&&(typeof c.canonicalContentKey!=='string'||!/^([0-9a-f]{16})$/.test(c.canonicalContentKey)))throw new Error('Whole zone object key');
    integer(c.placementCount,0,100000,'placement count');
    if(!Array.isArray(c.bbox)||c.bbox.length!==6||c.bbox.some(x=>!Number.isFinite(x)))throw new Error('Whole zone bounds');
    if(ids.has(id)||keys.has(plane+':'+role+':'+cellX+':'+cellY))throw new Error('Duplicate whole zone chunk');ids.add(id);keys.add(plane+':'+role+':'+cellX+':'+cellY);
    const vertices=integer(c.vertexCount,0,524288,'vertices'),indices=integer(c.indexCount,0,1572864,'indices'),batches=integer(c.batchIntCount,0,1572864,'batches');
    if(indices%3||batches%5)throw new Error('Whole zone triangle/batch shape');
    const bytes=integer(c.bytes,16,MAX_RESOURCE,'resource bytes');if(bytes!==16+vertices*112+indices*4+batches*4)throw new Error('Whole zone packed resource size');
    integer(c.originX,-2147483648,2147483647,'originX');integer(c.originZ,-2147483648,2147483647,'originZ');integer(c.visibilityMask,0,2147483647,'visibility');
    gpuBytes+=vertices*112+indices*4;fileBytes+=bytes;
    return Object.freeze({...c,id,plane,role,cellX,cellY,path:path(c.path),sha256:hash(c.sha256,'resource')});
  });
  const textureIds=new Set();const textures=m.textures.map(t=>{
    if(!t||typeof t!=='object'||typeof t.transparent!=='boolean')throw new Error('Whole zone texture declaration');
    const id=integer(t.id,0,2147483647,'texture ID'),width=integer(t.width,1,16384,'texture width'),height=integer(t.height,1,16384,'texture height');
    if(width*height>262144||textureIds.has(id)||t.bytes!==width*height*4)throw new Error('Whole zone texture shape');textureIds.add(id);gpuBytes+=t.bytes;fileBytes+=t.bytes;
    return Object.freeze({...t,id,width,height,path:path(t.path),sha256:hash(t.sha256,'texture')});
  });
  // Reserve the existing local arena, texture updates and metadata separately.
  if(gpuBytes>WHOLE_ZONE_GPU_BYTES-256*1024*1024)throw new Error('Whole zone immutable resource budget');
  return Object.freeze({...m,settings:Object.freeze(m.settings.slice()),chunks:Object.freeze(chunks),textures:Object.freeze(textures),gpuBytes,fileBytes});
}
export function decodeWholeZoneChunk(buffer,c){
  if(!(buffer instanceof ArrayBuffer)||buffer.byteLength!==c.bytes)throw new Error('Whole zone chunk byte length');
  const d=new DataView(buffer);if(d.getUint32(0,true)!==MAGIC||d.getUint32(4,true)!==c.vertexCount||d.getUint32(8,true)!==c.indexCount||d.getUint32(12,true)!==c.batchIntCount)throw new Error('Whole zone chunk header');
  const little=new Uint8Array(new Uint32Array([1]).buffer)[0]===1;if(!little)throw new Error('Whole zone little-endian host required');
  return {vertices:new Float32Array(buffer,16,c.vertexCount*28),indices:new Int32Array(buffer,16+c.vertexCount*112,c.indexCount),batches:new Int32Array(buffer,16+c.vertexCount*112+c.indexCount*4,c.batchIntCount)};
}
export async function fetchWholeZoneBytes(url,expected,{fetcher=fetch,signal,maximum=MAX_RESOURCE}={}){
  integer(expected,1,maximum,'fetch bytes');const response=await fetcher(url,{signal,cache:'no-store'});if(!response.ok)throw new Error('Whole zone resource HTTP '+response.status);
  const advertised=response.headers?.get?.('content-length');if(advertised!==null&&advertised!==undefined&&Number(advertised)!==expected)throw new Error('Whole zone HTTP length mismatch');
  if(!response.body?.getReader)throw new Error('Whole zone bounded streaming unavailable');
  const data=new Uint8Array(expected),reader=response.body.getReader();let at=0;
  try{for(;;){const {done,value}=await reader.read();if(done)break;if(!(value instanceof Uint8Array)||at+value.byteLength>expected)throw new Error('Whole zone response exceeds declaration');data.set(value,at);at+=value.byteLength;}if(at!==expected)throw new Error('Truncated whole zone resource');}
  catch(error){try{await reader.cancel(error);}catch(_){}throw error;}finally{reader.releaseLock();}
  return data.buffer;
}
async function digest(buffer){return [...new Uint8Array(await crypto.subtle.digest('SHA-256',buffer))].map(x=>x.toString(16).padStart(2,'0')).join('');}
/** No payload view survives its upload callback. The returned catalog retains metadata only. */
export async function loadWholeZone({baseURL,manifestBytes,manifestSha256,fetcher=fetch,uploadChunk,uploadTexture,finishGpu,stats,signal,progress=()=>{}}){
  integer(manifestBytes,1,MAX_MANIFEST,'manifest bytes');hash(manifestSha256,'catalog');const base=new URL(baseURL,import.meta.url);
  const manifestRaw=await fetchWholeZoneBytes(new URL('manifest.json',base),manifestBytes,{fetcher,signal,maximum:MAX_MANIFEST});if(await digest(manifestRaw)!==manifestSha256)throw new Error('Whole zone catalog digest mismatch');
  const m=validateWholeZoneManifest(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(manifestRaw)));Object.assign(stats,{phase:'loading',ready:false,manifestSha256,expectedPinnedChunks:m.chunks.length,expectedPinnedBytes:m.gpuBytes,sourceFingerprint:m.packageFingerprintSha256,sourceManifestSha256:m.manifestSha256,sourceSectors:1330,sourceObjectPlacements:24580,downloadedBytes:manifestBytes,preparedResources:0,pinnedChunks:0,pinnedBytes:0,textureBytes:0,startupGpuCompleted:false});
  for(const t of m.textures){const raw=await fetchWholeZoneBytes(new URL(t.path,base),t.bytes,{fetcher,signal});if(await digest(raw)!==t.sha256)throw new Error('Whole zone texture digest mismatch');await uploadTexture(t,new Int32Array(raw));stats.downloadedBytes+=raw.byteLength;stats.textureBytes+=raw.byteLength;stats.preparedResources++;}
  for(const c of m.chunks){const raw=await fetchWholeZoneBytes(new URL(c.path,base),c.bytes,{fetcher,signal});if(await digest(raw)!==c.sha256)throw new Error('Whole zone chunk digest mismatch');const planes=decodeWholeZoneChunk(raw,c);await uploadChunk(c,planes);stats.downloadedBytes+=raw.byteLength;stats.pinnedChunks++;stats.pinnedBytes+=planes.vertices.byteLength+planes.indices.byteLength;stats.preparedResources++;if(stats.pinnedChunks%64===0)progress(stats.pinnedChunks,m.chunks.length);}
  if(stats.pinnedChunks!==m.chunks.length||stats.pinnedBytes+stats.textureBytes!==m.gpuBytes)throw new Error('Incomplete whole zone resource residency');
  await finishGpu();Object.assign(stats,{phase:'ready',ready:true,startupGpuCompleted:true});return m;
}
