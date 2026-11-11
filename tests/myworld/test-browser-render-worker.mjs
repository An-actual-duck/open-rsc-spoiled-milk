#!/usr/bin/env -S node --experimental-default-type=module
// CPU-only facade/replay test, not an actual browser Worker or GPU oracle.
import assert from 'node:assert/strict';
import {createBrowserRenderWorker} from '../../tools/browser-gameplay/web/browser-render-worker.js';
import {createRenderWorkerReplay,createWorkerOutputSizer} from '../../tools/browser-gameplay/web/browser-render-worker-replay.js';
import {renderWorkerPolicy,ownRenderCommand,renderNativeName} from '../../tools/browser-gameplay/web/browser-render-worker-protocol.js';
let checks=0;const check=(v,message)=>{assert(v,message);checks++;};
const endpoint=name=>renderNativeName(name);
for(const query of ['', 'renderworker=off'])check(!renderWorkerPolicy('webgl',new URLSearchParams(query)).enabled);
assert.throws(()=>renderWorkerPolicy('software',new URLSearchParams('renderworker=on&benchmark=1&overlay=coverage')));checks++;
assert.throws(()=>renderWorkerPolicy('webgl',new URLSearchParams('renderworker=on&benchmark=1&overlay=coverage&overlaydelta=on')));checks++;
check(renderWorkerPolicy('webgl',new URLSearchParams('renderworker=on&benchmark=1&overlay=coverage')).enabled);
let callbacks=0;const oldWindow=globalThis.window;globalThis.window={devicePixelRatio:1,addEventListener(){},removeEventListener(){}};
class FakeWorker {
  constructor(){this.listeners=new Map();this.sent=[];this.terminated=false;this.replay=null;this.stats={javaFrames:0,javaWorldFrames:0,javaBridgeReportedWorldFrames:0};this.bufferWords=[];}
  addEventListener(name,call){this.listeners.set(name,call);}
  emit(message){this.listeners.get('message')?.({data:message});}
  postMessage(message,buffers=[]){
    if(this.terminated)return;
    if(message.type==='init'){
      const sizer=createWorkerOutputSizer(),self=this;
      const natives={};
      natives[endpoint('initialize')]=async()=>8192;
      natives[endpoint('planWorldSprites')]=async()=>Int32Array.of(7);
      natives[endpoint('worldProgress')]=async()=>{};
      natives[endpoint('lightingFrame')]=async(lib,v)=>{self.bufferWords.push(Array.from(new Uint32Array(v.buffer,v.byteOffset,v.length)));};
      natives[endpoint('overlayFrame')]=async()=>{};
      natives[endpoint('present')]=async(lib,w,h,world)=>{self.stats.javaFrames++;if(world)self.stats.javaWorldFrames++;self.output=sizer.size(w,h);};
      natives[endpoint('frameWork')]=async()=>{self.stats.javaBridgeReportedWorldFrames=self.stats.javaWorldFrames;};
      this.replay=createRenderWorkerReplay({backend:{stats:this.stats,natives,dispose(){self.stats.disposed=true;self.stats.gpuBytes=0;self.stats.spriteResourceBytes=0;}},outputSizer:sizer});
      queueMicrotask(()=>this.emit({type:'ready',version:1}));return;
    }
    if(message.type==='readPixels'){queueMicrotask(()=>this.emit({type:'pixels',id:message.id,width:1,height:1,pixels:Uint8Array.of(1,2,3,4)}));return;}
    if(message.type==='dispose'){this.replay.dispose();queueMicrotask(()=>this.emit({type:'disposed',stats:this.stats}));return;}
    const owned=structuredClone(message,{transfer:buffers});this.sent.push(owned);
    queueMicrotask(async()=>{
      const ack=await this.replay.execute(owned);ack.stats={value:{...this.stats},samples:{},timeOrigin:performance.timeOrigin};ack.buffers=[];
      for(const c of owned.commands)for(const a of c.args)if(ArrayBuffer.isView(a))ack.buffers.push(a.buffer);
      const returned=structuredClone(ack,{transfer:ack.buffers});this.emit(returned);
    });
  }
  terminate(){this.terminated=true;}
}
const worker=new FakeWorker(),canvas={getBoundingClientRect:()=>({width:1843.55,height:1037}),transferControlToOffscreen:()=>({})};
const gpu=await createBrowserRenderWorker(canvas,()=>{}, {workerFactory:()=>worker,oracle:true,mutateReturned:true,physicalOutput:true,benchmarkControl:true,onComplete:()=>callbacks++});
check(await gpu.natives[endpoint('initialize')](null,'#version 120\nx','#version 120\ny')===8192);
gpu.stats.measurement=true;
const raw=new Uint32Array([0x7fc00001,0x80000000,0x3f800000]),plane=new Float32Array(raw.buffer);
await gpu.natives[endpoint('lightingFrame')](null,plane);raw[0]=0;raw[1]=0;
await gpu.natives[endpoint('worldProgress')](null,new Int32Array(12));
await gpu.natives[endpoint('overlayFrame')](null,960,540,0,1,1,0,false,null);
await gpu.natives[endpoint('present')](null,960,540,true,new Float32Array(48),new Float32Array(30),new Int32Array(0),new Int32Array(960*540));
check(gpu.stats.renderWorker.submittedFrames===0,'World frame remains staged before receipt');
await gpu.natives[endpoint('frameWork')](null,new Float32Array(14),new Int32Array(14));
await gpu.drain();
check(gpu.stats.javaFrames===1&&gpu.stats.javaBridgeReportedWorldFrames===1);
check(gpu.stats.renderWorker.completedFrames===1);
check(worker.bufferWords[0][0]===0x7fc00001&&worker.bufferWords[0][1]===0x80000000,'Owned raw float bits survive source mutation');
check(worker.output.width===1843&&worker.output.height===1037,'Actual main CSS metrics preserved');
check(plane.buffer.byteLength===12,'Java/source storage never detached');
check(gpu.stats.renderWorker.oracleMutatedReturnedBytes>0);
const allocated=gpu.stats.renderWorker.allocatedBuffers;
await gpu.natives[endpoint('lightingFrame')](null,plane);await gpu.natives[endpoint('present')](null,960,540,false,new Float32Array(48),new Float32Array(30),new Int32Array(0),new Int32Array(960*540));await gpu.drain();
check(gpu.stats.renderWorker.reusedBuffers>0);check(gpu.stats.renderWorker.allocatedBuffers<=allocated+1);
const pixels=await gpu.readPixels();check(pixels.pixels[3]===4);
const disposal=await gpu.disposeAndConfirm();check(disposal.disposed&&disposal.gpuBytes===0&&worker.terminated);
check(callbacks>=3);
const source=Float32Array.of(1,2,3);assert.throws(()=>ownRenderCommand('lightingFrame',[source],()=>new ArrayBuffer(1)));checks++;
check(source[0]===1);
globalThis.window=oldWindow;
console.log(JSON.stringify({passed:true,checks,scope:'CPU facade/transfer/replay only; no browser or GPU'}));
