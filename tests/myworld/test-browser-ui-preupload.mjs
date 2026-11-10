#!/usr/bin/env node
// State/order/token/resource/error contracts only; actual pixels require the hardware runner.
import assert from 'node:assert/strict';
import {createBrowserWebGL} from '../../tools/browser-gameplay/web/webgl.js';
import {createBrowserOverlay} from '../../tools/browser-gameplay/web/browser-overlay.js';
import {createStreamUploadValidation} from '../../tools/browser-gameplay/web/browser-upload-validation.js';
import {browserUiPreuploadBenchmarkPolicy as policy} from '../../tools/browser-gameplay/web/browser-ui-preupload.js';
import {fakeGL} from './browser-owned-fake-gl.mjs';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const shaders={vertex:'#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}',fragment:'#version 120\nuniform sampler2D uTexture;void main(){vec4 color=vec4(1.0);gl_FragColor = color;}',depthVertex:'#version 120\nattribute vec3 aPosition;uniform mat4 uLightMatrix;void main(){gl_Position=uLightMatrix*vec4(aPosition,1.0);}',depthFragment:'#version 120\nuniform sampler2D uTexture;void main(){gl_FragColor=vec4(1.0);}'};
for(const mode of ['webgl','software','input-probe'])for(const query of ['', 'ui-preupload=on','ui-preupload=bad'])assert.equal(policy(mode,new URLSearchParams(query)),false);
assert.equal(policy('software',new URLSearchParams('benchmark&ui-preupload=on')),false);assert.equal(policy('webgl',new URLSearchParams('benchmark')),false);assert.equal(policy('webgl',new URLSearchParams('benchmark&ui-preupload=on')),true);assert.throws(()=>policy('webgl',new URLSearchParams('benchmark&ui-preupload=bad')),/Invalid private/);
function noLeaks(state){for(const [kind,set]of state.resources)assert.equal(set.size,0,'Leaked '+kind);}
const x=fakeGL(),stats={},gate=createStreamUploadValidation(x.gl,{stats,enabled:true}),overlay=createBrowserOverlay(x.gl,{stats,deferOrCheck:gate.deferOrCheck});const rgba=Uint8Array.from({length:16},(_,i)=>i*10),saved=rgba.slice(),t=new Float32Array(4).fill(1);
overlay.prepare(2,2,1,1,1,0,true,t);const before=x.errors;overlay.prepareRgb(2,2,rgba);assert.equal(x.errors-before,2,'First shader/storage checks must be immediate');assert.equal(x.draws.length,0,'RGB preupload drew');rgba.fill(0);const rgb=[...x.resources.get('texture')].find(tex=>tex.format===x.gl.RGBA8);assert.deepEqual(rgb.bytes,saved,'Borrowed RGB view retained');assert.throws(()=>overlay.prepareRgb(2,2,rgba),/unconsumed/);assert.throws(()=>overlay.prepare(2,2,1,1,2,0,false,t),/consumed/);gate.beforeDraw();overlay.begin(2,2);assert.throws(()=>overlay.draw(2,2,rgba),/already staged/);assert.throws(()=>overlay.drawPrepared(3,2),/token mismatch/);overlay.drawPrepared(2,2);assert.throws(()=>overlay.drawPrepared(2,2),/token mismatch/);gate.frameComplete();
overlay.prepare(2,2,1,2,2,0,false,t);const same=x.errors;overlay.prepareRgb(2,2,rgba);assert.equal(x.errors,same,'Same-size pre-draw RGB check was not batched');gate.beforeDraw();overlay.begin(2,2);overlay.drawPrepared(2,2);gate.frameComplete();
// Retained errors are rejected before RGB storage is touched.
overlay.prepare(2,2,3,0,3,1,false,t);const count=x.events.filter(e=>e.method==='texSubImage2D'&&e.args[6]===x.gl.RGBA).length;assert.throws(()=>overlay.prepareRgb(2,2,rgba),/stale/);assert.equal(x.events.filter(e=>e.method==='texSubImage2D'&&e.args[6]===x.gl.RGBA).length,count);overlay.dispose();overlay.dispose();gate.dispose();noLeaks(x);
function normalized(draw){if(draw.op==='copy')return {op:'copy',source:draw.source===null?'default':'private',args:draw.args};const {draw:target,samples,...rest}=draw;return {...rest,...(target===undefined?{}:{draw:target===null?'default':'private'})};}
const sessions=[];
for(const [name,uiPreupload,benchmarkControl]of [['off',false,false],['on',true,false],['live',false,true]]){const state=fakeGL(),backend=createBrowserWebGL(state.canvas(),()=>{},{ownedState:true,streamUploadValidation:true,uiPreupload,benchmarkControl});sessions.push({...await initializeSession(backend,shaders),state,name});}
let compared=0,transitions=0;
for(const test of cases()){
 const streams=[];
 for(const s of sessions){const was=s.backend.stats.uiPreuploadActive;s.state.draws.length=0;const render=await prepareFrame(s,test);if(s.name==='live'){s.backend.queueUiPreupload(!was);assert.equal(s.backend.stats.uiPreuploadActive,was);}
  await render();assert.equal(s.backend.stats.presentedUiPreupload,was);assert.equal(s.backend.stats.uiPreuploadUsed,was);streams.push(s.state.draws.map(normalized));await finishFrame(s);if(s.name==='live'){assert.equal(s.backend.stats.uiPreuploadActive,!was);transitions++;}assert.equal(s.backend.stats.worldFrameSamples.length,s.backend.stats.worldFrameUiPreuploadPolicies.length);
 }
 assert.deepEqual(streams[1],streams[0],'Effective draw/copy state differs ON '+test.name);assert.deepEqual(streams[2],streams[0],'Effective draw/copy state differs live '+test.name);compared+=streams[0].length;
}
for(const s of sessions){s.backend.dispose();s.backend.dispose();noLeaks(s.state);}
// Real GL error position is modeled here; the GPU runner injects an actual invalid upload.
for(const [stream,method]of [[true,'texSubImage2D'],[false,'texSubImage2D'],[true,'texImage2D']]){
 const state=fakeGL({timer:true}),backend=createBrowserWebGL(state.canvas(),()=>{},{ownedState:true,streamUploadValidation:stream,uiPreupload:true,benchmarkControl:true}),s=await initializeSession(backend,shaders);
 if(method==='texSubImage2D'){const warm=await prepareFrame(s,cases()[1]);await warm();await finishFrame(s);}
 const render=await prepareFrame(s,{...cases()[1],seq:5,g:2});state.injectWhen(method,a=>method==='texSubImage2D'?a[4]===192&&a[5]===128&&a[6]===state.gl.RGBA:a[3]===192&&a[4]===128&&a[2]===state.gl.RGBA8);const mark=state.events.length;await assert.rejects(render,/Streaming upload GL error|Coverage GL error/);const events=state.events.slice(mark),fault=events.findIndex(e=>e.injected);assert.ok(fault>=0);assert.equal(events.slice(fault+1).filter(e=>/^(drawArrays|drawElements|clear|copyTexSubImage2D|createQuery|beginQuery|getQueryParameter)$/.test(e.method)).length,0,'Failed RGB reached draw/query');assert.equal(backend.stats.backendFailed,true);for(const name of ['queueUiPreupload','queueOwnedState','queueStreamUploadValidation'])assert.throws(()=>backend[name](false),/unavailable/);await assert.rejects(()=>finishFrame(s),/unavailable/);backend.dispose();backend.dispose();noLeaks(state);
}
// Bounds validation runs before upload or timer creation.
{const state=fakeGL({timer:true}),backend=createBrowserWebGL(state.canvas(),()=>{},{ownedState:true,streamUploadValidation:true,uiPreupload:true});await initializeSession(backend,shaders);const mark=state.events.length;await assert.rejects(()=>backend.natives.Java_orsc_BrowserWebGLBridge_present(null,1.5,2,true,new Float32Array(48),new Float32Array(30),new Int32Array(0),new Int32Array(3)),/bounds/);assert.equal(state.events.slice(mark).filter(e=>/^(texImage2D|texSubImage2D|createQuery|drawArrays|drawElements)$/.test(e.method)).length,0);backend.dispose();noLeaks(state);}
// The same32MiB combined peak remains valid for both maximum-area aspect shapes.
{const state=fakeGL(),stats={},gate=createStreamUploadValidation(state.gl,{stats,enabled:true}),m=createBrowserOverlay(state.gl,{stats,deferOrCheck:gate.deferOrCheck});for(const [w,h,seq]of [[2048,1024,1],[1024,2048,2]]){m.prepare(w,h,1,seq,seq,0,false,new Float32Array(w*h));m.prepareRgb(w,h,new Uint8Array(w*h*4));gate.beforeDraw();m.begin(w,h);m.drawPrepared(w,h);gate.frameComplete();assert.equal(stats.overlayCompositeResourceBytes,32*1024*1024);assert.ok(stats.overlayCompositePeakBytes<=32*1024*1024);}assert.throws(()=>m.prepare(2049,1024,1,3,3,0,false,new Float32Array(0)),/bounds/);m.dispose();gate.dispose();noLeaks(state);}
console.log(JSON.stringify({passed:true,frames:cases().length,comparedOperations:compared,transitions,scope:'Effective-state/order/token/borrowed-buffer/error/resource oracle; no GPU pixels or FPS claim'}));
