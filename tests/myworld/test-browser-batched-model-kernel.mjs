import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
const {transformModelBatch,createBatchedModelKernel,batchedModelKernelPolicy}=await import(pathToFileURL(process.argv[2]));
const input=fs.readFileSync(process.argv[3]);let offset=0;const i32=()=>{const v=input.readInt32BE(offset);offset+=4;return v;};
const readArrays=()=>Array.from({length:8},()=>{const a=new Int32Array(i32());for(let n=0;n<a.length;n++)a[n]=i32();return a;});
assert.equal(i32(),0x4e4b3031);const total=i32();let packets=0,vertices=0,values=0,records=[];
function flush(){if(!records.length)return;const counts=Int32Array.from(records.map(r=>r.count)),rows=records.flatMap(r=>r.actual);assert.equal(transformModelBatch(records.length,rows,counts,records[0].camera,records[0].trig),true);for(const r of records)for(let n=0;n<8;n++){assert.deepEqual(r.actual[n],r.expected[n],`Frozen record ${r.index} array ${n}`);values+=r.actual[n].length;}packets++;records=[];}
for(let trial=0;trial<total;trial++){
  const count=i32(),camera=Int32Array.from({length:8},i32),trig=Int32Array.from({length:6},i32),actual=readArrays(),expected=readArrays();vertices+=count;
  if(records.length&&(records.length===128||!records[0].camera.every((v,n)=>v===camera[n])||!records[0].trig.every((v,n)=>v===trig[n])))flush();
  records.push({count,camera,trig,actual,expected,index:trial});
}
flush();assert.equal(offset,input.length);
let guards=0;
const valid=()=>({rows:Array.from({length:16},()=>new Int32Array([1,2,3])),counts:new Int32Array([3,3]),camera:new Int32Array([0,0,0,9,8,0,0,0]),trig:new Int32Array(6)});
function reject(p){const saved=p.rows.map(a=>a instanceof Int32Array?Int32Array.from(a):a);assert.equal(transformModelBatch(2,p.rows,p.counts,p.camera,p.trig,p.table??null),false);p.rows.forEach((a,n)=>assert.deepEqual(a,saved[n]));guards++;}
// A bad later record cannot leave a valid prefix transformed.
for(let n=8;n<16;n++)for(const invalid of [null,new Int32Array(2),new Float64Array(3),[1,2,3]]){const p=valid();p.rows[n]=invalid;reject(p);}
for(let writer=3;writer<16;writer++){if(writer%8<3)continue;for(let prior=0;prior<writer;prior++){const p=valid();p.rows[writer]=p.rows[prior];reject(p);}}
{const p=valid(),buffer=new ArrayBuffer(64);p.table=new Int32Array(buffer,0,8);p.rows[11]=new Int32Array(buffer,4,4);reject(p);}
for(const field of ['counts','camera','trig']){const p=valid();p.rows[11]=p[field];reject(p);}
{const p=valid(),buffer=new ArrayBuffer(64);p.rows[0]=new Int32Array(buffer,0,4);p.rows[11]=new Int32Array(buffer,4,4);reject(p);}
for(const value of [-1,-2147483648]){const p=valid();p.counts[1]=value;reject(p);}
{const p=valid();p.camera[4]=0;reject(p);}
// Metadata-only rows do not dereference absent arrays or write any pixels.
{const p=valid();p.counts[1]=0;p.rows.fill(null,8);assert.equal(transformModelBatch(2,p.rows,p.counts,p.camera,p.trig),true);}
assert.deepEqual(batchedModelKernelPolicy('software',new URLSearchParams('benchmark=1&modelbatch=on')),{properties:[],requested:false});
assert.deepEqual(batchedModelKernelPolicy('webgl',new URLSearchParams('modelbatch=on')),{properties:[],requested:false});
assert.deepEqual(batchedModelKernelPolicy('webgl',new URLSearchParams('benchmark=1&modelbatch=on')),{properties:['spoiledmilk.browserBatchedModelKernel=true'],requested:true});
assert.throws(()=>batchedModelKernelPolicy('webgl',new URLSearchParams('benchmark=1&modelbatch=bad')));
{const p=valid(),n=createBatchedModelKernel(),returned=n.native(null,2,p.rows,p.counts,p.camera,p.trig,null);assert.equal(returned,true);assert.equal(typeof returned?.then,'undefined');assert.equal(n.stats.packets,1);assert.equal(n.stats.models,2);assert.equal(n.stats.vertices,6);assert(Object.values(n.stats).every(x=>typeof x==='number'));}
console.log(JSON.stringify({passed:true,cases:total,packets,vertices,values,guards,scope:'Whole-packet exact frozen Java array outputs and no-write rejection; CPU only, no browser/FPS claim'}));
