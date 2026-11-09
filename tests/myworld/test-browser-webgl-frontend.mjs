#!/usr/bin/env -S node --experimental-vm-modules
// Browser frontend API contract with mocked Java/WebGL transport only.
// No browser, hardware rendering, network or performance claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
let clock = 500, gpuDisposed = 0, transportDisposed = 0, configured;
const status = {textContent:''}, messages = [];
let layoutReads=0;
const target = {width:960,height:560,getBoundingClientRect:()=>{layoutReads++;return{left:20,top:40,width:960,height:560}}};
const display = {style:{},querySelectorAll:()=>[target,canvas],getBoundingClientRect:()=>({left:0,top:0}),append:()=>{canvas.parentElement=display;}};
const canvas = {style:{},setAttribute(){}};
const stats = {lastError:null,lastFrame:0};
const gateEvents=[];
const window = {dispatchEvent:e=>gateEvents.push(e.type),browserRuntimeVersion:'5.0',addEventListener(){}};
const context = vm.createContext({window,location:{search:'?mode=webgl',origin:'http://127.0.0.1'},parent:{postMessage:m=>messages.push(m)},
  URLSearchParams,performance:{now:()=>clock},console,
  document:{querySelector:s=>s==='#status'?status:display,createElement:s=>s==='canvas'?canvas:{},head:{append:s=>queueMicrotask(()=>s.onload())},addEventListener(){}},
  Event:class {constructor(type){this.type=type;}},queueMicrotask,setTimeout,WebSocket:class{},
  cheerpjInit:async o=>{configured=o;},cheerpjCreateDisplay(){},cheerpjRunMain:()=>new Promise(()=>{})});
let gateChanged;const cover={blocksWorldInput:false,attach(){},dispose(){},update(state){const next=state?.phase==='loading';if(next!==this.blocksWorldInput){this.blocksWorldInput=next;gateChanged();}}};
const coverModule=new vm.SyntheticModule(['createWorldCover'],function(){this.setExport('createWorldCover',o=>{gateChanged=o.onGateChange;return cover;});},{context});
const transportModule = new vm.SyntheticModule(['createTransport'],function(){this.setExport('createTransport',()=>({dispose:()=>transportDisposed++}));},{context});
const gpuModule = new vm.SyntheticModule(['createBrowserWebGL'],function(){this.setExport('createBrowserWebGL',()=>({canvas,stats,dispose:()=>gpuDisposed++,natives:{
  Java_orsc_BrowserWebGLBridge_worldProgress:async()=>{stats.worldReadiness={phase:stats.declaredPhase||'loading'};},
  Java_orsc_BrowserWebGLBridge_present:async(lib,width,height)=>{canvas.width=width;canvas.height=height;stats.lastFrame=clock;stats.worldReadiness={phase:'ready'};},
}}));},{context});
const source = fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser.js',import.meta.url),'utf8');
const module = new vm.SourceTextModule(source,{context,importModuleDynamically:async specifier=>{assert.equal(specifier,'./webgl.js');await gpuModule.link(()=>{});await gpuModule.evaluate();return gpuModule;}});
await module.link(specifier=>{if(specifier==='./browser-world-cover.js')return coverModule;assert.equal(specifier,'./transport.js');return transportModule;});
module.evaluate();
for(let i=0;i<30&&!configured;i++)await new Promise(resolve=>setImmediate(resolve));
assert.ok(configured,'actual frontend did not initialize Java options');
assert.equal(canvas.style.pointerEvents,'none');
assert.equal(configured.enableInputMethods,false);
await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,2,0,0,0,1,960,540,0]);assert.equal(window.browserWorldInputAllowed(),false);
await configured.natives.Java_orsc_BrowserWebGLBridge_present(null,960,540);assert.equal(window.browserWorldInputAllowed(),true);assert.deepEqual(gateEvents,['browser-world-readinesschange','browser-world-readinesschange']);
assert.equal(canvas.style.left,'20px');assert.equal(canvas.style.top,'60px');
assert.equal(canvas.style.width,'960px');assert.equal(canvas.style.height,'540px');
assert.equal(canvas.style.display,'block');
const steadyMessages=messages.length,steadyLayouts=layoutReads;stats.declaredPhase='ready';for(let i=0;i<10;i++)await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,4,0,0,0,1,960,540,0]);assert.equal(messages.length,steadyMessages,'steady source frames copied whole proof to parent');assert.equal(layoutReads,steadyLayouts,'steady loading protocol forced extra canvas layout queries');
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportLoop(null,'modern');
assert.equal(window.browserProof.clientLoop,'modern');
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportTelemetry(null,'sceneRenderStats',[1,77]);
assert.equal(window.browserProof.profiled,true);assert.deepEqual(Array.from(window.browserProof.telemetrySamples[0].values),[1,77]);
stats.lastError='controlled shader error';clock=2000;
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'GAME',60,100);
assert.match(status.textContent,/controlled shader error/);assert.match(status.textContent,/1500 ms ago/);
await configured.natives.Java_orsc_browser_BrowserBootstrap_report(null,'ordinary successful update');
assert.match(status.textContent,/controlled shader error/);
window.browserSessionDispose();window.browserSessionDispose();
assert.equal(gpuDisposed,1);assert.equal(transportDisposed,1);
assert.ok(messages.some(m=>m.proof?.gpu?.lastError==='controlled shader error'));
console.log('PASS mocked frontend contract: native merge, AWT alignment, pointer transparency, sticky GPU errors/age and idempotent Stop disposal; no hardware proof');
