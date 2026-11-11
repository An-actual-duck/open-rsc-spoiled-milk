import {WHOLE_ZONE_SOURCE,WHOLE_ZONE_GPU_BYTES,WHOLE_ZONE_MAX_CHUNKS} from './browser-whole-zone-policy.js';
/** Source-bound immutable packed resources. Only the private Worker calls this loader. */
const HASH=/^[0-9a-f]{64}$/,MAX_RESOURCE=64*1024*1024,MAX_MANIFEST=32*1024*1024,PIN_BASE=1000000;
const MAGIC=0x575a4331;
const TAP=Object.getPrototypeOf(Int32Array.prototype),TGET=name=>Object.getOwnPropertyDescriptor(TAP,name).get;
const ABYTES=Object.getOwnPropertyDescriptor(ArrayBuffer.prototype,'byteLength').get;
const TBUFFER=TGET('buffer'),TOFFSET=TGET('byteOffset'),TBYTES=TGET('byteLength'),TTAG=Object.getOwnPropertyDescriptor(TAP,Symbol.toStringTag).get;
function ints(value){try{if(TTAG.call(value)!=='Int32Array')throw new Error();const buffer=TBUFFER.call(value);ABYTES.call(buffer);return new Int32Array(buffer,TOFFSET.call(value),TBYTES.call(value)/4);}catch(_){throw new Error('Whole zone requires a genuine int32 plane');}}
function integer(n,min,max,label){if(!Number.isSafeInteger(n)||n<min||n>max)throw new Error('Whole zone '+label+' bounds');return n;}
function hash(value,label){if(typeof value!=='string'||!HASH.test(value))throw new Error('Whole zone '+label+' hash');return value;}
function path(value){if(typeof value!=='string'||value.length>256||!/^([a-zA-Z0-9_-]+\/)*[a-zA-Z0-9_.-]+$/.test(value)||value.split('/').some(x=>x==='.'||x==='..'))throw new Error('Whole zone resource path');return value;}
function levels(value,expected,label){if(!value||Object.keys(value).length!==3||[0,1,2].some(n=>value[n]!==expected[n]))throw new Error('Incomplete whole zone '+label);}
const EXTERNAL_ASSETS=Object.freeze(['Authentic_Landscape.orsc','Authentic_Sprites.orsc','Custom_Sprites.osar','library.orsc','models.orsc'].map(n=>'Cache/video/'+n));
function externalAssets(value){
  if(!Array.isArray(value)||value.length!==EXTERNAL_ASSETS.length)throw new Error('Whole zone external asset roster');let total=0;
  const out=value.map((v,n)=>{if(!v||v.path!==EXTERNAL_ASSETS[n])throw new Error('Whole zone external asset identity');integer(v.bytes,1,16*1024*1024,'external asset bytes');hash(v.sha256,'external asset');total+=v.bytes;return Object.freeze({path:v.path,bytes:v.bytes,sha256:v.sha256});});
  if(total>64*1024*1024)throw new Error('Whole zone external asset budget');return Object.freeze(out);
}
export function validateWholeZoneConfig(c){
  if(!c||c.schemaVersion!==1)throw new Error('Private whole zone catalog not staged');
  integer(c.manifestBytes,1,MAX_MANIFEST,'catalog bytes');hash(c.manifestSha256,'catalog');hash(c.clientJarSha256,'client JAR');return Object.freeze({schemaVersion:1,manifestBytes:c.manifestBytes,manifestSha256:c.manifestSha256,clientJarSha256:c.clientJarSha256,externalAssets:externalAssets(c.externalAssets)});
}
export function validateWholeZoneTexture(expected,width,height,input,transparent){
  const pixels=ints(input);if(!expected||expected.length!==5||expected[1]!==width||expected[2]!==height||expected[4]!==Number(transparent)||pixels.length!==width*height)throw new Error('Pinned whole zone texture layout changed');
  let count=0,zeros=0,r=0,g=0,b=0;for(let n=0;n<pixels.length;n++){const c=pixels[n];if(c===0){zeros++;continue;}count++;r+=c>>>24;g+=(c>>>16)&255;b+=(c>>>8)&255;}
  const average=count?((Math.floor(r/count)<<16)|(Math.floor(g/count)<<8)|Math.floor(b/count)):0;
  if(average!==expected[3]||Number(zeros>0)!==expected[4])throw new Error('Pinned whole zone texture material changed');return true;
}
export function validateWholeZoneLive(m,mapIdentity,settings,materials){
  settings=ints(settings);materials=ints(materials);
  if(mapIdentity!==WHOLE_ZONE_SOURCE.manifestSha256||settings.length!==4||settings.some((x,n)=>x!==m.settings[n])||materials.length!==m.materialMetadata.length||materials.some((x,n)=>x!==m.materialMetadata[n]))throw new Error('Whole zone live source/settings/material mismatch');
  return true;
}
export function validateWholeZoneManifest(m){
  if(!m||m.schemaVersion!==1||m.complete!==true||m.type!=='spoiled-milk-whole-overworld-zone'||m.packageFingerprintSha256!==WHOLE_ZONE_SOURCE.fingerprint||m.manifestSha256!==WHOLE_ZONE_SOURCE.manifestSha256)throw new Error('Whole zone source identity mismatch');
  if(!Array.isArray(m.settings)||m.settings.length!==4||m.settings.some(x=>!Number.isSafeInteger(x)||x<-2147483648||x>2147483647))throw new Error('Whole zone settings declaration');
  hash(m.clientJarSha256,'baked client JAR');hash(m.textureCatalogSha256,'texture catalog');levels(m.scope?.sectors,WHOLE_ZONE_SOURCE.sectors,'sector scope');levels(m.scope?.placements,WHOLE_ZONE_SOURCE.placements,'object scope');
  if(!Array.isArray(m.chunks)||!m.chunks.length||m.chunks.length>WHOLE_ZONE_MAX_CHUNKS-512||!Array.isArray(m.textures)||!m.textures.length||m.textures.length>2048)throw new Error('Whole zone catalog length');
  if(!Array.isArray(m.objectCells)||m.objectCells.length!==5320||!Array.isArray(m.worldCells)||m.worldCells.length!==1330)throw new Error('Whole zone source cell roster');
  const objectKeys=new Map(),worldKeys=new Set(),placementCounts=[0,0,0];
  const objectCells=m.objectCells.map(c=>{
    integer(c.plane,0,2,'object plane');integer(c.cellX,-100000,100000,'object cellX');integer(c.cellY,-100000,100000,'object cellY');integer(c.placementCount,0,100000,'cell placements');
    if(typeof c.emptyCanonicalContentKey!=='string'||!/^([0-9a-f]{16})$/.test(c.emptyCanonicalContentKey)||!Array.isArray(c.identities)||c.identities.length!==c.placementCount*7||c.identities.some(x=>!Number.isSafeInteger(x)||x<-2147483648||x>2147483647))throw new Error('Whole zone authored object identities');
    const key=c.plane+':'+c.cellX+':'+c.cellY;if(objectKeys.has(key)||typeof c.canonicalContentKey!=='string'||!/^([0-9a-f]{16})$/.test(c.canonicalContentKey))throw new Error('Whole zone object cell key');
    const owned=Object.freeze({...c,identities:Object.freeze(c.identities.slice())});objectKeys.set(key,owned);placementCounts[c.plane]+=c.placementCount;return owned;
  });
  if(placementCounts.some((n,level)=>n!==WHOLE_ZONE_SOURCE.placements[level]))throw new Error('Whole zone placement coverage');
  const worldCells=m.worldCells.map(c=>{integer(c.plane,0,2,'world plane');integer(c.cellX,-100000,100000,'world cellX');integer(c.cellY,-100000,100000,'world cellY');hash(c.sourceSha256,'world cell');const key=c.plane+':'+c.cellX+':'+c.cellY;if(worldKeys.has(key))throw new Error('Duplicate whole zone world cell');worldKeys.add(key);return Object.freeze({...c});});
  const ids=new Set(),keys=new Set();let gpuBytes=0,fileBytes=0,effectScalars=0;
  const chunks=m.chunks.map(c=>{
    if(!c||typeof c!=='object')throw new Error('Whole zone chunk declaration');
    const id=integer(c.id,PIN_BASE,2147483647,'chunk ID'),plane=integer(c.plane,0,2,'plane'),role=integer(c.role,0,1,'role'),cellX=integer(c.cellX,-100000,100000,'cellX'),cellY=integer(c.cellY,-100000,100000,'cellY');
    if(c.sourceKind!==(role===0?'world':'objects')||c.cellSize!==(role===0?48:24))throw new Error('Whole zone cell representation');
    if(role===1&&(typeof c.canonicalContentKey!=='string'||!/^([0-9a-f]{16})$/.test(c.canonicalContentKey)))throw new Error('Whole zone object key');
    integer(c.placementCount??0,0,100000,'placement count');
    const sourceKey=plane+':'+cellX+':'+cellY;if(role===0?!worldKeys.has(sourceKey):!objectKeys.has(sourceKey))throw new Error('Whole zone chunk lacks source cell');
    if(role===1&&(objectKeys.get(sourceKey).canonicalContentKey!==c.canonicalContentKey||objectKeys.get(sourceKey).placementCount!==c.placementCount))throw new Error('Whole zone object resource/cell disagreement');
    if(!Array.isArray(c.bbox)||c.bbox.length!==6||c.bbox.some(x=>!Number.isFinite(x)))throw new Error('Whole zone bounds');
    for(let n=0;n<3;n++)if(c.bbox[n]>c.bbox[n+3])throw new Error('Whole zone reversed bounds');
    const effects={};for(const [name,size]of [['shadowCasters',10],['glowEmitters',7]]){const rows=c[name];if(!Array.isArray(rows)||rows.length>8192||(effectScalars+=rows.length*size)>2000000)throw new Error('Whole zone effect budget');effects[name]=Object.freeze(rows.map(row=>{if(!Array.isArray(row)||row.length!==size||row.some(x=>!Number.isSafeInteger(x)||x<-2147483648||x>2147483647))throw new Error('Whole zone effect descriptor');return Object.freeze(row.slice());}));}
    if(ids.has(id)||keys.has(plane+':'+role+':'+cellX+':'+cellY))throw new Error('Duplicate whole zone chunk');ids.add(id);keys.add(plane+':'+role+':'+cellX+':'+cellY);
    const vertices=integer(c.vertexCount,0,524288,'vertices'),indices=integer(c.indexCount,0,1572864,'indices'),batches=integer(c.batchIntCount,0,1572864,'batches');
    if(indices%3||batches%5)throw new Error('Whole zone triangle/batch shape');
    const bytes=integer(c.bytes,16,MAX_RESOURCE,'resource bytes');if(bytes!==16+vertices*112+indices*4+batches*4)throw new Error('Whole zone packed resource size');
    integer(c.originX,-2147483648,2147483647,'originX');integer(c.originZ,-2147483648,2147483647,'originZ');integer(c.visibilityMask,0,2147483647,'visibility');
    gpuBytes+=vertices*112+indices*4;fileBytes+=bytes;
    return Object.freeze({...c,...effects,bbox:Object.freeze(c.bbox.slice()),id,plane,role,cellX,cellY,path:path(c.path),sha256:hash(c.sha256,'resource')});
  });
  const resources=new Map(chunks.map(c=>[c.id,c])),covered=new Set();
  for(const [cells,kind]of [[worldCells,'world'],[objectCells,'objects']])for(const cell of cells){
    integer(cell.vertexCount,0,524288,'cell vertices');integer(cell.indexCount,0,1572864,'cell indices');
    if(kind==='world'&&cell.plane===0&&(!cell.vertexCount||!cell.indexCount))throw new Error('Whole zone L0 terrain cannot be empty');
    if(cell.resourceId===null){if(cell.vertexCount||cell.indexCount||kind==='world'&&cell.plane===0||kind==='objects'&&cell.placementCount>0)throw new Error('Whole zone geometry resource omitted');continue;}
    integer(cell.resourceId,PIN_BASE,2147483647,'cell resource ID');const c=resources.get(cell.resourceId);
    if(!c||covered.has(c.id)||c.sourceKind!==kind||c.plane!==cell.plane||c.cellX!==cell.cellX||c.cellY!==cell.cellY||c.vertexCount!==cell.vertexCount||c.indexCount!==cell.indexCount)throw new Error('Whole zone resource coverage disagreement');covered.add(c.id);
  }
  if(covered.size!==chunks.length)throw new Error('Uncovered whole zone resource');
  const textureIds=new Set();const textures=m.textures.map(t=>{
    if(!t||typeof t!=='object'||typeof t.transparent!=='boolean')throw new Error('Whole zone texture declaration');
    const id=integer(t.id,0,2147483647,'texture ID'),width=integer(t.width,1,16384,'texture width'),height=integer(t.height,1,16384,'texture height');
    if(width*height>262144||textureIds.has(id)||t.bytes!==width*height*4)throw new Error('Whole zone texture shape');textureIds.add(id);gpuBytes+=t.bytes;fileBytes+=t.bytes;
    return Object.freeze({...t,id,width,height,path:path(t.path),sha256:hash(t.sha256,'texture')});
  });
  if(!Array.isArray(m.materialMetadata)||m.materialMetadata.length!==textures.length*5||m.materialMetadata.some(x=>!Number.isSafeInteger(x)||x<-2147483648||x>2147483647))throw new Error('Whole zone material metadata');
  for(let n=0;n<textures.length;n++){const t=textures[n],at=n*5;if(m.materialMetadata[at]!==t.id||m.materialMetadata[at+1]!==t.width||m.materialMetadata[at+2]!==t.height||m.materialMetadata[at+3]<0||m.materialMetadata[at+3]>0xffffff||m.materialMetadata[at+4]!==Number(t.transparent)||n&&textures[n-1].id>=t.id)throw new Error('Whole zone texture/material disagreement');}
  integer(m.sourceManifestBytes,1,MAX_MANIFEST,'source manifest size');
  // Reserve the existing local arena, texture updates and metadata separately.
  if(gpuBytes>WHOLE_ZONE_GPU_BYTES-256*1024*1024)throw new Error('Whole zone immutable resource budget');
  return Object.freeze({...m,scope:Object.freeze({sectors:Object.freeze({...m.scope.sectors}),placements:Object.freeze({...m.scope.placements})}),externalAssets:externalAssets(m.externalAssets),settings:Object.freeze(m.settings.slice()),materialMetadata:Object.freeze(m.materialMetadata.slice()),worldCells:Object.freeze(worldCells),objectCells:Object.freeze(objectCells),chunks:Object.freeze(chunks),textures:Object.freeze(textures),gpuBytes,fileBytes});
}
export function validateWholeZoneSource(m,source){
  if(!source||!Array.isArray(source.terrainSectors)||source.terrainSectors.length>2048)throw new Error('Whole zone raw source manifest');
  const selected=new Map(),expectedObjects=new Set(),counts=[0,0,0];
  for(const c of source.terrainSectors)if(c.worldSpace==='global'&&[0,1,2].includes(c.level)){
    const key=c.level+':'+c.sectorX+':'+c.sectorY;if(selected.has(key))throw new Error('Duplicate whole zone source sector');selected.set(key,c.sha256);counts[c.level]++;
    for(let x=0;x<2;x++)for(let y=0;y<2;y++)expectedObjects.add(c.level+':'+(c.sectorX*2+x)+':'+(c.sectorY*2+y));
  }
  if(counts.some((n,l)=>n!==WHOLE_ZONE_SOURCE.sectors[l])||selected.size!==m.worldCells.length||expectedObjects.size!==m.objectCells.length)throw new Error('Whole zone selected source coverage');
  for(const c of m.worldCells)if(selected.get(c.plane+':'+c.cellX+':'+c.cellY)!==c.sourceSha256)throw new Error('Whole zone source cell omitted/substituted');
  for(const c of m.objectCells)if(!expectedObjects.has(c.plane+':'+c.cellX+':'+c.cellY))throw new Error('Whole zone object source cell omitted/substituted');
  return true;
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
export async function loadWholeZone({baseURL,manifestBytes,manifestSha256,clientJarSha256,externalAssets:expectedAssets,fetcher=fetch,uploadChunk,uploadTexture,finishGpu,stats,signal,mapIdentity,settings,materials,progress=()=>{},assertAlive=()=>{}}){
  assertAlive();integer(manifestBytes,1,MAX_MANIFEST,'manifest bytes');hash(manifestSha256,'catalog');const base=new URL(baseURL,import.meta.url);
  const manifestRaw=await fetchWholeZoneBytes(new URL('manifest.json',base),manifestBytes,{fetcher,signal,maximum:MAX_MANIFEST});if(await digest(manifestRaw)!==manifestSha256)throw new Error('Whole zone catalog digest mismatch');
  const m=validateWholeZoneManifest(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(manifestRaw)));
  const stageAssets=externalAssets(expectedAssets);if(stageAssets.some((a,n)=>a.path!==m.externalAssets[n].path||a.bytes!==m.externalAssets[n].bytes||a.sha256!==m.externalAssets[n].sha256))throw new Error('Whole zone baked/staged external assets disagree');
  if(m.clientJarSha256!==clientJarSha256)throw new Error('Whole zone baked/client artifact mismatch');
  validateWholeZoneLive(m,mapIdentity,settings,materials);
  const materialWords=new Int32Array(m.materialMetadata);if(await digest(materialWords.buffer)!==m.textureCatalogSha256)throw new Error('Whole zone material catalog digest mismatch');
  const sourceRaw=await fetchWholeZoneBytes(new URL('source-manifest.json',base),m.sourceManifestBytes,{fetcher,signal,maximum:MAX_MANIFEST});if(await digest(sourceRaw)!==WHOLE_ZONE_SOURCE.manifestSha256)throw new Error('Whole zone original source manifest digest mismatch');validateWholeZoneSource(m,JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(sourceRaw)));
  assertAlive();Object.assign(stats,{phase:'loading',ready:false,manifestSha256,expectedPinnedChunks:m.chunks.length,expectedPinnedBytes:m.gpuBytes,sourceFingerprint:m.packageFingerprintSha256,sourceManifestSha256:m.manifestSha256,sourceSectors:1330,sourceObjectPlacements:24580,sourceCatalogVerified:true,rawSourceCPUReady:false,catalogJSONBytes:manifestBytes,metadataRetainedScope:'Parsed immutable catalog descriptors; JSON size is not exact JS heap size',downloadedMetadataBytes:manifestBytes+m.sourceManifestBytes,downloadedBytes:manifestBytes+m.sourceManifestBytes,bakedGPUDataDownloadedBytes:0,preparedResources:0,pinnedChunks:0,pinnedBytes:0,textureBytes:0,startupGpuCompleted:false});
  stats.downloadedSourceAssetBytes=0;stats.externalAssetsVerified=false;
  for(const asset of m.externalAssets){const raw=await fetchWholeZoneBytes(new URL('../'+asset.path,base),asset.bytes,{fetcher,signal});if(await digest(raw)!==asset.sha256)throw new Error('Whole zone staged external asset digest mismatch: '+asset.path);stats.downloadedSourceAssetBytes+=raw.byteLength;stats.downloadedBytes+=raw.byteLength;}
  stats.externalAssetsVerified=true;
  for(let n=0;n<m.textures.length;n++){const t=m.textures[n];const raw=await fetchWholeZoneBytes(new URL(t.path,base),t.bytes,{fetcher,signal});if(await digest(raw)!==t.sha256)throw new Error('Whole zone texture digest mismatch');const pixels=new Int32Array(raw),material=m.materialMetadata.slice(n*5,n*5+5);validateWholeZoneTexture(material,t.width,t.height,pixels,t.transparent);await uploadTexture(t,pixels,material);stats.downloadedBytes+=raw.byteLength;stats.bakedGPUDataDownloadedBytes+=raw.byteLength;stats.textureBytes+=raw.byteLength;stats.preparedResources++;}
  for(const c of m.chunks){const raw=await fetchWholeZoneBytes(new URL(c.path,base),c.bytes,{fetcher,signal});if(await digest(raw)!==c.sha256)throw new Error('Whole zone chunk digest mismatch');const planes=decodeWholeZoneChunk(raw,c);await uploadChunk(c,planes);stats.downloadedBytes+=raw.byteLength;stats.bakedGPUDataDownloadedBytes+=raw.byteLength;stats.pinnedChunks++;stats.pinnedBytes+=planes.vertices.byteLength+planes.indices.byteLength;stats.preparedResources++;if(stats.pinnedChunks%64===0)progress(stats.pinnedChunks,m.chunks.length);}
  if(stats.pinnedChunks!==m.chunks.length||stats.pinnedBytes+stats.textureBytes!==m.gpuBytes)throw new Error('Incomplete whole zone resource residency');
  await finishGpu();assertAlive();Object.assign(stats,{phase:'ready',ready:true,startupGpuCompleted:true});return m;
}
/** Per-frame selection keeps the bounded live renderer and the immutable arena distinct. */
export function createWholeZoneSelection(catalog,stats){
  const world=new Map(),objects=new Map(),objectCells=new Map();
  for(const c of catalog.chunks)(c.sourceKind==='world'?world:objects).set(c.plane+':'+c.cellX+':'+c.cellY,c);
  for(const c of catalog.objectCells||[])objectCells.set(c.plane+':'+c.cellX+':'+c.cellY,c);
  let current=null,pending=false,lastEpoch=null;
  function frame(metadata,overrides){
    metadata=ints(metadata);overrides=ints(overrides);
    if(metadata.length!==18||overrides.length%6||overrides.length>3*25*4*6)throw new Error('Whole zone frame shape');
    const v=Array.from(metadata);if(v[0]!==1||![0,1].includes(v[1])||v[2]<-32||v[2]>32||v[7]!==2||![0,1].includes(v[17])||v.slice(11,15).some((x,n)=>x!==catalog.settings[n]))throw new Error('Whole zone frame contract/settings');
    if(v[1]&&(v[2]!==0||v[17]!==0))throw new Error('Whole zone applies to overworld only');
    for(const n of [8,9,10])if(v[n]<0)throw new Error('Whole zone visibility mask');
    const epoch=[v[16]>>>0,v[15]>>>0];if(lastEpoch&&(epoch[0]<lastEpoch[0]||epoch[0]===lastEpoch[0]&&epoch[1]<lastEpoch[1]))throw new Error('Whole zone source epoch moved backwards');
    const rows=new Map();for(let at=0;at<overrides.length;at+=6){const plane=overrides[at],x=overrides[at+1],y=overrides[at+2],reason=overrides[at+5],key=plane+':'+x+':'+y;
      if(plane<0||plane>2||reason<0||reason>3||rows.has(key))throw new Error('Whole zone object authority row');
      rows.set(key,{key:(overrides[at+4]>>>0).toString(16).padStart(8,'0')+(overrides[at+3]>>>0).toString(16).padStart(8,'0'),reason});
    }
    current={v,rows};pending=true;lastEpoch=epoch;stats.active=v[1]===1;stats.sourceEpochLow=epoch[1];stats.sourceEpochHigh=epoch[0];stats.currentLevel=v[2];stats.nonGlobalWorldSpace=v[17]===1;
  }
  function merge(input,level){
    if(!pending||!current||current.v[2]!==level)throw new Error('Whole zone presentation missing fresh frame declaration');pending=false;
    if(input.length%8||input.length>256*8)throw new Error('Whole zone local draw bounds');
    const {v,rows}=current;stats.currentLocalDraws=input.length/8;
    for(let at=0;at<input.length;at+=8)if(input[at]>=PIN_BASE)throw new Error('Pinned chunk appeared in local Java draw list');
    if(!v[1]){if(level===0&&v[17]===0)throw new Error('Whole zone overworld source inactive');Object.assign(stats,{currentPinnedDraws:0,currentMergedDraws:input.length/8,verifiedObjectCells:0,mutatedObjectCells:0,animatedObjectCells:0,authoredBaselineObjectCells:0,selectedObjectCells:0});return input;}
    const out=[];let unchanged=0,mutated=0,animated=0,baseline=0,knownCells=0;
    const add=c=>{if(!c||c.sourceKind==='objects'&&c.plane>0)return;const dx=c.originX-v[3],dz=c.originZ-v[4];integer(dx,-2147483648,2147483647,'rebased X');integer(dz,-2147483648,2147483647,'rebased Z');out.push(c.id,dx,dz,v[3],v[4],v[8+c.plane]&c.visibilityMask,c.plane,c.role);};
    for(let plane=0;plane<3;plane++)for(let x=v[5]-2;x<=v[5]+2;x++)for(let y=v[6]-2;y<=v[6]+2;y++)add(world.get(plane+':'+x+':'+y));
    for(let plane=0;plane<3;plane++)for(let x=(v[5]-2)*2;x<(v[5]+3)*2;x++)for(let y=(v[6]-2)*2;y<(v[6]+3)*2;y++){
      const key=plane+':'+x+':'+y,cell=objectCells.get(key);if(!cell)continue;knownCells++;const row=rows.get(key);if(!row)throw new Error('Missing whole zone object authority: '+key);
      const matches=row.key===cell.canonicalContentKey;
      if(row.reason===0){if(!matches)throw new Error('Whole zone unchanged key mismatch');unchanged++;add(objects.get(key));}
      else if(row.reason===3){if(!matches)throw new Error('Whole zone authored baseline key mismatch');baseline++;add(objects.get(key));}
      else if(row.reason===1)mutated++;else animated++;
    }
    const pinned=out.length/8;if(pinned+input.length/8>1024)throw new Error('Whole zone merged draw capacity');
    const merged=new Int32Array(out.length+input.length);merged.set(out);merged.set(input,out.length);
    Object.assign(stats,{currentPinnedDraws:pinned,currentMergedDraws:merged.length/8,verifiedObjectCells:unchanged,mutatedObjectCells:mutated,animatedObjectCells:animated,authoredBaselineObjectCells:baseline,selectedObjectCells:knownCells});return merged;
  }
  return {frame,merge};
}
