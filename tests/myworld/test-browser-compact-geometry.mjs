/** Actual compact APIs against independent literal GPU attribute interpretation.
 * Synthetic GL records allocations/bindings only; hardware pixels/timing separate. */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {COMPACT_GEOMETRY,LEGACY_GEOMETRY,COMPACT_CONTRACT,packCompactVertices,verifyCompactVertices,bindGeometryAttributes,bindShadowGeometryAttributes,geometryBytes,validateCompactShaderAttributes} from '../../tools/browser-gameplay/web/browser-geometry-layout.js';
import {decodeWholeZoneChunk,validateWholeZoneManifest} from '../../tools/browser-gameplay/web/browser-whole-zone.js';
import {selectCompactCatalog,validateCompactConfig} from '../../tools/browser-gameplay/web/browser-compact-catalog.js';
import {createBoundedChunkUpload} from '../../tools/browser-gameplay/web/browser-bounded-upload.js';
import {createBoundedResidency} from '../../tools/browser-gameplay/web/browser-bounded-residency.js';
import {wholeZonePolicy} from '../../tools/browser-gameplay/web/browser-whole-zone-policy.js';
let checks=0;const check=(v,m)=>{assert(v,m);checks++;},equal=(a,b,m)=>{assert.deepEqual(a,b,m);checks++;},refuse=(fn,re)=>{assert.throws(fn,re);checks++;};
const count=257,legacy=new Float32Array(count*28);
for(let n=0;n<count;n++){
 const v=n*28;legacy.set([n===0?-0:n*193.125,-n*0.125,n*29.875],v);legacy[v+7]=Math.fround((n-128)/317);legacy[v+8]=Math.fround((n+71)/701);
 for(const c of [3,4,5,6,15,16,17,24,25,26])legacy[v+c]=Math.fround(((n+c*47)%256)/255);
 legacy[v+14]=n%256;legacy[v+18]=Math.fround(Math.sin(n));legacy[v+19]=Math.fround(Math.cos(n));legacy[v+20]=-0;
 for(const c of [21,22,23,27])legacy[v+c]=(n+c)%256;
 for(const c of [9,10,11,12,13])legacy[v+c]=NaN; // These are not browser shader inputs.
}
const compact=packCompactVertices(legacy);equal(compact.byteLength,count*48);equal(verifyCompactVertices(legacy,compact),count*23);
// Oracle is a literal layout table, independently interpreting native attribute types.
const expected=[['aPosition',3,0,0,'FLOAT',false],['aMaterialColor',4,3,32,'UNSIGNED_BYTE',true],['aTexCoord',2,7,12,'FLOAT',false],['aRawMaterialColor',3,15,36,'UNSIGNED_BYTE',true],['aBaseLegacyLight',1,14,39,'UNSIGNED_BYTE',false],['aNormal',3,18,20,'FLOAT',false],['aModelKind',1,21,43,'UNSIGNED_BYTE',false],['aMaterialFamily',1,22,44,'UNSIGNED_BYTE',false],['aTerrainVariationMask',1,23,45,'UNSIGNED_BYTE',false],['aTerrainBlendColor',3,24,40,'UNSIGNED_BYTE',true],['aTerrainBlendStrength',1,27,46,'UNSIGNED_BYTE',false]];
const bytes=new DataView(compact.buffer),wordBuf=new ArrayBuffer(4),floats=new Float32Array(wordBuf),words=new Uint32Array(wordBuf),word=x=>{floats[0]=x;return words[0];},old=new Uint32Array(legacy.buffer);
for(let n=0;n<count;n++)for(const[name,size,source,target,type,normalized]of expected)for(let c=0;c<size;c++){
 const value=type==='FLOAT'?bytes.getFloat32(n*48+target+c*4,true):bytes.getUint8(n*48+target+c)/(normalized?255:1);
 equal(word(value),old[n*28+source+c],name+' input matches source float32');
}
for(const c of [0,1,2,7,8,18,19,20]){const bad=legacy.slice();bad[c]=Infinity;refuse(()=>packCompactVertices(bad),/nonfinite/);}
for(const c of [3,4,5,6,15,16,17,24,25,26,14,21,22,23,27]){const bad=legacy.slice();bad[c]=c===14?256:Math.fround(0.123456);refuse(()=>packCompactVertices(bad),/losslessly/);bad[c]=-0;refuse(()=>packCompactVertices(bad),/losslessly/);}
const corrupt=compact.slice();corrupt[47]=1;refuse(()=>verifyCompactVertices(legacy,corrupt),/reserved/);corrupt[47]=0;corrupt[32]^=1;refuse(()=>verifyCompactVertices(legacy,corrupt),/differs/);
function packed(format){
 const c={id:1000000,vertexCount:count,indexCount:3,batchIntCount:5,geometryLayout:format,bytes:(format===COMPACT_GEOMETRY?24+count*48:16+count*112)+32};
 const b=new ArrayBuffer(c.bytes),h=new DataView(b),offset=format===COMPACT_GEOMETRY?24:16;
 (format===COMPACT_GEOMETRY?[0x575a4332,1,48,count,3,5]:[0x575a4331,count,3,5]).forEach((v,n)=>h.setUint32(n*4,v,true));
 new Uint8Array(b,offset,count*(format===COMPACT_GEOMETRY?48:112)).set(format===COMPACT_GEOMETRY?compact:new Uint8Array(legacy.buffer));
 new Int32Array(b,offset+count*(format===COMPACT_GEOMETRY?48:112),3).set([0,1,256]);new Int32Array(b,c.bytes-20,5).set([7,0,3,1,1]);return {c,b};
}
for(const mode of [LEGACY_GEOMETRY,COMPACT_GEOMETRY]){
 const {c,b}=packed(mode),p=decodeWholeZoneChunk(b,c);equal(p.geometryLayout,mode);equal(p.indices,Int32Array.of(0,1,256));equal(p.batches,Int32Array.of(7,0,3,1,1));equal(p.vertices.byteLength,c.vertexCount*(mode===COMPACT_GEOMETRY?48:112));
 for(const offset of [0,mode===COMPACT_GEOMETRY?4:4,mode===COMPACT_GEOMETRY?8:8]){const bad=b.slice(0);new DataView(bad).setUint32(offset,123,true);refuse(()=>decodeWholeZoneChunk(bad,c),/header/);}
 refuse(()=>decodeWholeZoneChunk(b.slice(0,b.byteLength-1),c),/length/);refuse(()=>decodeWholeZoneChunk(b,{...c,geometryLayout:'unknown'}),/layout/);
}
function glDouble(){
 let vao={external:true},array={external:true},serial=0;const objects=new Set(),storage=new Map(),pointers=[],uploads=[];
 const gl={VERTEX_ARRAY_BINDING:1,ARRAY_BUFFER_BINDING:2,ARRAY_BUFFER:3,ELEMENT_ARRAY_BUFFER:4,STATIC_DRAW:5,FLOAT:6,UNSIGNED_BYTE:7,ACTIVE_ATTRIBUTES:8,FLOAT_VEC2:9,FLOAT_VEC3:10,FLOAT_VEC4:11};
 gl.getParameter=p=>p===1?vao:array;gl.bindVertexArray=v=>{vao=v;};gl.bindBuffer=(target,v)=>{if(target===3)array=v;else vao.element=v;};
 for(const name of ['VertexArray','Buffer']){gl['create'+name]=()=>{const v={id:++serial};objects.add(v);return v;};gl['delete'+name]=v=>{check(objects.delete(v),'delete owned object exactly once');storage.delete(v);};}
 gl.bufferData=(target,n)=>{storage.set(target===3?array:vao.element,new Uint8Array(n));};gl.bufferSubData=(target,at,data)=>{storage.get(target===3?array:vao.element).set(data,at);uploads.push(data.byteLength);};
 gl.getAttribLocation=(_,name)=>expected.findIndex(r=>r[0]===name);gl.enableVertexAttribArray=()=>{};gl.vertexAttribPointer=(...v)=>pointers.push(v);
 gl.getProgramParameter=()=>expected.length;gl.getActiveAttrib=(_,n)=>({name:expected[n][0],size:1,type:expected[n][1]===1?gl.FLOAT:gl['FLOAT_VEC'+expected[n][1]]});
 return {gl,objects,storage,pointers,uploads,bindings:()=>[vao,array]};
}
for(const mode of [LEGACY_GEOMETRY,COMPACT_GEOMETRY]){
 const f=glDouble();bindGeometryAttributes(f.gl,{},mode);equal(f.pointers.length,11);
 for(let i=0;i<11;i++){const[,size,source,target,type,norm]=expected[i];equal(f.pointers[i],[i,size,mode===LEGACY_GEOMETRY?f.gl.FLOAT:f.gl[type],mode===COMPACT_GEOMETRY&&norm,mode===LEGACY_GEOMETRY?112:48,mode===LEGACY_GEOMETRY?source*4:target]);}
 f.pointers.length=0;bindShadowGeometryAttributes(f.gl,mode);equal(f.pointers,[[0,3,f.gl.FLOAT,false,mode===LEGACY_GEOMETRY?112:48,0],[1,2,f.gl.FLOAT,false,mode===LEGACY_GEOMETRY?112:48,mode===LEGACY_GEOMETRY?28:12]]);
 validateCompactShaderAttributes(f.gl,{});f.gl.getActiveAttrib=()=>({name:'aUnexpected',size:1,type:f.gl.FLOAT});refuse(()=>validateCompactShaderAttributes(f.gl,{}),/unsupported/);
 const {c,b}=packed(mode);let allocated=0,published=null,steps=0;const original=f.bindings(),u=createBoundedChunkUpload(f.gl,{chunk:c,buffer:b,program:{},attributes:expected,check(){},reserve:n=>check(n===geometryBytes(c),'reserve actual bytes'),allocated:n=>{allocated+=n;},released:n=>{allocated-=n;},publish:value=>{published=value;}});
 while(!u.step(224)){check(++steps<1000,'bounded completion');check(!published,'no early publication');equal(f.bindings(),original);}
 check(f.uploads.every(n=>n<=224));equal(allocated,count*(mode===COMPACT_GEOMETRY?48:112)+12);equal(published.geometryLayout,mode);equal(published.vertexBytes,count*(mode===COMPACT_GEOMETRY?48:112));equal(f.storage.get(published.vbo),new Uint8Array(decodeWholeZoneChunk(b,c).vertices.buffer,decodeWholeZoneChunk(b,c).vertices.byteOffset,published.vertexBytes));
 const second=glDouble(),v=createBoundedChunkUpload(second.gl,{chunk:c,buffer:b,program:{},attributes:expected,check(){},reserve(){},allocated:n=>{allocated+=n;},released:n=>{allocated-=n;},publish(){throw Error('must not publish');}});for(let n=0;n<1000&&second.objects.size===0;n++)v.step(224);v.dispose();equal(second.objects.size,0);refuse(()=>v.step(224),/Stale/);
}
const H='a'.repeat(64),J='b'.repeat(64),S='c'.repeat(64),P='d'.repeat(64),Q='e'.repeat(64);
const canonical={clientJarSha256:J,manifestSha256:S,packageFingerprintSha256:P,settings:[1,2,3,4],chunks:[{id:1000000,sha256:Q,bytes:1000,vertexCount:3,indexCount:3,batchIntCount:5,path:'chunks/c1000000.bin'}],textures:[],gpuBytes:348};
const side={schemaVersion:1,type:'spoiled-milk-compact-geometry',complete:true,canonicalCatalogSha256:H,clientJarSha256:J,sourceManifestSha256:S,packageFingerprintSha256:P,settings:[1,2,3,4],layout:COMPACT_CONTRACT,vertexCount:3,geometryGpuBytes:156,resourceFileBytes:200,resources:[{id:1000000,geometryLayout:COMPACT_GEOMETRY,canonicalSha256:Q,canonicalBytes:1000,vertexCount:3,indexCount:3,batchIntCount:5,path:'compact-chunks/c1000000.bin',sha256:S,bytes:200}]};
const selected=selectCompactCatalog(canonical,side,H);equal(selected.gpuBytes,156);equal(canonical.chunks[0].bytes,1000);equal(selected.chunks[0].canonicalSha256,Q);
for(const field of ['schemaVersion','type','complete','canonicalCatalogSha256','clientJarSha256','sourceManifestSha256','packageFingerprintSha256','settings','layout','vertexCount','geometryGpuBytes','resourceFileBytes']){const bad=structuredClone(side);bad[field]=field==='complete'?false:field==='settings'?[]:field==='layout'?{}:field.includes('Bytes')||field==='vertexCount'||field==='schemaVersion'?999:'invalid';refuse(()=>selectCompactCatalog(canonical,bad,H),/Compact/);}
for(const field of ['id','geometryLayout','canonicalSha256','canonicalBytes','vertexCount','indexCount','batchIntCount','path','sha256','bytes']){const bad=structuredClone(side);bad.resources[0][field]=['id','canonicalBytes','vertexCount','indexCount','batchIntCount','bytes'].includes(field)?999:'invalid';refuse(()=>selectCompactCatalog(canonical,bad,H),/Compact/);}
for(const resources of [[],[side.resources[0],side.resources[0]]])refuse(()=>selectCompactCatalog(canonical,{...side,resources},H),/roster/);
const config={schemaVersion:1,geometryLayout:COMPACT_GEOMETRY,canonicalCatalogSha256:H,manifestSha256:S,manifestBytes:100};validateCompactConfig(config,H);refuse(()=>validateCompactConfig({...config,canonicalCatalogSha256:Q},H),/binding/);
for(const value of ['compact','bogus'])refuse(()=>wholeZonePolicy('webgl',new URLSearchParams('geometryformat='+value)),/requires/);
const query=new URLSearchParams('wholezone=on&benchmark=1&renderworker=on&overlay=coverage&geometryformat=compact');equal(wholeZonePolicy('webgl',query).geometryFormat,'compact');equal(wholeZonePolicy('webgl',new URLSearchParams()).geometryFormat,'legacy');
// Actual scheduler accounting: compact required set fits a budget legacy cannot.
for(const [mode,shouldFit]of [[LEGACY_GEOMETRY,false],[COMPACT_GEOMETRY,true]]){
 const c={...selected.chunks[0],geometryLayout:mode},stats={},resident=new Map(),timers=[];const scheduler=createBoundedResidency({catalog:{chunks:[c]},stats,capacityBytes:200,maxChunks:2,optionalReserveBytes:0,resident,totalBytes:()=>40,schedule:fn=>{timers.push(fn);return timers.length;},cancelSchedule(){},evict(){throw Error('no eviction');},prepare:async()=>{},createUpload(){throw Error('not executing upload');}});
 if(shouldFit){scheduler.select([c.id]);equal(stats.requiredBytes,156);}else refuse(()=>scheduler.select([c.id]),/exceeds/);scheduler.dispose();
}
if(process.argv.length===4){
 const raw=fs.readFileSync(process.argv[2]),real=validateWholeZoneManifest(JSON.parse(raw)),derivative=JSON.parse(fs.readFileSync(process.argv[3])),hash=crypto.createHash('sha256').update(raw).digest('hex'),result=selectCompactCatalog(real,derivative,hash);
 equal(result.chunks.length,real.chunks.length);check(result.gpuBytes<real.gpuBytes);equal(result.worldCells,real.worldCells);equal(result.objectCells,real.objectCells);equal(result.materialMetadata,real.materialMetadata);
}
console.log(JSON.stringify({passed:true,checks,scope:'Actual compact pack/decode/manifest/binding/uploader/scheduler APIs; independent literal attribute-byte interpretation, negative cases, mixed legacy/compact GL double. Hardware shader pixels, driver residency and timing are separate.'}));
