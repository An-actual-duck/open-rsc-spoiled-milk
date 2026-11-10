import assert from 'node:assert/strict';
import {createBrowserWebGL} from '../../tools/browser-gameplay/web/webgl.js';
import {physicalOutputSize} from '../../tools/browser-gameplay/web/browser-physical-output.js';
import {fakeGL} from './browser-owned-fake-gl.mjs';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const shaders={vertex:'#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}',fragment:'#version 120\nuniform sampler2D uTexture;void main(){vec4 color=vec4(1.0);gl_FragColor = color;}',depthVertex:'#version 120\nattribute vec3 aPosition;uniform mat4 uLightMatrix;void main(){gl_Position=uLightMatrix*vec4(aPosition,1.0);}',depthFragment:'#version 120\nuniform sampler2D uTexture;void main(){gl_FragColor=vec4(1.0);}'};
const handlers=new Map(),win={devicePixelRatio:1,addEventListener:(n,f)=>{const fs=handlers.get(n)||new Set();fs.add(f);handlers.set(n,fs);},removeEventListener:(n,f)=>handlers.get(n)?.delete(f)};globalThis.window=win;
let rect={width:384,height:256},reads=0;
const a=fakeGL(),canvas=a.canvas();canvas.getBoundingClientRect=()=>{reads++;return rect;};const on=createBrowserWebGL(canvas,()=>{},{ownedState:true,physicalOutput:true}),session=await initializeSession(on,shaders);
const b=fakeGL(),offCanvas=b.canvas(),off=createBrowserWebGL(offCanvas,()=>{},{ownedState:true}),reference=await initializeSession(off,shaders);
const signal=()=>{for(const f of handlers.get('browser-display-change')||[])f();};
let checked=0;
for(const test of cases()){
 if(test.name==='resized-world'){rect={width:512,height:320};signal();}
 // A mid-fade CSS/DPR change MUST defer until a fresh world/opaque source.
 if(test.name==='fade-2'){rect={width:576,height:384};signal();}
 if(test.name==='world-3'){rect={width:384,height:256};signal();}
 a.draws.length=b.draws.length=0;const render=await prepareFrame(session,test),oldWidth=canvas.width,oldHeight=canvas.height;await render();await (await prepareFrame(reference,test))();
 assert.equal(on.stats.physicalOutput.logicalWidth,test.w);assert.equal(on.stats.physicalOutput.logicalHeight,test.h);
 if(test.mode===2||test.mode===3){assert.equal(canvas.width,oldWidth);assert.equal(canvas.height,oldHeight);}else {const wanted=physicalOutputSize(test.w,test.h,rect,win.devicePixelRatio);assert.equal(canvas.width,wanted.width);assert.equal(canvas.height,wanted.height);}
 const world=draws=>draws.filter(d=>d.op==='elements'&&d.draw===null&&d.program?.includes('uTexture'));
 assert.deepEqual(world(a.draws).map(d=>d.uniforms),world(b.draws).map(d=>d.uniforms),'world projection/lighting changed with physical viewport');
 for(const draw of world(a.draws))assert.deepEqual(draw.viewport,[0,0,canvas.width,canvas.height]);
 const ui=a.draws.findLast(d=>d.op==='arrays'&&d.program?.includes('uniform vec2 logicalSize'));
 assert(ui,'physical output missing explicit logical UI mapping');assert.deepEqual(ui.uniforms.find(([n])=>n==='logicalSize')[1],[test.w,test.h]);assert.deepEqual(ui.uniforms.find(([n])=>n==='outputSize')[1],[canvas.width,canvas.height]);
 assert.equal(on.stats.overlayRgbBytes,test.w*test.h*4,'CPU RGB upload grew with physical output');
 if(test.mode!==0)assert.equal(on.stats.overlayCoverageBytes,test.w*test.h*4,'CPU T upload grew with physical output');
 assert.equal(on.stats.retainedResourceBytes,canvas.width*canvas.height*8);assert(on.stats.overlayCompositePeakBytes<=48*1024*1024);assert.equal(off.stats.overlayCompositePeakBudgetBytes,32*1024*1024);
 for(const copy of a.draws.filter(d=>d.op==='copy'))assert.deepEqual(copy.args.slice(-2),[canvas.width,canvas.height]);
 await finishFrame(session);await finishFrame(reference);checked++;
}
assert(reads<10,'physical size hot path forced DOM reads');
a.draws.length=0;await on.natives.Java_orsc_BrowserWebGLBridge_regionTransition(null,true);await on.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,2,4,2,0,0,0,1,192,128,2]));await on.natives.Java_orsc_BrowserWebGLBridge_holdWorld();assert.equal(a.draws.filter(d=>d.op==='arrays').length,1);assert.equal(a.draws.filter(d=>d.op==='copy').length,0);assert.deepEqual(a.draws.find(d=>d.op==='arrays').viewport,[0,0,canvas.width,canvas.height]);
canvas.lose();await assert.rejects(()=>on.natives.Java_orsc_BrowserWebGLBridge_holdWorld(),/unavailable/);on.dispose();on.dispose();off.dispose();for(const state of [a,b])for(const [kind,set]of state.resources)assert.equal(set.size,0,'physical output leaked '+kind);for(const fs of handlers.values())assert.equal(fs.size,0);
// Benchmark controls queue across retained/held frames and commit only at a fresh source boundary.
const c=fakeGL(),cc=c.canvas();cc.getBoundingClientRect=()=>({width:384,height:256});const controlled=createBrowserWebGL(cc,()=>{},{ownedState:true,benchmarkControl:true,physicalOutputControl:true}),cs=await initializeSession(controlled,shaders);
let cr=await prepareFrame(cs,{name:'control-world',mode:1,world:true,seq:1,g:1,w:192,h:128});await cr();await finishFrame(cs);assert.equal(controlled.stats.physicalOutputActive,false);assert.equal(cc.width,192);
controlled.queuePhysicalOutput(true);assert.equal(controlled.stats.physicalOutputActive,false);
cr=await prepareFrame(cs,{name:'control-missing-world',mode:1,world:false,seq:1,g:1,w:192,h:128});await cr();await finishFrame(cs);assert.equal(controlled.stats.physicalOutputActive,false,'Missing world masqueraded as opaque reset');
cr=await prepareFrame(cs,{name:'control-fade',mode:3,count:1,world:true,seq:2,g:1,w:192,h:128});await cr();await finishFrame(cs);assert.equal(cc.width,192);assert.equal(controlled.stats.pendingPhysicalOutput,true);
await controlled.natives.Java_orsc_BrowserWebGLBridge_regionTransition(null,true);await controlled.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,new Int32Array([1,2,2,4,2,0,0,0,1,192,128,2]));await controlled.natives.Java_orsc_BrowserWebGLBridge_holdWorld();assert.equal(controlled.stats.physicalOutputActive,false);
cr=await prepareFrame(cs,{name:'control-world-on',mode:1,world:true,seq:3,g:2,w:192,h:128});await cr();await finishFrame(cs);assert.equal(controlled.stats.physicalOutputActive,true);assert.equal(cc.width,384);assert.equal(controlled.stats.pendingPhysicalOutput,null);
controlled.queuePhysicalOutput(false);cr=await prepareFrame(cs,{name:'control-fade-off-pending',mode:3,count:1,world:true,seq:4,g:2,w:192,h:128});await cr();await finishFrame(cs);assert.equal(controlled.stats.physicalOutputActive,true);assert.equal(cc.width,384);
cr=await prepareFrame(cs,{name:'control-opaque-off',mode:0,world:false,seq:5,g:3,w:192,h:128});await cr();await finishFrame(cs);assert.equal(controlled.stats.physicalOutputActive,false);assert.equal(cc.width,192);assert.equal(controlled.stats.physicalOutputTransitions.length,2);assert.equal(controlled.stats.worldFrameSamples.length,controlled.stats.physicalOutputWorldFramePolicies.length);assert.throws(()=>controlled.queuePhysicalOutput(1),/unavailable/);controlled.dispose();for(const [kind,set]of c.resources)assert.equal(set.size,0,'controlled output leaked '+kind);
delete globalThis.window;
console.log(`PASS ${checked} actual-pipeline CPU frames: logical projection/upload invariance, physical viewport/capture, deferred fade/BASE resize, FULL hold, OFF32MiB vs ON48MiB bound, context loss/disposal. No shader pixels/FPS claim.`);
