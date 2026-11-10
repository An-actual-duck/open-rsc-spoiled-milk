#!/usr/bin/env node
// Exact commands/state/error-accounting test, NOT a rasterized pixel/FPS claim.
import assert from 'node:assert/strict';
import {createBrowserWebGL} from '../../tools/browser-gameplay/web/webgl.js';
import {fakeGL} from './browser-owned-fake-gl.mjs';
import {initializeSession,prepareFrame,finishFrame,cases} from './browser-owned-scenario.mjs';
const shaders={vertex:'#version 120\nattribute vec3 aPosition;void main(){gl_Position=vec4(aPosition,1.0);}',fragment:'#version 120\nuniform sampler2D uTexture;void main(){vec4 color=vec4(1.0);gl_FragColor = color;}',depthVertex:'#version 120\nattribute vec3 aPosition;uniform mat4 uLightMatrix;void main(){gl_Position=uLightMatrix*vec4(aPosition,1.0);}',depthFragment:'#version 120\nuniform sampler2D uTexture;void main(){gl_FragColor=vec4(1.0);}'};
const sessions=[];
for(const [name,ownedState,benchmarkControl]of [['scoped',false,false],['owned',true,false],['mixed',false,true]]){const state=fakeGL(),backend=createBrowserWebGL(state.canvas(),()=>{},{ownedState,benchmarkControl}),session=await initializeSession(backend,shaders);sessions.push({...session,name,state});}
assert.equal(sessions[0].backend.queueOwnedState,undefined,'Ordinary scoped session exposes live control');assert.equal(sessions[1].backend.queueOwnedState,undefined,'Ordinary owned session exposes live control');
let scopedQueries=0,ownedQueries=0,comparedDraws=0,transitions=0;
for(const test of cases()){
 const snapshots=[];
 for(const session of sessions){const {state,backend}=session,was=backend.stats.ownedStateActive;state.draws.length=0;state.queries.length=0;const errors=state.errors,render=await prepareFrame(session,test);
  if(session.name==='mixed'){backend.queueOwnedState(!was);assert.equal(backend.stats.ownedStateActive,was,'Queued policy changed during uploads');}
  await render();if(test.world&&test.mode===1)assert.ok(backend.stats.worldReadiness.completeFrames>0,'Fixture advertised incomplete sources '+JSON.stringify({declaration:session.readinessDeclaration,sprites:backend.stats.worldSpriteGroups,readiness:backend.stats.worldReadiness}));assert.equal(backend.stats.presentedOwnedState,was,'A frame mixed ownership policies');snapshots.push(structuredClone(state.draws));
  if(session.name==='owned'){ownedQueries+=state.queries.length;assert.equal(state.queries.length,0,'Hot owned frame queried GL state: '+state.queries);}
  if(session.name==='scoped')scopedQueries+=state.queries.length;
  session.lastErrors=state.errors-errors;await finishFrame(session);
  if(session.name==='mixed'){assert.equal(backend.stats.ownedStateActive,!was,'Queue not committed at frameWork end');transitions++;}
  assert.equal(backend.stats.worldFrameSamples.length,backend.stats.worldFramePolicies.length);assert.equal(backend.stats.worldFrameSamples.length,backend.stats.worldFrameSpriteGroups.length);
 }
 for(let n=1;n<snapshots.length;n++)assert.deepEqual(snapshots[n],snapshots[0],sessions[n].name+' effective GL draw/clear/copy state differs: '+test.name);
 assert.equal(sessions[0].lastErrors,sessions[1].lastErrors,'Owned path removed GL error check');assert.equal(sessions[0].lastErrors,sessions[2].lastErrors,'Mixed path removed GL error check');comparedDraws+=snapshots[0].length;
}
assert.ok(scopedQueries>2000);assert.equal(ownedQueries,0);assert.ok(comparedDraws>200);assert.equal(transitions,cases().length);
for(const s of sessions){s.backend.dispose();s.backend.dispose();for(const [kind,set]of s.state.resources)assert.equal(set.size,0,s.name+' leaked '+kind);}
// Context loss and partial upload failure remain fail-closed in owned mode.
for(const kind of ['loss','upload']){const state=fakeGL(),canvas=state.canvas(),backend=createBrowserWebGL(canvas,()=>{},{ownedState:true,benchmarkControl:true});await initializeSession(backend,shaders);if(kind==='loss')canvas.lose();else{state.inject('texSubImage2D','error');await assert.rejects(()=>backend.natives.Java_orsc_BrowserWebGLBridge_uploadTexture(null,7,2,2,new Int32Array(4),true),/WebGL error/);}assert.throws(()=>backend.queueOwnedState(false),/unavailable/);backend.dispose();for(const [name,set]of state.resources)assert.equal(set.size,0,kind+' leaked '+name);}
// Failure after owned prepare, during shadow storage or final FULL capture: no lost ledgers.
for(const method of ['texImage2D','copyTexSubImage2D']){const state=fakeGL(),backend=createBrowserWebGL(state.canvas(),()=>{},{ownedState:true}),session=await initializeSession(backend,shaders),render=await prepareFrame(session,cases()[1]);state.inject(method,'error');await assert.rejects(render,/storage error|capture GL error/);backend.dispose();backend.dispose();for(const [kind,set]of state.resources)assert.equal(set.size,0,method+' leaked '+kind);assert.equal(backend.stats.gpuBytes,0);assert.equal(backend.stats.shadowResourceBytes,0);}
console.log(JSON.stringify({passed:true,scope:'CPU effective-state/draw/resource oracle only; no shader pixel/FPS proof',frames:cases().length,comparedDraws,scopedQueries,ownedQueries,transitions}));
