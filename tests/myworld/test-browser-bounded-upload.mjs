import assert from 'node:assert/strict';
import {createBoundedChunkUpload} from '../../tools/browser-gameplay/web/browser-bounded-upload.js';
import {wholeZonePolicy,wholeZoneCapacity} from '../../tools/browser-gameplay/web/browser-whole-zone-policy.js';
function fixture(){
  const objects=new Set(),buffers=new Map(),calls=[];let id=0,vao={external:true},array={external:true};
  const gl={VERTEX_ARRAY_BINDING:1,ARRAY_BUFFER_BINDING:2,ARRAY_BUFFER:3,ELEMENT_ARRAY_BUFFER:4,STATIC_DRAW:5,FLOAT:6};
  gl.getParameter=p=>p===1?vao:array;gl.bindVertexArray=v=>{vao=v;};gl.bindBuffer=(target,value)=>{if(target===3)array=value;else vao.element=value;};
  for(const name of ['VertexArray','Buffer']){gl['create'+name]=()=>{const v={id:++id};objects.add(v);return v;};gl['delete'+name]=v=>{assert(objects.delete(v));buffers.delete(v);};}
  gl.bufferData=(target,n)=>{assert.equal(typeof n,'number');buffers.set(target===3?array:vao.element,new Uint8Array(n));};
  gl.bufferSubData=(target,at,bytes)=>{calls.push(bytes.length);buffers.get(target===3?array:vao.element).set(bytes,at);};
  gl.getAttribLocation=()=>0;gl.enableVertexAttribArray=gl.vertexAttribPointer=()=>{};
  const c={vertexCount:12,indexCount:12,batchIntCount:5,bytes:16+12*112+12*4+5*4},buffer=new ArrayBuffer(c.bytes),header=new Uint32Array(buffer,0,4);header.set([0x575a4331,12,12,5]);
  const vertices=new Float32Array(buffer,16,12*28);for(let n=0;n<12;n++)vertices.set([n,-n,n*2],n*28);
  new Int32Array(buffer,16+12*112,12).set(Array.from({length:12},(_,i)=>i));new Int32Array(buffer,16+12*112+48,5).set([1,0,12,1,0]);
  let allocated=0,published=null,reserveLimit=Infinity;
  const make=()=>createBoundedChunkUpload(gl,{chunk:c,buffer,program:{},attributes:[['aPosition',3,0]],check(){},reserve:n=>{if(n>reserveLimit)throw new Error('resource cap');},allocated:n=>{allocated+=n;},released:n=>{allocated-=n;},publish:v=>{assert.equal(published,null);published=v;}});
  return {gl,make,objects,c,buffer,calls,set reserveLimit(n){reserveLimit=n;},get allocated(){return allocated;},get published(){return published;},bindings:()=>[vao,array]};
}
const f=fixture(),original=f.bindings(),upload=f.make();let steps=0;
while(!upload.step(112)){steps++;assert(steps<100);assert.equal(f.published,null);assert.deepEqual(f.bindings(),original);}
assert(steps>20,'preparation/upload collapsed into one call');assert(f.calls.every(n=>n<=112));assert.deepEqual([...f.published.bounds],[0,-11,0,11,-0,22]);assert.equal(f.allocated,12*116);assert.deepEqual(f.bindings(),original);
upload.dispose();assert.equal(f.objects.size,3,'published buffers belong to renderer');
const cancel=fixture(),partial=cancel.make();for(let n=0;n<30&&!cancel.allocated;n++)partial.step(112);assert(cancel.allocated>0);partial.dispose();assert.equal(cancel.allocated,0);assert.equal(cancel.objects.size,0);assert.equal(cancel.published,null);assert.throws(()=>partial.step(112),/Stale/);
const bad=fixture();new Int32Array(bad.buffer,16+12*112,12)[5]=99;const invalid=bad.make();assert.throws(()=>{while(!invalid.step(112)){};},/index/);invalid.dispose();assert.equal(bad.objects.size,0);
const cap=fixture();cap.reserveLimit=10;const refused=cap.make();assert.throws(()=>{while(!refused.step(112)){};},/cap/);refused.dispose();assert.equal(cap.objects.size,0);
for(const n of [0,4,111,113,NaN])assert.throws(()=>fixture().make().step(n),/slice/);
const privateOptions=new URLSearchParams('wholezone=on&benchmark=1&renderworker=on&overlay=coverage&gpuresidency=bounded&staticscene=on');
assert.equal(wholeZonePolicy('webgl',privateOptions).residency,'bounded');assert(wholeZonePolicy('webgl',privateOptions).properties.includes('spoiledmilk.browserStaticSceneReuse=true'));
assert.equal(wholeZoneCapacity({wholeZone:true,benchmarkControl:true,gpuResidency:'bounded'}).gpuBytes,256*1024*1024);
assert.equal(wholeZoneCapacity({wholeZone:true,benchmarkControl:true}).gpuBytes,3*1024*1024*1024);
assert.throws(()=>wholeZonePolicy('webgl',new URLSearchParams('gpuresidency=bounded')),/requires/);
assert.throws(()=>wholeZonePolicy('webgl',new URLSearchParams('staticscene=on')),/requires/);
console.log('PASS bounded upload progressive validation/copy/transfer, publication, binding restore, cancellation, corrupt indices, refusal and private policy. GL double only.');
