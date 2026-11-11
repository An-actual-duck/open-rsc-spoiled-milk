import assert from 'node:assert/strict';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';

const modulePath=process.argv[2], reference=process.argv[3];
const {transformModelVertices,createNativeModelKernel,nativeModelKernelPolicy}=await import(pathToFileURL(modulePath));
const data=fs.readFileSync(reference);let offset=0;
const i32=()=>{const v=data.readInt32BE(offset);offset+=4;return v;};
const arrays=()=>Array.from({length:8},()=>{const a=new Int32Array(i32());for(let n=0;n<a.length;n++)a[n]=i32();return a;});
assert.equal(i32(),0x4e4b3031);const cases=i32();let vertices=0,values=0;
for(let trial=0;trial<cases;trial++){
  const count=i32(),camera=Array.from({length:8},i32),trig=Array.from({length:6},i32),actual=arrays(),expected=arrays();
  const [ox,oy,oz,shift,near,rotZ,rotY,rotX]=camera;
  assert.equal(transformModelVertices(count,...actual,ox,oy,oz,shift,near,rotZ,rotY,rotX,...trig),true);
  for(let n=0;n<actual.length;n++){
    assert.deepEqual(actual[n],expected[n],`Frozen RSModel case ${trial} array ${n}`);
    values+=actual[n].length;
  }
  vertices+=count;
}
assert.equal(offset,data.length);
let guards=0;
function rejected(args){const before=args.slice(1,9).map(a=>a instanceof Int32Array?Int32Array.from(a):a);assert.equal(transformModelVertices(...args),false);args.slice(1,9).forEach((a,n)=>assert.deepEqual(a,before[n]));guards++;}
const valid=()=>[3,...Array.from({length:8},()=>new Int32Array([1,2,3])),0,0,0,9,8,0,0,0,0,0,0,0,0,0];
for(const invalid of [0,-1,4,NaN,Infinity,1.5,0x80000000]){const a=valid();a[0]=invalid;rejected(a);}
for(let n=1;n<=8;n++)for(const invalid of [null,new Int16Array(3),new Int32Array(2),[1,2,3]]){const a=valid();a[n]=invalid;rejected(a);}
for(let n=4;n<=8;n++)for(let p=1;p<n;p++){const a=valid();a[n]=a[p];rejected(a);}
for(let n=4;n<=8;n++)for(let p=1;p<n;p++){const a=valid(),b=new ArrayBuffer(24);a[p]=new Int32Array(b,0,4);a[n]=new Int32Array(b,4,4);rejected(a);}
for(const near of [0,-1,NaN,Infinity,1.5,0x80000000]){const a=valid();a[13]=near;rejected(a);}
for(let n=9;n<23;n++)if(n!==13)for(const invalid of [undefined,NaN,Infinity,0.1,0x80000000]){const a=valid();a[n]=invalid;rejected(a);}
// Distinct views of a single buffer are supported when there is no overlap.
{const a=valid(),b=new ArrayBuffer(8*16);for(let n=1;n<=8;n++)a[n]=new Int32Array(b,(n-1)*16,3);assert.equal(transformModelVertices(...a),true);}
assert.deepEqual(nativeModelKernelPolicy('software',new URLSearchParams('benchmark=1&modelkernel=on')),{properties:[],requested:false});
assert.deepEqual(nativeModelKernelPolicy('webgl',new URLSearchParams('modelkernel=on')),{properties:[],requested:false});
assert.deepEqual(nativeModelKernelPolicy('webgl',new URLSearchParams('benchmark=1')),{properties:['spoiledmilk.browserNativeModelKernel=false'],requested:false});
assert.deepEqual(nativeModelKernelPolicy('webgl',new URLSearchParams('benchmark=1&modelkernel=on')),{properties:['spoiledmilk.browserNativeModelKernel=true'],requested:true});
assert.throws(()=>nativeModelKernelPolicy('webgl',new URLSearchParams('benchmark=1&modelkernel=bad')));
const native=createNativeModelKernel();const returned=native.native(null,...valid());assert.equal(typeof returned?.then,'undefined');assert.equal(returned,true);assert.equal(native.stats.calls,1);assert.equal(native.stats.vertices,3);assert.equal(native.stats.rejections,0);assert.equal(await native.native(null,...[0,...valid().slice(1)]),false);assert.equal(native.stats.rejections,1);
console.log(JSON.stringify({passed:true,cases,vertices,values,guards,scope:'Exact frozen Java array outputs and mutation-free unsupported-input rejection; CPU only, no FPS claim'}));
