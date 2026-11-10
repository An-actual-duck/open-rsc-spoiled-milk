#!/usr/bin/env -S node --experimental-vm-modules
// Browser frontend API contract with mocked Java/WebGL transport only.
// No browser, hardware rendering, network or performance claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const benchmarkUi=process.argv.includes('--benchmark-ui-preupload'),benchmarkStream=process.argv.includes('--benchmark-stream')||benchmarkUi;let streamRequested,uiRequested;
let clock = 500, gpuDisposed = 0, transportDisposed = 0, middleDisposed = 0, middleCreated = 0, coverDisposed = 0, displayDisposed = 0, configured,gpuOptions;
const status = {textContent:''}, messages = [];
let layoutReads=0;
const target = {width:960,height:560,getBoundingClientRect:()=>{layoutReads++;return{left:20,top:40,width:960,height:560}}};
const display = {style:{},querySelectorAll:()=>[target,canvas],getBoundingClientRect:()=>({left:0,top:0}),append:()=>{canvas.parentElement=display;}};
const canvas = {style:{},setAttribute(){}};
const stats = {lastError:null,lastFrame:0};
const gateEvents=[];
const window = {dispatchEvent:e=>gateEvents.push(e.type),browserRuntimeVersion:'5.0',addEventListener(){}};
const context = vm.createContext({window,location:{search:benchmarkStream?'?mode=webgl&benchmark=1&cache=lru&underlay=initial&uploadvalidation=on'+(benchmarkUi?'&ui-preupload=on':''):'?mode=webgl',origin:'http://127.0.0.1'},parent:{postMessage:m=>messages.push(m)},
  URLSearchParams,performance:{now:()=>clock},console,
  document:{querySelector:s=>s==='#status'?status:display,createElement:s=>s==='canvas'?canvas:{},head:{append:s=>queueMicrotask(()=>s.onload())},addEventListener(){}},
  Event:class {constructor(type){this.type=type;}},queueMicrotask,setTimeout,WebSocket:class{},
  cheerpjInit:async o=>{configured=o;},cheerpjCreateDisplay(){},cheerpjRunMain:()=>new Promise(()=>{})});
let gateChanged;const cover={blocksWorldInput:false,attach(){},dispose(){coverDisposed++;this.blocksWorldInput=false;},update(state){const next=state?.phase==='loading';if(next!==this.blocksWorldInput){this.blocksWorldInput=next;gateChanged();}}};
const coverModule=new vm.SyntheticModule(['createWorldCover'],function(){this.setExport('createWorldCover',o=>{gateChanged=o.onGateChange;return cover;});},{context});
const transportModule = new vm.SyntheticModule(['createTransport'],function(){this.setExport('createTransport',()=>({dispose:()=>transportDisposed++}));},{context});
const middleStats = {presses:0,moves:0,releases:0,cancels:0,coalesced:0};
const middleNative = async()=>Int32Array.from([1,30,40,0]);
const middleModule = new vm.SyntheticModule(['createBrowserMiddleMouseInput'],function(){this.setExport('createBrowserMiddleMouseInput',()=>{
  middleCreated++;return {stats:middleStats,dispose:()=>middleDisposed++,natives:{Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent:middleNative}};
});},{context});
const displayModule=new vm.SyntheticModule(['createBrowserDisplay'],function(){this.setExport('createBrowserDisplay',()=>({stats:{},scale:1,setSize(){},attachDesktop(){},dispose(){displayDisposed++;}}));},{context});
const gpuModule = new vm.SyntheticModule(['createBrowserWebGL'],function(){this.setExport('createBrowserWebGL',(_canvas,_report,options)=>{gpuOptions=options;stats.ownedStateActive=options.ownedState;stats.streamUploadValidationActive=options.streamUploadValidation;stats.uiPreuploadActive=options.uiPreupload;return({canvas,stats,queueUiPreupload:value=>{uiRequested=value;},queueStreamUploadValidation:value=>{streamRequested=value;},queueOwnedState(){},dispose:()=>gpuDisposed++,natives:{
  Java_orsc_BrowserWebGLBridge_worldProgress:async()=>{stats.worldReadiness={phase:stats.declaredPhase||'loading'};},
  Java_orsc_BrowserWebGLBridge_present:async(lib,width,height)=>{canvas.width=width;canvas.height=height;stats.lastFrame=clock;stats.worldReadiness={phase:'ready'};},
}});});},{context});
const uiPreuploadModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-ui-preupload.js',import.meta.url),'utf8'),{context});
const controlsModule = new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-benchmark-controls.js',import.meta.url),'utf8'),{context});
const streamValidationModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-upload-validation.js',import.meta.url),'utf8'),{context});
const source = fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser.js',import.meta.url),'utf8');
const module = new vm.SourceTextModule(source,{context,importModuleDynamically:async specifier=>{assert.equal(specifier,'./webgl.js');await gpuModule.link(()=>{});await gpuModule.evaluate();return gpuModule;}});
await module.link(specifier=>{
  if(specifier==='./browser-world-cover.js')return coverModule;
  if(specifier==='./browser-display.js')return displayModule;
  if(specifier==='./browser-benchmark-controls.js')return controlsModule;
  if(specifier==='./browser-ui-preupload.js')return uiPreuploadModule;
  if(specifier==='./browser-upload-validation.js')return streamValidationModule;
  if(specifier==='./browser-middle-mouse.js')return middleModule;
  assert.equal(specifier,'./transport.js');return transportModule;
});
module.evaluate();
for(let i=0;i<30&&!configured;i++)await new Promise(resolve=>setImmediate(resolve));
assert.ok(configured,'actual frontend did not initialize Java options');
assert.equal(gpuOptions.ownedState,!benchmarkStream,'Ordinary frontend must pass the verified owned policy to the actual backend seam');
assert.equal(gpuOptions.benchmarkControl,benchmarkStream,'Ordinary frontend must not expose live benchmark control');
assert.equal(window.browserProof.ownedStateRequested,!benchmarkStream);assert.equal(stats.ownedStateActive,!benchmarkStream,'Actual selected backend policy receipt');
if(!benchmarkStream)assert.equal(window.browserBenchmarkOwnedState,undefined);
else assert.equal(typeof window.browserBenchmarkOwnedState,'function');
if(!benchmarkStream)assert.equal(configured.javaProperties.some(p=>p.startsWith('spoiledmilk.browserModelPickBounds=')||p.startsWith('spoiledmilk.presentationHaloMatchReuse=')),false,'Ordinary frontend must not override CPU benchmark experiment policies');
else {assert(configured.javaProperties.includes('spoiledmilk.browserModelPickBounds=false'));assert(configured.javaProperties.includes('spoiledmilk.presentationHaloMatchReuse=true'));}
assert.equal(middleCreated,1,'WebGL frontend creates one middle-input owner');
assert.equal(window.browserProof.middleInput,middleStats,'proof counters come from the real module seam');
assert.equal(configured.natives.Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent,middleNative,'Java input consumer is merged into CheerpJ natives');
assert.deepEqual(Array.from(await configured.natives.Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent(null)),[1,30,40,0]);
assert.equal(canvas.style.pointerEvents,'none');
assert.equal(configured.enableInputMethods,false);
assert.equal(gpuOptions.uiPreupload,benchmarkUi,'Unmeasured UI preupload must stay off except explicit private benchmark');assert.equal(window.browserProof.uiPreuploadRequested,benchmarkUi);assert.equal(stats.uiPreuploadActive,benchmarkUi);if(benchmarkStream){window.browserBenchmarkUiPreupload(!benchmarkUi);assert.equal(uiRequested,!benchmarkUi);}else assert.equal(window.browserBenchmarkUiPreupload,undefined);
assert.equal(gpuOptions.streamUploadValidation,true,'Ordinary WebGL adopts validated streaming policy');assert.equal(stats.streamUploadValidationActive,true,'Actual selected backend streaming receipt');
if(benchmarkStream){assert.equal(typeof window.browserBenchmarkStreamUploadValidation,'function');window.browserBenchmarkStreamUploadValidation(false);assert.equal(streamRequested,false);assert.equal(window.browserProof.benchmark.streamUploadValidationRequested,true);}else assert.equal(window.browserBenchmarkStreamUploadValidation,undefined,'ordinary sessions cannot change stream policy');
assert.equal(configured.javaProperties.some(p=>p.startsWith('spoiledmilk.browserFramePreparation=')||p.startsWith('spoiledmilk.browserAnimatedBufferReuse=')),benchmarkStream,'only private benchmark URL receives preparation overrides');
await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,2,0,0,0,1,960,540,0]);assert.equal(window.browserWorldInputAllowed(),false);
await configured.natives.Java_orsc_BrowserWebGLBridge_present(null,960,540);assert.equal(window.browserWorldInputAllowed(),true);assert.deepEqual(gateEvents,['browser-world-readinesschange','browser-world-readinesschange']);
assert.equal(canvas.style.left,'20px');assert.equal(canvas.style.top,'60px');
assert.equal(canvas.style.width,'960px');assert.equal(canvas.style.height,'540px');
assert.equal(canvas.style.display,'block');
const steadyMessages=messages.length,steadyLayouts=layoutReads;stats.declaredPhase='ready';for(let i=0;i<10;i++)await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,4,0,0,0,1,960,540,0]);assert.equal(messages.length,steadyMessages,'steady source frames copied whole proof to parent');assert.equal(layoutReads,steadyLayouts,'steady loading protocol forced extra canvas layout queries');
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportLoop(null,'modern');
assert.equal(window.browserProof.clientLoop,'modern');
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportPreparation(null,true,false,true);
assert.equal(window.browserProof.preparationControls.framePreparationEnabled,true);
assert.equal(window.browserProof.preparationControls.framePreparationActive,false);
assert.equal(window.browserProof.preparationControls.animatedBufferReuseEnabled,true);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportModelPickBounds(null,true,false);assert.equal(window.browserProof.modelPickBounds.requested,true);assert.equal(window.browserProof.modelPickBounds.enabled,false);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportTelemetry(null,'sceneRenderStats',[1,77]);
assert.equal(window.browserProof.profiled,true);assert.deepEqual(Array.from(window.browserProof.telemetrySamples[0].values),[1,77]);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportInputError(null,'controlled input relay error');
assert.equal(window.browserProof.inputError,'controlled input relay error');
assert.match(status.textContent,/controlled input relay error/);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'GAME',60,100);
assert.match(status.textContent,/controlled input relay error/,'loop/FPS success cannot conceal a stopped input relay');
await configured.natives.Java_orsc_browser_BrowserBootstrap_report(null,'ordinary successful update');
assert.match(status.textContent,/controlled input relay error/);
stats.lastError='controlled shader error';clock=2000;
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'GAME',60,100);
assert.match(status.textContent,/controlled shader error/);assert.match(status.textContent,/1500 ms ago/);
await configured.natives.Java_orsc_browser_BrowserBootstrap_report(null,'ordinary successful update');
assert.match(status.textContent,/controlled shader error/);
cover.blocksWorldInput=true;window.browserSessionDispose();window.browserSessionDispose();assert.equal(window.browserBenchmarkStreamUploadValidation,undefined,'Stop removes private streaming control');
assert.equal(coverDisposed,1,'Stop disposes the loading-cover owner exactly once before iframe removal');assert.equal(window.browserWorldInputAllowed(),true,'Stop releases the world input gate');
assert.equal(displayDisposed,1);assert.equal(gpuDisposed,1);assert.equal(transportDisposed,1);assert.equal(middleDisposed,1,'Stop disposes the middle-input owner exactly once');
assert.ok(messages.some(m=>m.proof?.gpu?.lastError==='controlled shader error'));
console.log('PASS mocked frontend contract: GPU/middle native merge, AWT alignment, pointer transparency, sticky input/GPU errors and idempotent owner disposal; no hardware proof');
