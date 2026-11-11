import assert from 'node:assert/strict';
import {readUnpackedPixels} from './browser-overlay-unpack-spec-oracle.mjs';
const bytes=Uint8Array.from({length:100},(_,i)=>i),view=bytes.subarray(8,72);
assert.deepEqual([...readUnpackedPixels({view,width:2,height:2,format:'RGBA',type:'UNSIGNED_BYTE',
  rowLength:4,skipRows:1,skipPixels:1,srcOffset:4})],[32,33,34,35,36,37,38,39,48,49,50,51,52,53,54,55]);
const floats=Float32Array.from({length:32},(_,i)=>i+.25),floatView=floats.subarray(4,20);
assert.deepEqual([...readUnpackedPixels({view:floatView,width:2,height:2,format:'RED',type:'FLOAT',
  rowLength:3,skipRows:1,skipPixels:1,srcOffset:1,alignment:8})],[10.25,11.25,14.25,15.25]);
assert.deepEqual([...readUnpackedPixels({view:new Float32Array([1,2,99,99,3,4]),width:2,height:2,
  format:'RED',type:'FLOAT',rowLength:3,alignment:8})],[1,2,3,4],'Final-row padding must not be required');
const base={view,width:2,height:2,format:'RGBA',type:'UNSIGNED_BYTE'};
for(const change of [{pixelUnpackBuffer:{}},{srcOffset:65},{srcOffset:-1},{rowLength:2,skipPixels:1},
  {view:bytes.subarray(90),rowLength:4},{view:new Float32Array(64)},{alignment:3}])
  assert.throws(()=>readUnpackedPixels({...base,...change}),/INVALID_OPERATION/);
const small=bytes.subarray(0,4);
assert.throws(()=>readUnpackedPixels({...base,view:small}),/view too small/,'Backing buffer capacity cannot override view bounds');
assert.deepEqual([...readUnpackedPixels({view:new Uint8Array([10,20,30,0,40,50,60,255]),width:1,height:2,format:'RGBA',type:'UNSIGNED_BYTE',flipY:true,premultiplyAlpha:true})],[40,50,60,255,0,0,0,0]);
const floatBits=new Uint32Array([0x80000000,0x7fa12345,0xffa23456,0x3f800000]);
const transported=readUnpackedPixels({view:new Float32Array(floatBits.buffer),width:2,height:2,format:'RED',type:'FLOAT'});
assert.deepEqual(new Uint32Array(transported.buffer),floatBits,'Raw float transfer preserves signedzero and NaN payload bits');
console.log('PASS independent Khronos unpack arithmetic: view offsets, element units, row stride/skip/alignment, exact tail, PBO/type/range constraints; CPU specification model only');
