import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {fakeGL} from './browser-overlay-delta-gl-double.mjs';
import {installUnpackSpecOracle} from './browser-overlay-unpack-spec-oracle.mjs';
const modulePath=process.argv[2];assert(modulePath,'Exact frozen candidate overlay module required');
const {createBrowserOverlay}=await import(pathToFileURL(modulePath));
const W=65,H=33;
function planes(index,value){
 const raw=new Uint8Array(W*H*4+32),floats=new Float32Array(W*H+8);
 const rgb=raw.subarray(12,12+W*H*4),t=floats.subarray(3,3+W*H).fill(1);
 if(index!==null){rgb[index*4]=value;t[index]=.25;}
 return {rgb,t};
}
function packet(p,overrides={}){return {w:W,h:H,gen:1,seq:1,reset:false,partial:false,bg:-1,bs:-1,
 bands:new Int32Array([0,H]),work:new Int32Array([1,0,16,H,1,0,1,1,512,1,512,1,0,3,3,1]),p,...overrides};}
function declare(overlay,a){return overlay.prepareDelta(a.w,a.h,a.gen,a.seq,a.reset,a.partial,a.bg,a.bs,a.bands,a.work,a.p.rgb,a.p.t);}
const f=fakeGL(),oracle=installUnpackSpecOracle(f.gl),stats={};
const overlay=createBrowserOverlay(f.gl,{stats,deltaTransport:true,overlayRectMode:'observe',maxTextureSize:4096,deferOrCheck:c=>c()});
const first=planes(1,31);declare(overlay,packet(first));overlay.begin(W,H);overlay.draw(W,H,first.rgb);
const second=planes(W*H-1,211),change=packet(second,{gen:2,seq:2,partial:true,bg:1,bs:1,
 bands:new Int32Array([0,16,32,33]),work:new Int32Array([1,0,1,17,2,1,1,1,1,2,513,1,0,3,3,257])});
declare(overlay,change);overlay.begin(W,H);overlay.draw(W,H,second.rgb);
const rgbTexture=[...f.objects].find(o=>o.format===f.gl.RGBA8&&o.pixels);
const tTexture=[...f.objects].find(o=>o.format===f.gl.R32F);
assert.deepEqual(rgbTexture.pixels,second.rgb);assert.deepEqual(tTexture.pixels,second.t);
assert(oracle.uploads.every(u=>u.data.buffer===first.rgb.buffer||u.data.buffer===first.t.buffer||u.data.buffer===second.rgb.buffer||u.data.buffer===second.t.buffer));
// Malformed metadata must fail BEFORE graphics or even oversized source access.
const invalid=[{w:0},{work:{length:1e9,get 0(){throw new Error('OVERSIZED_WORK_READ');}}},
 {work:{length:Infinity}},{work:new Int32Array(15)},{bands:{length:1e9,get 0(){throw new Error('OVERSIZED_BAND_READ');}}},
 {work:new Int32Array([1,0,1,17,2,1,1,1,1,2,513,1,0,3,3,769])},
 {work:new Int32Array([1,0,1,17,2,1,1,1,1,2,514,1,0,3,3,257])},
 {work:new Int32Array([1,0,1,17,2,1,1,1,1,2,513,1,0,4,3,257])}];
for(const mutation of invalid){const before=f.calls+oracle.trace.length;assert.throws(()=>declare(overlay,{...change,gen:3,seq:3,bg:2,bs:2,...mutation}),/Invalid/);assert.equal(f.calls+oracle.trace.length,before,'Invalid metadata touched GL');}
const cleared=planes(null);declare(overlay,packet(cleared,{gen:3,seq:3,bg:2,bs:2,partial:true,bands:[32,33],work:[0,0,0,1,1,1,1,0,0,1,1,0,0,3,3,256]}));overlay.begin(W,H);overlay.draw(W,H,cleared.rgb);
assert.deepEqual(rgbTexture.pixels,cleared.rgb);assert.deepEqual(tTexture.pixels,cleared.t);
// Even correctly sized ArrayLike metadata cannot redirect a bounded copy via an iterator.
const harmless=packet(planes(null),{gen:4,seq:4,bg:3,bs:3,partial:true,bands:[],work:[0,0,0,0,0,1,1,0,0,0,0,0,0,3,3,0]});
Object.defineProperty(harmless.work,Symbol.iterator,{get(){throw new Error('UNBOUNDED_ITERATOR_USED');}});
Object.defineProperty(harmless.bands,Symbol.iterator,{get(){throw new Error('BAND_ITERATOR_USED');}});
declare(overlay,harmless);overlay.begin(W,H);overlay.draw(W,H,harmless.p.rgb);
assert.deepEqual(rgbTexture.pixels,cleared.rgb,'No-footprint packet should not alter texture');
overlay.dispose();assert.equal(f.objects.size,0);
console.log('PASS independent observed RGB/T transfer with view offsets and exact edge footprints; malformed metadata zero-GL/no-unbounded-read; bounded ArrayLike admission; CPU specification model only');
