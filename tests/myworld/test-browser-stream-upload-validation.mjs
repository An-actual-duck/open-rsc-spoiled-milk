#!/usr/bin/env node
// Production API + injected queued WebGL errors. No browser/GPU/FPS claim.
import assert from 'node:assert/strict';
import {createBrowserWebGL} from '../../tools/browser-gameplay/web/webgl.js';
import {createStreamUploadValidation,browserUploadValidationBenchmarkPolicy} from '../../tools/browser-gameplay/web/browser-upload-validation.js';
function fixture(enabled=true,benchmarkControl=true){
 let next=0,active=1000,vao=null,array=null,program=null,pending=0,inject=null,lost=false,polls=0,draws=0;
 const bindings=new Map(),state=new Map(),objects=new Set(),constants={NO_ERROR:0,TEXTURE0:1000};
 const allocations=['createBuffer','createTexture','createVertexArray','createShader','createProgram','createFramebuffer','fenceSync'];
 const f={getExtension:()=>null,getShaderParameter:()=>true,getProgramParameter:()=>true,getAttribLocation:()=>0,getUniformLocation:(_p,n)=>n,getShaderInfoLog:()=>'',getProgramInfoLog:()=>'',isEnabled:()=>false,isContextLost:()=>lost,
 clientWaitSync:()=>gl.CONDITION_SATISFIED,
 getError:()=>{polls++;const e=pending;pending=0;return e;},checkFramebufferStatus:()=>gl.FRAMEBUFFER_COMPLETE,
 getParameter:p=>p===gl.MAX_TEXTURE_SIZE?4096:p===gl.ACTIVE_TEXTURE?active:p===gl.TEXTURE_BINDING_2D?bindings.get(active)||null:p===gl.VERTEX_ARRAY_BINDING?vao:p===gl.ARRAY_BUFFER_BINDING?array:p===gl.CURRENT_PROGRAM?program:p===gl.COLOR_WRITEMASK?[true,true,true,true]:p===gl.COLOR_CLEAR_VALUE?[0,0,0,1]:p===gl.VIEWPORT?[0,0,2,2]:p===gl.DEPTH_WRITEMASK?true:state.get(p)||0,
 activeTexture:v=>active=v,bindTexture:(_t,v)=>bindings.set(active,v),bindVertexArray:v=>vao=v,bindBuffer:(_t,v)=>array=v,useProgram:v=>program=v,
 drawElements:()=>draws++,drawArrays:()=>draws++};
 const gl=new Proxy(f,{get(o,k){if(/^[A-Z0-9_]+$/.test(k))return constants[k]??=2000+Object.keys(constants).length;
  return(...args)=>{if(inject===k){inject=null;pending=gl.INVALID_OPERATION;}
   if(allocations.includes(k)){const v={id:++next};objects.add(v);return v;}
   if(k.startsWith('delete')){if(args[0])assert(objects.delete(args[0]),'unknown/double delete '+k);return;}
   return o[k]?.(...args);
  };}});
 const backend=createBrowserWebGL({width:2,height:2,getContext:()=>gl,addEventListener(){},removeEventListener(){}},()=>{},{benchmarkControl,streamUploadValidation:enabled});
 return{backend,n:backend.natives,gl,objects,get draws(){return draws;},resetDraws(){draws=0;},get polls(){return polls;},fault(method){inject=method;},lose(){lost=true;}};
}
const v='#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}';
const f='#version 120\nvoid main(){vec4 color=vec4(1.0);gl_FragColor = color;}';
const vertices=new Float32Array(84),indices=new Int32Array([0,1,2]),batches=new Int32Array([7,0,3,1,0]);
const palette=new Float32Array(4096),skyValues=new Float32Array(20);skyValues[0]=1;skyValues[3]=1;
const skyGeometry=new Float32Array([0,0,0,0, 1,0,0,0, 0,1,0,0]);
const ranges=new Int32Array([0,3,0,3,0,1,3,0,1,3,0,1]);
async function sky(x){await x.n.Java_orsc_BrowserWebGLBridge_skyFrame(null,skyValues,palette);}
async function overlay(x,seq=(x.seq||0)+1){await x.n.Java_orsc_BrowserWebGLBridge_overlayFrame(null,2,2,1,1,seq,0,false,new Float32Array(4));x.seq=seq;x.declared=true;}
async function present(x){if(!x.declared)await overlay(x);x.declared=false;await x.n.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,1,1,1,0,0,0,1,2,2,1]));await x.n.Java_orsc_BrowserWebGLBridge_present(null,2,2,true,new Float32Array(48),new Float32Array(30),new Int32Array([1,0,0,0,0,2,0,0]),new Int32Array(4));}
async function complete(x){await x.n.Java_orsc_BrowserWebGLBridge_frameWork(null,new Float32Array(14),new Int32Array(14));}
async function setup(enabled=true){const x=fixture(enabled),n=x.n;
 await n.Java_orsc_BrowserWebGLBridge_initialize(null,v,f);await n.Java_orsc_BrowserWebGLBridge_initializeLighting(null,v,f);await n.Java_orsc_BrowserWebGLBridge_initializeSky();
 await n.Java_orsc_BrowserWebGLBridge_uploadChunk(null,1,vertices,indices,batches);
 await n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,1,1,new Int32Array([-1]),false);
 await n.Java_orsc_BrowserWebGLBridge_uploadSkyGeometry(null,skyGeometry,ranges,1);
 const light=new Float32Array(81);light[0]=1;light[1]=1;light[3]=16;light[5]=16;light[16]=1/16;
 await n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,light);await sky(x);await overlay(x,1);await present(x);await complete(x);x.resetDraws();return x;}
const streams={chunk:async x=>x.n.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,vertices,indices,batches),sky,
 coverage:x=>overlay(x,2),atlas:async x=>{await x.n.Java_orsc_BrowserWebGLBridge_planWorldSprites(null,1,1,new Int32Array([1,1,1]));await x.n.Java_orsc_BrowserWebGLBridge_commitWorldSprites(null,1,1,new Uint8Array([1,2,3,255]),new Float32Array(20));}};
for(const [name,stream]of Object.entries(streams)){
 const x=await setup();const before=x.backend.stats.javaFrames,lastFrame=x.backend.stats.lastFrame,worldFrames=x.backend.stats.javaWorldFrames,passed=x.backend.stats.streamUploadValidationPassed;
 x.fault(name==='chunk'?'bufferSubData':'texSubImage2D');await stream(x);
 assert.equal(x.draws,0);assert.equal(x.backend.stats.streamUploadValidationPassed,passed);
 await assert.rejects(()=>present(x),/Streaming upload GL error/);
 assert.equal(x.draws,0,name+' drew shadows/world/sky/UI before rejecting queued error');assert.equal(x.backend.stats.javaFrames,before);assert.equal(x.backend.stats.javaWorldFrames,worldFrames);assert.equal(x.backend.stats.lastFrame,lastFrame);assert.equal(x.backend.stats.worldReadiness.phase,'error');
 assert.equal(x.backend.stats.streamUploadValidationFailures,1);assert.equal(x.backend.stats.backendFailed,true);
 await assert.rejects(()=>x.n.Java_orsc_BrowserWebGLBridge_lightingFrame(null,new Float32Array(81)),/unavailable/);
 await assert.rejects(()=>complete(x),/unavailable/);assert.throws(()=>x.backend.queueStreamUploadValidation(false),/unavailable/);
 x.backend.dispose();x.backend.dispose();assert.equal(x.objects.size,0,name+' leaked resources after tentative content failure');
}
// Zero-image atlas hot commits still validate the streamed descriptor buffer.
{const x=await setup();await streams.atlas(x);await present(x);await complete(x);x.resetDraws();
 await x.n.Java_orsc_BrowserWebGLBridge_planWorldSprites(null,1,2,new Int32Array([1,1,1]));x.fault('bufferSubData');await x.n.Java_orsc_BrowserWebGLBridge_commitWorldSprites(null,1,2,new Uint8Array(),new Float32Array(20));await assert.rejects(()=>present(x),/Streaming upload/);assert.equal(x.draws,0);x.backend.dispose();assert.equal(x.objects.size,0);}
// Context loss without an event also poisons before any draw.
{const x=await setup();x.lose();await assert.rejects(()=>present(x),/context lost/);assert.equal(x.draws,0);assert.equal(x.backend.stats.backendFailed,true);x.backend.dispose();assert.equal(x.objects.size,0);}
// Legacy policy is literal immediate validation; allocation/shape change stays immediate when enabled.
for(const enabled of [false,true]){const x=await setup(enabled),before=x.backend.stats.gpuBytes;
 x.fault('texImage2D');await assert.rejects(()=>x.n.Java_orsc_BrowserWebGLBridge_uploadTexture(null,9,2,1,new Int32Array(2),false),/WebGL error 0x/);assert.equal(x.backend.stats.gpuBytes,before);assert.equal(x.draws,0);x.backend.dispose();assert.equal(x.objects.size,0);}
{const x=await setup(false);x.fault('bufferSubData');await assert.rejects(()=>streams.chunk(x),/WebGL error 0x/);assert.equal(x.draws,0);x.backend.dispose();}
// RGB uploads remain immediate after world draw; never defer them into next frame.
{const x=await setup();await overlay(x,2);x.fault('texSubImage2D');await assert.rejects(()=>present(x),/Coverage GL error 0x/);assert(x.draws>0,'RGB still occurs after the world pass');assert.equal(x.backend.stats.backendFailed,true);x.backend.dispose();assert.equal(x.objects.size,0);}
// One pre-draw poll replaces all eligible checks. RGB/postdraw/retained polls remain immediate.
{const x=await setup(),s=x.backend.stats,polls=x.polls;await streams.chunk(x);await sky(x);await overlay(x,2);await streams.atlas(x);assert.equal(x.polls,polls);
 const before=s.streamUploadValidationChecks;await present(x);assert.equal(s.streamUploadValidationChecks,before+1);assert(x.polls>polls+1,'RGB/retained immediate checks disappeared');await complete(x);
 x.backend.queueStreamUploadValidation(false);assert.equal(s.streamUploadValidationActive,true);await sky(x);await overlay(x,3);await present(x);assert.equal(s.presentedStreamUploadValidation,true);await complete(x);assert.equal(s.streamUploadValidationActive,false);const p=x.polls;await sky(x);assert.equal(x.polls,p+1);x.backend.dispose();assert.equal(x.objects.size,0);}
{let polls=0;const stats={},g=createStreamUploadValidation({NO_ERROR:0,getError(){polls++;return 0;}},{stats,enabled:true});g.deferOrCheck(()=>assert.fail());assert.throws(()=>g.frameComplete(),/Unvalidated/);assert.throws(()=>g.queue(false),/unavailable/);assert.equal(polls,0);}
{const x=fixture(true,false);assert.equal(x.backend.stats.streamUploadValidationActive,false);assert.throws(()=>x.backend.queueStreamUploadValidation(true),/Private benchmark/);x.backend.dispose();assert.equal(x.objects.size,0);}
for(const [mode,q,expected]of [['software','benchmark&uploadvalidation=on',false],['webgl','uploadvalidation=on',false],['webgl','benchmark',false],['webgl','benchmark&uploadvalidation=on',true]])assert.equal(browserUploadValidationBenchmarkPolicy(mode,new URLSearchParams(q)),expected);
assert.throws(()=>browserUploadValidationBenchmarkPolicy('webgl',new URLSearchParams('benchmark&uploadvalidation=no')),/Invalid/);
console.log('PASS production queued errors for chunk/atlas pixels+hot descriptors/sky/coverage, zero shadow/world/UI draws, sticky failure/cleanup, allocations immediate, policy atomicity/default isolation. CPU API double only.');
