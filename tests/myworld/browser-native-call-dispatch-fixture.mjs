import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {browserNativeCallPolicy,createSynchronousNativeCall} from '../../tools/browser-gameplay/web/browser-native-call-dispatch.js';
import {fakeGL} from './browser-owned-fake-gl.mjs';
import {initializeSession,prepareFrame,finishFrame,cases,geometry} from './browser-owned-scenario.mjs';
const modules=await Promise.all(process.argv.slice(2,5).map(path=>import(pathToFileURL(path))));
assert.equal(modules.length,3);
for(const index of [1,2])assert.throws(()=>modules[index].createBrowserWebGL({getContext(){assert.fail('Mismatched dispatch created a GL context');}},()=>{},{nativeCallDispatch:index===2?'async':'sync'}),/module\/policy mismatch/);
// Command equivalence must not depend on how long deep byte recording/JIT takes
// in each cohort: readiness intentionally observes cadence. This is a CPU-only
// fake clock, not a production change or a performance measurement.
const originalPerformance=Object.getOwnPropertyDescriptor(globalThis,'performance');let clock=1000;
Object.defineProperty(globalThis,'performance',{configurable:true,value:{now:()=>clock}});
for(const mode of ['software','webgl'])for(const value of ['', 'nativecalls=sync','nativecalls=bad'])assert.equal(browserNativeCallPolicy(mode,new URLSearchParams(value)),'async');
assert.equal(browserNativeCallPolicy('software',new URLSearchParams('benchmark&nativecalls=sync')),'async');
assert.equal(browserNativeCallPolicy('webgl',new URLSearchParams('benchmark')),'async');
assert.equal(browserNativeCallPolicy('webgl',new URLSearchParams('benchmark&nativecalls=sync')),'sync');
assert.throws(()=>browserNativeCallPolicy('webgl',new URLSearchParams('benchmark&nativecalls=bad')),/Invalid private/);
const helperTrace=[],failure=new Error('same error identity');
const direct=createSynchronousNativeCall((a,b)=>{helperTrace.push('body');return a+b;},{before:()=>helperTrace.push('before'),failure:error=>helperTrace.push(error)});
assert.equal(direct(3,4),7);assert.deepEqual(helperTrace,['before','body']);
const failed=createSynchronousNativeCall(()=>{throw failure;},{failure:error=>helperTrace.push(error)});
assert.throws(failed,error=>error===failure);assert.equal(helperTrace.at(-1),failure);
const unhandled=[];const rejected=error=>unhandled.push(error);process.on('unhandledRejection',rejected);
assert.throws(createSynchronousNativeCall(()=>Promise.reject(failure),{name:'future async'}),/Unexpected asynchronous result/);
await new Promise(resolve=>setImmediate(resolve));assert.equal(unhandled.length,0);process.removeListener('unhandledRejection',rejected);
const shaders={vertex:'#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}',fragment:'#version 120\nuniform sampler2D uTexture;void main(){vec4 color=vec4(1.0);gl_FragColor = color;}',depthVertex:'#version 120\nattribute vec3 aPosition;uniform mat4 uLightMatrix;void main(){gl_Position=uLightMatrix*vec4(aPosition,1.0);}',depthFragment:'#version 120\nuniform sampler2D uTexture;void main(){gl_FragColor=vec4(1.0);}'};
const win={devicePixelRatio:1,addEventListener(){},removeEventListener(){}};globalThis.window=win;
function recordingState(){
 const state=fakeGL(),commands=[];
 const value=v=>ArrayBuffer.isView(v)?{type:v.constructor.name,bytes:Array.from(new Uint8Array(v.buffer,v.byteOffset,v.byteLength))}:v&&typeof v==='object'?(v.p&&v.n?{location:[v.p.id,v.n]}:{object:v.id??null}):v;
 const gl=new Proxy(state.gl,{get(target,name){const entry=target[name];return typeof entry==='function'?(...args)=>{commands.push([name,args.map(value)]);return entry(...args);}:entry;}});
 const originalCanvas=state.canvas;state.canvas=()=>{const canvas=originalCanvas();canvas.getContext=()=>gl;return canvas;};
 return {...state,commands};
}
const snapshots=[];
for(const [index,module]of modules.entries()){
 clock=1000;
 const state=recordingState(),canvas=state.canvas();canvas.getBoundingClientRect=()=>({width:384,height:256});
 const mode=index===2?'sync':'async',reports=[],backend=module.createBrowserWebGL(canvas,message=>reports.push(message),{ownedState:true,benchmarkControl:true,streamUploadValidation:true,physicalOutput:true,nativeCallDispatch:mode});
 if(index)assert.deepEqual(backend.stats.nativeCallDispatch,{requested:mode,active:mode,synchronousMethods:index===2?19:0,asynchronousMethods:index===2?1:20});
 const names=Object.keys(backend.natives),calls=new Map();assert.equal(names.length,20);
 for(const name of names){const call=backend.natives[name];backend.natives[name]=(...args)=>{calls.set(name,(calls.get(name)||0)+1);const result=call(...args);assert.equal(result instanceof Promise,index!==2||name.endsWith('_initialize'),'Incorrect native return contract '+name);return result;};}
 const session=await initializeSession(backend,shaders);
 // Upload borrowed arrays now; later Java mutation cannot affect GPU copies.
 const input=geometry(1),before=new Float32Array(input);await backend.natives.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,input,new Int32Array([0,1,2]),new Int32Array([7,0,3,1,0]));input.fill(123);assert.notDeepEqual(input,before);
 for(const test of cases()){clock+=16;await(await prepareFrame(session,test))();await finishFrame(session);}
 await backend.natives.Java_orsc_BrowserWebGLBridge_deleteTextures(null,new Int32Array([8]));
 await backend.natives.Java_orsc_BrowserWebGLBridge_deleteChunks(null,new Int32Array([3]));
 const declaration=new Int32Array([1,2,2,4,3,0,0,0,1,192,128,2]);
 await backend.natives.Java_orsc_BrowserWebGLBridge_regionTransition(null,true);await backend.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,declaration);await backend.natives.Java_orsc_BrowserWebGLBridge_holdWorld();
 assert.equal(calls.size,20,'Native inventory was not fully exercised');
 const invariant={javaFrames:backend.stats.javaFrames,worldFrames:backend.stats.javaWorldFrames,held:backend.stats.heldWorldFrames,drawCalls:backend.stats.drawCalls,triangles:backend.stats.triangles,gpuBytes:backend.stats.gpuBytes,retainedBytes:backend.stats.retainedResourceBytes,shadowBytes:backend.stats.shadowResourceBytes};
 snapshots.push({commands:structuredClone(state.commands),draws:structuredClone(state.draws),state:structuredClone(state.state),queries:[...state.queries],invariant,reports,calls:[...calls]});
 canvas.lose();const lose=()=>backend.natives.Java_orsc_BrowserWebGLBridge_holdWorld();if(index===2)assert.throws(lose,/unavailable/);else await assert.rejects(lose,/unavailable/);
 backend.dispose();backend.dispose();for(const[kind,resources]of state.resources)assert.equal(resources.size,0,'Dispose leaked '+kind);
 const stopped=()=>backend.natives.Java_orsc_BrowserWebGLBridge_lightingFrame(null,new Float32Array(81));if(index===2)assert.throws(stopped,/unavailable/);else await assert.rejects(stopped,/unavailable/);
}
assert.deepEqual(snapshots[1],snapshots[0],'ASYNC control differs from frozen original command/state/borrowed array/call count');
assert.deepEqual(snapshots[2],snapshots[0],'SYNC candidate differs from frozen original command/state/borrowed array/call count');
// Compare failure order, exact error identity/command effects and late fatal
// rejection semantics. True synchronous mode is expected to throw immediately.
const failures=[];
for(const[index,module]of modules.entries()){
 const state=fakeGL(),trace=[],backend=module.createBrowserWebGL(state.canvas(),message=>trace.push('report:'+message),{streamUploadValidation:true,nativeCallDispatch:index===2?'sync':'async'});
 trace.push('before');let result,error;
 try{result=backend.natives.Java_orsc_BrowserWebGLBridge_deleteChunks(null,new Int32Array([999]));trace.push('returned:'+String(result instanceof Promise));}catch(e){error=e;trace.push('threw:'+String(e));}
 queueMicrotask(()=>trace.push('microtask'));
 if(result instanceof Promise){try{await result;}catch(e){error=e;trace.push('rejected:'+String(e));}}
 await Promise.resolve();assert(error);assert(backend.stats.backendFailed);assert.equal(backend.stats.worldReadiness.phase,'error');
 failures.push({trace,draws:structuredClone(state.draws),resources:backend.stats.gpuBytes});backend.dispose();
}
assert.deepEqual(failures[1],failures[0],'Default async error/microtask scheduling changed');
assert.deepEqual(failures[2].draws,failures[0].draws);assert.equal(failures[2].resources,failures[0].resources);
assert(failures[2].trace.findIndex(item=>item.startsWith('threw:'))<failures[2].trace.indexOf('microtask'),'SYNC failure did not throw immediately');
for(const[index,module]of modules.entries()){
 const state=fakeGL(),backend=module.createBrowserWebGL(state.canvas(),()=>{},{ownedState:true,streamUploadValidation:true,nativeCallDispatch:index===2?'sync':'async'});
 const session=await initializeSession(backend,shaders);await(await prepareFrame(session,cases()[1]))();await finishFrame(session);
 state.inject('bufferSubData','error');await backend.natives.Java_orsc_BrowserWebGLBridge_replaceChunk(null,1,geometry(1),new Int32Array([0,1,2]),new Int32Array([7,0,3,1,0]));
 const render=await prepareFrame(session,{...cases()[1],seq:3,g:2}),draws=state.draws.length;
 await assert.rejects(render,/Streaming upload GL error/);assert.equal(state.draws.length,draws,'Queued error drew world/sky/UI');assert(backend.stats.backendFailed);backend.dispose();for(const[kind,resources]of state.resources)assert.equal(resources.size,0,'Failed batch leaked '+kind);
}
// Shader initialization is genuinely async in every mode, including an already
// fulfilled contract check. Duplicate calls and Stop must retain its old guards.
for(const[index,module]of modules.entries()){
 const state=fakeGL(),backend=module.createBrowserWebGL(state.canvas(),()=>{},{nativeCallDispatch:index===2?'sync':'async'});
 const pending=backend.natives.Java_orsc_BrowserWebGLBridge_initialize(null,shaders.vertex,shaders.fragment);
 assert(pending instanceof Promise);const duplicate=backend.natives.Java_orsc_BrowserWebGLBridge_initialize(null,shaders.vertex,shaders.fragment);
 backend.dispose();await assert.rejects(duplicate,/initialized twice/);await assert.rejects(pending,/unavailable/);backend.dispose();for(const[kind,resources]of state.resources)assert.equal(resources.size,0,'Stop/init leaked '+kind);
}
for(const method of ['createBuffer','texSubImage2D','copyTexSubImage2D']){
 const outcomes=[];
 for(const[index,module]of modules.entries()){
  const state=recordingState(),backend=module.createBrowserWebGL(state.canvas(),()=>{},{ownedState:true,streamUploadValidation:true,nativeCallDispatch:index===2?'sync':'async'});
  const session=await initializeSession(backend,shaders);await(await prepareFrame(session,cases()[1]))();await finishFrame(session);
  let action;
  if(method==='createBuffer')action=async()=>backend.natives.Java_orsc_BrowserWebGLBridge_uploadChunk(null,9,geometry(9),new Int32Array([0,1,2]),new Int32Array([7,0,3,1,0]));
  else action=await prepareFrame(session,{...cases()[1],seq:3,g:2});
  state.inject(method,method==='createBuffer'?'null':'error');const before=state.draws.length;let error;
  try{await action();}catch(e){error=e;}assert(error,'Missing real injected failure '+method);assert(backend.stats.backendFailed);
  if(method==='createBuffer')assert.equal(state.draws.length,before,'Allocation error reached draw');
  outcomes.push({error:String(error),commands:structuredClone(state.commands),draws:structuredClone(state.draws),gpuBytes:backend.stats.gpuBytes,frames:backend.stats.javaFrames,worldFrames:backend.stats.javaWorldFrames});
  await assert.rejects(async()=>backend.natives.Java_orsc_BrowserWebGLBridge_lightingFrame(null,new Float32Array(81)),/unavailable/);
  backend.dispose();for(const[kind,resources]of state.resources)assert.equal(resources.size,0,method+' leaked '+kind);
 }
 assert.deepEqual(outcomes[1],outcomes[0],'ASYNC control error/rollback changed '+method);assert.deepEqual(outcomes[2],outcomes[0],'SYNC error/rollback changed '+method);
}
delete globalThis.window;
Object.defineProperty(globalThis,'performance',originalPerformance);
if(process.argv[5])fs.writeFileSync(process.argv[5],JSON.stringify({passed:true,frames:cases().length,modes:['original-async','control-async','generated-sync'],commandsCompared:snapshots[0].commands.length,nativeCallsCompared:Object.fromEntries(snapshots[0].calls),commandTraceSha256:crypto.createHash('sha256').update(JSON.stringify(snapshots[0].commands)).digest('hex'),literalAsyncSchedulingPreserved:true,allocationRgbCopyFailureParity:true,queuedErrorNoDraw:true,stopInitRace:true,contextLossAndDispose:true,GPU:false,CheerpJSmoke:false,FPSClaim:false},null,2)+'\n');
console.log('PASS frozen original/control/SYNC42 frames: same GL commands/state/arrays, native counts/returns, retained/resize/holds, immediate fatal throw vs literalasync rejection ordering, queued-error zero-draw, contextloss/disposal; no shader pixels or CheerpJ/FPS proof');
