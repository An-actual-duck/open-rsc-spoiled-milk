import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
const {createOverlayStaging}=await import(pathToFileURL(process.argv[2]));
const W=65,H=33,N=W*H;
const rgbBacking=new Uint8Array(N*4+17),rgb=rgbBacking.subarray(3,3+N*4);
for(let i=0;i<rgb.length;i++)rgb[i]=(i*73+19)&255;
const tBuffer=new ArrayBuffer((N+7)*4),t=new Float32Array(tBuffer,12,N),bits=new Uint32Array(tBuffer,12,N);
const patterns=[0x80000000,0x7fa12345,0xffc23456,0x7f800000,0x3e800000];for(let i=0;i<N;i++)bits[i]=patterns[i%patterns.length];
const plan={width:W,height:H,rectangles:[0,0,32,16,64,32,1,1],area:513};
function expectedBytes(input,regions){const out=[];for(let n=0;n<regions.length;n+=4){const [x,y,w,h]=regions.slice(n,n+4);for(let row=0;row<h;row++)for(let col=0;col<w;col++)for(let b=0;b<4;b++)out.push(input[(((y+row)*W+x+col)*4)+b]);}return new Uint8Array(out);}
const expectedRgb=expectedBytes(rgb,plan.rectangles),expectedT=expectedBytes(new Uint8Array(t.buffer,t.byteOffset,t.byteLength),plan.rectangles);
const stats={},staging=createOverlayStaging({stats}),packed=staging.pack(plan,rgb,t);
assert.deepEqual(packed.rgb,expectedRgb);assert.deepEqual(new Uint8Array(packed.transmittance.buffer,packed.transmittance.byteOffset,packed.transmittance.byteLength),expectedT);
assert.deepEqual([...packed.offsets],[0,512]);assert.equal(packed.bytes,513*8);
assert.equal(packed.rgb.byteLength,513*4);assert.equal(packed.transmittance.byteLength,513*4);
assert.notEqual(packed.rgb.buffer,rgb.buffer);assert.notEqual(packed.transmittance.buffer,t.buffer);
const allocations=stats.overlayStagingAllocations,capacity=stats.overlayStagingRetainedBytes;
assert.equal(staging.pack(plan,rgb,t).rgb.buffer,packed.rgb.buffer);assert.equal(stats.overlayStagingAllocations,allocations,'Same capacity must reuse scratch');
const before=packed.rgb.slice();const retained=stats.overlayStagingRetainedBytes;
const invalid=[{...plan,rectangles:[0,0,32,16,64,32,2,1],area:514},{...plan,area:514},{...plan,width:0},
 {...plan,rectangles:[0,0,65,33],area:N},{...plan,rectangles:Array(36).fill(0)}];
for(const bad of invalid){assert.throws(()=>staging.pack(bad,rgb,t),/Invalid|bounded|exceeds/);assert.equal(stats.overlayStagingRetainedBytes,retained);assert.deepEqual(packed.rgb,before,'Invalid later geometry changed valid packed prefix');}
assert.throws(()=>staging.pack(plan,rgb.subarray(0,-1),t),/Invalid/);assert.throws(()=>staging.pack(plan,rgb,t.subarray(0,-1)),/Invalid/);
assert.throws(()=>staging.pack(plan,rgb,new Int32Array(N)),/Invalid/);
const exact=packed.rgb.slice();rgb.fill(0);bits.fill(0);assert.deepEqual(packed.rgb,exact,'Borrowed source mutation affected owned staging');
const zero=staging.pack({...plan,rectangles:[],area:0},rgb,t);assert.equal(zero.bytes,0);assert.equal(stats.overlayStagingRetainedBytes,capacity);assert.equal(stats.overlayStagingAllocations,allocations);
const biggerW=512,biggerH=512,bigger={width:biggerW,height:biggerH,rectangles:[0,0,512,256],area:512*256};
staging.pack(bigger,new Uint8Array(biggerW*biggerH*4),new Float32Array(biggerW*biggerH));
assert(stats.overlayStagingRetainedBytes<=12*1024*1024);assert(stats.overlayStagingPeakLiveBytes<=24*1024*1024);assert(stats.overlayStagingPeakLiveBytes>=capacity+512*256*8);
assert.equal(stats.overlayPackingMeasurements,0,'Optional timing must remain disabled');staging.dispose();assert.equal(stats.overlayStagingRetainedBytes,0);
let attempts=0;const failureStats={},badAllocator=createOverlayStaging({stats:failureStats,allocate(){attempts++;throw new Error('allocation sentinel');}});
assert.throws(()=>badAllocator.pack(invalid[0],rgb,t),/Invalid/);assert.equal(attempts,0,'Invalid geometry allocated before rejection');
assert.throws(()=>badAllocator.pack(plan,rgb,t),/allocation sentinel/);assert.equal(failureStats.overlayStagingRetainedBytes,0);badAllocator.dispose();
console.log('PASS independent tight packing layout, exact NaN/signedzero Float bits, unaligned RGB/subview bounds, synchronous source detachment, scratch reuse/caps/dispose and pre-allocation rejection; CPU only');
