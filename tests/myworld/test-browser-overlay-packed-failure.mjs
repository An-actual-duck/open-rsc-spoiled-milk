import assert from 'node:assert/strict';
import {createBrowserOverlay} from '../../tools/browser-gameplay/web/browser-overlay.js';
import {fakeGL} from './browser-overlay-delta-gl-double.mjs';
const W=65,H=33;
for(const fault of ['allocation','copy']){
 const f=fakeGL(),stats={},module=createBrowserOverlay(f.gl,{stats,deltaTransport:true,overlayRectMode:'packed',maxTextureSize:4096});
 const rgb=new Uint8Array(W*H*4),t=new Float32Array(W*H).fill(1);
 module.prepareDelta(W,H,1,1,false,false,-1,-1,[0,H],[0,0,0,H,1,0],rgb,t);module.begin(W,H);module.draw(W,H,rgb);
 const before=f.calls,NativeBuffer=globalThis.ArrayBuffer,nativeSet=Uint8Array.prototype.set;
 try{
  if(fault==='allocation')globalThis.ArrayBuffer=new Proxy(NativeBuffer,{construct(target,args){if(args[0]===65536)throw new Error('injected staging allocation failure');return Reflect.construct(target,args);}});
  else Uint8Array.prototype.set=function(...args){if(this.buffer.byteLength===65536)throw new Error('injected staging copy failure');return nativeSet.apply(this,args);};
  assert.throws(()=>module.prepareDelta(W,H,2,2,false,true,1,1,[0,0,32,16],[1,0,16,0,1,1,2,1,512,1,512,1,0,3,3,1],rgb,t),/injected staging/);
 }finally{globalThis.ArrayBuffer=NativeBuffer;Uint8Array.prototype.set=nativeSet;}
 assert.equal(f.calls,before,'Staging failure touched graphics before packing completed');
 assert.throws(()=>module.begin(W,H),/unavailable/,'Failed packed transaction resumed');
 module.dispose();assert.equal(stats.overlayStagingRetainedBytes,0);assert.equal(f.objects.size,0,'Failed transaction leaked GPU resources');
}
console.log('PASS actual packed prepare allocation/copy failures before ANY GL, backend poison and scratch/GPU disposal; no GPU');
