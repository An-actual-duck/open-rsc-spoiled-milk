// In-memory WebGL API/resource/pixel-placement controls. No hardware or FPS claim.
import assert from 'node:assert/strict';
import {createBrowserSpriteAtlas} from '../../tools/browser-gameplay/web/browser-sprite-atlas.js';
function fixture(extent=4){
 const image=new Uint8Array(extent*extent*4),stats={},uploads=[];let active=9,binding='originalTexture',array='originalBuffer',alignment=8,quad,fail=false;
 const gl={NO_ERROR:0,TEXTURE0:10,TEXTURE_2D:11,ARRAY_BUFFER:12,ACTIVE_TEXTURE:13,TEXTURE_BINDING_2D:14,ARRAY_BUFFER_BINDING:15,UNPACK_ALIGNMENT:16,RGBA:17,UNSIGNED_BYTE:18,
 getError:()=>0,getParameter:p=>p===13?active:p===14?binding:p===15?array:alignment,
 activeTexture:v=>active=v,bindTexture:(t,v)=>binding=v,bindBuffer:(t,v)=>array=v,pixelStorei:(p,v)=>alignment=v,
 texSubImage2D(t,l,x,y,w,h,f,ty,pixels){if(fail)throw new Error('injected upload failure');uploads.push({x,y,w,h,pixels:pixels.slice()});for(let r=0;r<h;r++)image.set(pixels.subarray(r*w*4,(r+1)*w*4),((y+r)*extent+x)*4);},
 bufferSubData(t,offset,vertices){quad=vertices.slice();}};
 const atlas=createBrowserSpriteAtlas(gl,{texture:'ownedTexture',vbo:'ownedBuffer',stats,extent});let sequence=0;
 function plan(rows,e=1,s=++sequence){return atlas.plan(e,s,Int32Array.from(rows.flat()));}
 function commit(missing,rows,colors={},e=1,s=sequence){const size=new Map(rows.map(([id,w,h])=>[id,w*h*4]));const pixels=new Uint8Array([...missing].reduce((n,id)=>n+size.get(id),0));let off=0;for(const id of missing){pixels.fill(colors[id]??id,off,off+size.get(id));off+=size.get(id);}const q=new Float32Array(rows.length*20);for(let at=0;at<rows.length;at++)for(let n=0;n<4;n++){q[at*20+n*5]=at+1;q[at*20+n*5+3]=n===1||n===2?1:0;q[at*20+n*5+4]=n>=2?1:0;}const result=atlas.commit(e,s,pixels,q);assert.deepEqual([active,binding,array,alignment],[9,'originalTexture','originalBuffer',8]);return result;}
 return{atlas,plan,commit,stats,uploads,image,get quad(){return quad;},fail(){fail=true;}};
}
let x=fixture(),rows=[[1,2,2],[1,2,2],[2,2,2]];let missing=x.plan(rows);assert.deepEqual([...missing],[1,2]);assert.equal(x.commit(missing,rows),3);assert.equal(x.uploads.length,2);const original=x.image.slice();missing=x.plan(rows);assert.deepEqual([...missing],[]);x.commit(missing,rows);assert.equal(x.uploads.length,2,'hot frames must upload zero image bytes');assert.deepEqual(x.image,original);assert.equal(x.stats.spriteResidentImages,2);assert.equal(x.stats.spritePlanCalls,2);assert.equal(x.stats.spriteCommitCalls,2);assert.equal(x.stats.spriteUploadedBytes,32);assert.equal(x.stats.spriteSourceImageBytes,96);assert.equal(x.stats.spriteDescriptorBytes,480);assert.equal(x.stats.spriteDrawReuses,4);assert.equal(x.quad[0],1);assert.equal(x.quad[20],2,'fresh ordered geometry retained for shared ID');assert.equal(x.quad[3],x.quad[23],'shared images use same texel-center region');assert.equal(x.quad[3],.125);assert.equal(x.quad[8],.375);
// Mutation/new exact ID requests new pixels, while an inactive image may remain cached.
rows=[[3,2,2]];missing=x.plan(rows);assert.deepEqual([...missing],[3]);x.commit(missing,rows,{3:199});assert.equal(x.uploads.at(-1).pixels[0],199);
// Pinned2x4 image and two evicted2x2 holes: compact into SAME texture and reupload current IDs.
x=fixture();rows=[[1,2,4],[2,2,2],[3,2,2]];missing=x.plan(rows);x.commit(missing,rows);rows=[[4,2,4],[1,2,4],[4,2,4]];missing=x.plan(rows);assert.deepEqual([...missing],[1,4]);x.commit(missing,rows);assert.equal(x.stats.spriteCompactions,1);assert.equal(x.stats.spriteResidentImages,2);assert.equal(x.quad[3],.625);assert.equal(x.quad[23],.125);assert.equal(x.quad[43],.625);assert.equal(x.image[0],1);assert.equal(x.image[8],4);
// Ordinary LRU eviction without compaction protects every current frame ID before eviction.
x=fixture();rows=[[1,2,4],[2,2,4]];missing=x.plan(rows);x.commit(missing,rows);rows=[[1,2,4],[3,2,4]];missing=x.plan(rows);assert.deepEqual([...missing],[3]);x.commit(missing,rows);assert.equal(x.stats.spriteEvictions,1);assert.equal(x.stats.spriteCompactions,0);
// Epoch renews residency. Descriptor input is borrowed only during plan.
x=fixture();let input=Int32Array.from([1,1,1]);missing=x.atlas.plan(1,1,input);input.fill(0);x.commit(missing,[[1,1,1]],{},1,1);missing=x.atlas.plan(2,1,Int32Array.from([1,1,1]));assert.deepEqual([...missing],[1]);x.commit(missing,[[1,1,1]],{},2,1);assert.throws(()=>x.atlas.plan(1,2,Int32Array.from([])),/Stale/);assert.throws(()=>x.atlas.plan(2,2,Int32Array.from([])),/unavailable/);
for(const [action,pattern]of[
 [a=>{a.plan([[1,1,1]]);a.atlas.assertCommitted();},/not committed/],
 [a=>{a.atlas.plan(1,1,Int32Array.from([1,1,1]));a.atlas.plan(1,2,Int32Array.from([]));},/Uncommitted/],
 [a=>{a.atlas.plan(1,1,Int32Array.from([1,1,1]));a.atlas.commit(1,2,new Uint8Array(4),new Float32Array(20));},/match/],
 [a=>a.plan([[1,4,4],[2,1,1]]),/capacity/],
 [a=>a.plan([[1,5,1]]),/bounds/],
 [a=>a.plan([[1,1,1],[1,2,1]]),/dimensions/],
 [a=>{a.plan([[1,1,1]]);a.atlas.commit(1,1,new Uint8Array(0),new Float32Array(20));},/dimensions/],
 [a=>{a.plan([[1,1,1]]);a.fail();a.commit([1],[[1,1,1]]);},/injected/]
]){const a=fixture();assert.throws(()=>action(a),pattern);assert.throws(()=>a.plan([]),/unavailable/);a.atlas.dispose();a.atlas.dispose();assert.equal(a.stats.spriteResidentImages,0);}
x=fixture();missing=x.plan([]);assert.deepEqual([...missing],[]);assert.equal(x.commit(missing,[]),0);x.atlas.dispose();assert.throws(()=>x.plan([]),/unavailable/);
console.log('PASS exact-ID sprite residency: hot zero-image uploads, shared ordered descriptors, mutation, pinned LRU, same-texture compaction, borrowed-array ownership, epochs/sequence and fail-closed upload/capacity/disposal controls. No GPU/FPS claim.');
