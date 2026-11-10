// Conservative Float32 clip proof, not a GPU/image/FPS test.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {webcrypto} from 'node:crypto';
import {buildChunkColorBounds as bounds,hasResidentPositionContract as contract,isChunkOutsideColorFrustum as culled,RESIDENT_COLOR_VERTEX_SHA256} from '../../tools/browser-gameplay/web/browser-color-frustum.js';
if(!globalThis.crypto)Object.defineProperty(globalThis,"crypto",{value:webcrypto});
const shader=fs.readFileSync(process.argv[2],'utf8');assert(await contract(shader),'maintained production vertex shader not authorized');
assert(!await contract(shader.replace('0.0, uChunkOffsetZ','1.0, uChunkOffsetZ')),'same-length displacement edit escaped exact shader guard');
assert(!await contract(shader+'\n// drift'));assert(!await contract('void main(){}'));
const vertices=new Float32Array(28*3);vertices.set([4,-2,6],0);vertices.set([5,3,9],28);vertices.set([7,0,8],56);const b=bounds(vertices);assert.deepEqual(Array.from(b),[4,-2,6,7,3,9]);vertices[0]=-100;assert.equal(b[0],4,'borrowed uploaded vertices retained');assert.equal(bounds(vertices)[0],-100,'repacked geometry bounds stale');vertices[0]=NaN;assert.equal(bounds(vertices),null);
const identity=Float32Array.from([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]),scratch=new Float64Array(18);
assert(culled([5,-.1,0,6,.1,.1],identity,960,540,0,0,scratch));
assert(!culled([1.2,-.1,0,1.25,.1,.1],identity,960,540,0,0,scratch),'128px side margin lost');
assert(!culled([5,-.1,-2,6,.1,0],identity,960,540,0,0,scratch),'near intersection falsely rejected');
for(const bad of [NaN,Infinity,-Infinity]){const m=identity.slice();m[3]=bad;assert(!culled(b,m,960,540,0,0,scratch));assert(!culled(b,identity,960,540,bad,0,scratch));}
assert(!culled(null,identity,960,540,0,0,scratch));assert(!culled(b,new Float32Array(16),960,540,0,0,scratch));assert(!culled(b,identity,0,540,0,0,scratch));
assert(!culled([1e38,0,0,2e38,1,1],Float32Array.from(identity,x=>x*1e10),960,540,0,0,scratch),'overflow input must remain visible');
let seed=19834721;const random=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
const mul=(a,b)=>{const m=new Float32Array(16);for(let col=0;col<4;col++)for(let row=0;row<4;row++){let n=0;for(let k=0;k<4;k++)n+=a[k*4+row]*b[col*4+k];m[col*4+row]=n;}return m;};
function matrix(scale){const angle=random()*Math.PI*2,c=Math.cos(angle),s=Math.sin(angle),n=.1+random()*10,f=100000000,aspect=.5+random()*2,q=.5+random()*2;
 const p=Float32Array.from([q/aspect,0,0,0,0,q,0,0,0,0,(f+n)/(n-f),-1,0,0,2*f*n/(n-f),0]);
 const v=Float32Array.from([c,0,s,0,0,1,0,0,-s,0,c,0,(random()-.5)*scale,(random()-.5)*scale,(random()-.5)*scale,1]);return mul(p,v);}
// Independent per-vertex FP32 shader-order and fused-order references.
function clip(m,x,y,z,ox,oz,fused){x=Math.fround(x+Math.fround(ox));z=Math.fround(z+Math.fround(oz));const out=[];for(let r=0;r<4;r++){
 if(fused)out.push(Math.fround(m[r]*x+m[4+r]*y+m[8+r]*z+m[12+r]));
 else out.push(Math.fround(Math.fround(Math.fround(Math.fround(m[r]*x)+Math.fround(m[4+r]*y))+Math.fround(m[8+r]*z))+m[12+r]));
 }return out;}
let proved=0,checkedPoints=0;
for(let n=0;n<50000;n++){
 const scale=[1,100,1e4,1e7][n%4],cx=Math.fround((random()-.5)*scale),cy=Math.fround((random()-.5)*scale),cz=Math.fround((random()-.5)*scale),extent=scale*(.00001+random()*.2),ox=Math.fround((random()-.5)*scale),oz=Math.fround((random()-.5)*scale);
 const box=Float64Array.from([cx-extent,cy-extent,cz-extent,cx+extent,cy+extent,cz+extent],Math.fround),m=matrix(scale),w=320+n%1600,h=200+n%900;
 if(!culled(box,m,w,h,ox,oz,scratch))continue;proved++;
 const px=1+256/w,py=1+256/h;let outside=[true,true,true,true];
 for(let point=0;point<20;point++)for(const fused of [false,true]){
  const x=point<8?box[point&1?3:0]:Math.fround(box[0]+random()*(box[3]-box[0]));
  const y=point<8?box[point&2?4:1]:Math.fround(box[1]+random()*(box[4]-box[1]));
  const z=point<8?box[point&4?5:2]:Math.fround(box[2]+random()*(box[5]-box[2]));
  const [a,b,c,d]=clip(m,x,y,z,ox,oz,fused);checkedPoints++;
  const sides=[a+px*d,-a+px*d,b+py*d,-b+py*d];outside=outside.map((old,i)=>old&&sides[i]<0);
 }
 assert(outside.some(Boolean),'candidate culled an AABB with no common outside FP32 side plane: '+n);
}
assert(proved>1000,'proof did not exercise rejection');assert(checkedPoints>40000);
console.log(JSON.stringify({passed:true,boxes:50000,provedOutside:proved,checkedFloat32Points:checkedPoints,shaderSha256:RESIDENT_COLOR_VERTEX_SHA256,scope:'Conservative side-plane/rounding CPU proof, no GPU/FPS claim'}));
