/** Browser-owned GPU representations. Java/local geometry keeps its literal112B
 * contract; compact48 is an independently source-bound immutable derivative. */
export const LEGACY_GEOMETRY='legacy112-v1',COMPACT_GEOMETRY='compact48-v1';
export const COMPACT_MAGIC=0x575a4332,COMPACT_HEADER_BYTES=24;
const definitions=[
  ['aPosition',3,0,0,'FLOAT',false],['aMaterialColor',4,3,32,'UNSIGNED_BYTE',true],
  ['aTexCoord',2,7,12,'FLOAT',false],['aRawMaterialColor',3,15,36,'UNSIGNED_BYTE',true],
  ['aBaseLegacyLight',1,14,39,'UNSIGNED_BYTE',false],['aNormal',3,18,20,'FLOAT',false],
  ['aModelKind',1,21,43,'UNSIGNED_BYTE',false],['aMaterialFamily',1,22,44,'UNSIGNED_BYTE',false],
  ['aTerrainVariationMask',1,23,45,'UNSIGNED_BYTE',false],['aTerrainBlendColor',3,24,40,'UNSIGNED_BYTE',true],
  ['aTerrainBlendStrength',1,27,46,'UNSIGNED_BYTE',false]
];
export const GEOMETRY_ATTRIBUTES=Object.freeze(definitions.map(r=>Object.freeze(r)));
export const COMPACT_CONTRACT=Object.freeze({id:COMPACT_GEOMETRY,version:1,strideBytes:48,headerBytes:COMPACT_HEADER_BYTES,
  sourceLayout:LEGACY_GEOMETRY,sourceStrideBytes:112,attributes:GEOMETRY_ATTRIBUTES});
export function geometryLayout(id=LEGACY_GEOMETRY){
  if(id!==LEGACY_GEOMETRY&&id!==COMPACT_GEOMETRY)throw new Error('Unsupported browser geometry layout');
  return {id,strideBytes:id===COMPACT_GEOMETRY?48:112,positionOffset:0,uvOffset:id===COMPACT_GEOMETRY?12:28};
}
export function geometryBytes(c){return c.vertexCount*geometryLayout(c.geometryLayout).strideBytes+c.indexCount*4;}
export function bindGeometryAttributes(gl,program,id=LEGACY_GEOMETRY,attributes=GEOMETRY_ATTRIBUTES){
  const layout=geometryLayout(id);
  for(const [name,size,legacyOffset]of attributes){
    const at=gl.getAttribLocation(program,name);if(at<0)continue;
    const def=GEOMETRY_ATTRIBUTES.find(r=>r[0]===name);
    if(!def||def[1]!==size||def[2]!==legacyOffset)throw new Error('Browser geometry attribute contract changed: '+name);
    gl.enableVertexAttribArray(at);
    gl.vertexAttribPointer(at,size,id===COMPACT_GEOMETRY?gl[def[4]]:gl.FLOAT,id===COMPACT_GEOMETRY&&def[5],layout.strideBytes,id===COMPACT_GEOMETRY?def[3]:legacyOffset*4);
  }
}
export function validateCompactShaderAttributes(gl,program){
  const count=gl.getProgramParameter(program,gl.ACTIVE_ATTRIBUTES);
  if(!Number.isSafeInteger(count)||count<1||count>GEOMETRY_ATTRIBUTES.length)throw new Error('Compact shader active attribute contract');
  for(let n=0;n<count;n++){
    const a=gl.getActiveAttrib(program,n),def=a&&GEOMETRY_ATTRIBUTES.find(r=>r[0]===a.name);
    const type=def&&(def[1]===1?gl.FLOAT:gl['FLOAT_VEC'+def[1]]);
    if(!def||a.size!==1||a.type!==type)throw new Error('Compact shader uses an unsupported attribute');
  }
}
export function bindShadowGeometryAttributes(gl,id=LEGACY_GEOMETRY){
  const l=geometryLayout(id);gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,l.strideBytes,l.positionOffset);
  gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,2,gl.FLOAT,false,l.strideBytes,l.uvOffset);
}
/** Views only: no copied vertex plane, and no runtime compact-to-legacy expansion. */
export function geometryFloatView(vertices,id=LEGACY_GEOMETRY){
  const l=geometryLayout(id);if(vertices.byteOffset%4||vertices.byteLength%l.strideBytes)throw new Error('Browser geometry plane alignment');
  return new Float32Array(vertices.buffer,vertices.byteOffset,vertices.byteLength/4);
}
export function geometryBounds(vertices,id=LEGACY_GEOMETRY){
  const f=geometryFloatView(vertices,id),stride=geometryLayout(id).strideBytes/4;
  if(!f.length)return null;const b=new Float64Array([Infinity,Infinity,Infinity,-Infinity,-Infinity,-Infinity]);
  for(let at=0;at<f.length;at+=stride)for(let a=0;a<3;a++){const n=f[at+a];if(!Number.isFinite(n))throw new Error('Nonfinite browser geometry');b[a]=Math.min(b[a],n);b[a+3]=Math.max(b[a+3],n);}
  return b;
}
// Offline converter/test API. The runtime uploads compact bytes directly.
const wordBuffer=new ArrayBuffer(4),wordFloat=new Float32Array(wordBuffer),wordInt=new Uint32Array(wordBuffer);
const floatWord=n=>{wordFloat[0]=n;return wordInt[0];};
const UNORM8=new Map(Array.from({length:256},(_,n)=>[floatWord(n/255),n]));
const FLOAT_COMPONENTS=Object.freeze([0,1,2,7,8,18,19,20]);
const UINT8=new Map(Array.from({length:256},(_,n)=>[floatWord(n),n]));
export function packCompactVertices(input){
  if(!(input instanceof Float32Array)||input.length%28)throw new Error('Compact conversion requires legacy float32 vertices');
  const count=input.length/28;if(count>524288)throw new Error('Compact vertex limit');
  const source=new Uint8Array(input.buffer,input.byteOffset,input.byteLength),words=new Uint32Array(input.buffer,input.byteOffset,input.length);
  const out=new Uint8Array(count*48);
  for(let n=0;n<count;n++){
    const s=n*112,t=n*48,w=n*28;
    for(const c of FLOAT_COMPONENTS)if(!Number.isFinite(input[w+c]))throw new Error('Compact nonfinite float attribute');
    out.set(source.subarray(s,s+12),t);out.set(source.subarray(s+28,s+36),t+12);out.set(source.subarray(s+72,s+84),t+20);
    for(const [,size,offset,target,type,normalized]of GEOMETRY_ATTRIBUTES){
      if(type==='FLOAT')continue;const map=normalized?UNORM8:UINT8;
      for(let c=0;c<size;c++){const byte=map.get(words[w+offset+c]);if(byte===undefined)throw new Error('Compact attribute is not losslessly representable at component '+(offset+c));out[t+target+c]=byte;}
    }
  }
  return out;
}
/** Reconstruct only declared shader attributes, then compare their float32 bits.
 * Omitted legacy9..13 are deliberately not reconstructed or used by the browser. */
export function verifyCompactVertices(legacy,compact){
  if(!(legacy instanceof Float32Array)||!(compact instanceof Uint8Array)||legacy.length%28||compact.length!==legacy.length/28*48)throw new Error('Compact proof shape');
  const old=new Uint32Array(legacy.buffer,legacy.byteOffset,legacy.length),floats=geometryFloatView(compact,COMPACT_GEOMETRY),raw=new Uint32Array(floats.buffer,floats.byteOffset,floats.length);
  let compared=0;
  for(let n=0;n<legacy.length/28;n++){
    if(compact[n*48+47]!==0)throw new Error('Compact reserved byte');
    for(const [,size,offset,target,type,normalized]of GEOMETRY_ATTRIBUTES)for(let c=0;c<size;c++){
      const word=type==='FLOAT'?raw[n*12+target/4+c]:floatWord(compact[n*48+target+c]/(normalized?255:1));
      if(word!==old[n*28+offset+c])throw new Error('Compact shader attribute differs at vertex '+n+' component '+(offset+c));compared++;
    }
  }
  return compared;
}
