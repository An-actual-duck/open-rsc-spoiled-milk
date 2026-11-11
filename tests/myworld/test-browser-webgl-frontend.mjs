#!/usr/bin/env -S node --experimental-vm-modules
// Browser frontend API contract with mocked Java/WebGL transport only.
// No browser, hardware rendering, network or performance claim.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {execFileSync} from 'node:child_process';
const argument=name=>{const index=process.argv.indexOf(name);return index<0?null:process.argv[index+1];};
const nativeMode=process.argv.includes('--native-sync')?'sync':'async';
const nativeExplicit=process.argv.includes('--native-sync')||process.argv.includes('--native-async');
const nativeFault=process.argv.includes('--native-thenable');
const withoutNativeModule=process.argv.includes('--without-native-module');
const nativeSource=argument('--native-source'),nativeCommit=argument('--native-source-commit');
if(nativeExplicit)assert(nativeSource&&/^[a-f0-9]{40}$/.test(nativeCommit),'Explicit reviewed native helper source required');
const physicalOption=process.argv.includes('--physical-off')?'off':process.argv.includes('--physical')?'on':null;
const repeatable=process.argv.includes('--repeatable');
const combat=process.argv.includes('--combat');
const benchmarkKeys=process.argv.includes('--benchmark-key-limbs');
const benchmarkBatch=process.argv.includes('--model-batch');
const benchmarkDelta=process.argv.includes('--overlay-delta');
const benchmarkLoadProfile=process.argv.includes('--load-profile');
const benchmarkRenderWorker=process.argv.includes('--render-worker');
const withoutAwtTarget=process.argv.includes('--no-awt-target');let awtVisible=!withoutAwtTarget;
const benchmarkStream=benchmarkLoadProfile||benchmarkRenderWorker||process.argv.includes('--benchmark-stream')||benchmarkKeys||repeatable||combat||nativeExplicit||benchmarkBatch||benchmarkDelta;let streamRequested;
let clock = 500, gpuDisposed = 0, transportDisposed = 0, middleDisposed = 0, middleCreated = 0, coverDisposed = 0, displayDisposed = 0, configured,gpuOptions;
const status = {textContent:''}, messages = [];
let layoutReads=0;
const target = {width:960,height:560,getBoundingClientRect:()=>{layoutReads++;return{left:20,top:40,width:960,height:560}}};
const display = {style:{},querySelectorAll:()=>awtVisible?[target,canvas]:[canvas],getBoundingClientRect:()=>({left:0,top:0,width:1843.55,height:1037}),append:()=>{canvas.parentElement=display;}};
const canvas = {style:{},setAttribute(){}};
const stats = {lastError:null,lastFrame:0};
const gateEvents=[];
const window = {dispatchEvent:e=>gateEvents.push(e.type),browserRuntimeVersion:'5.0',addEventListener(){}};
const context = vm.createContext({window,location:{search:(benchmarkStream?'?mode=webgl&benchmark=1&cache=lru&underlay=initial&uploadvalidation=on'+(benchmarkKeys?'&spritekeylimbs=on':'')+(benchmarkBatch?'&modelbatch=on':'')+(benchmarkDelta?'&overlaydelta=on':'')+(benchmarkRenderWorker?'&renderworker=on&overlay=coverage':'')+(benchmarkLoadProfile?'&loadprofile=on':''):'?mode=webgl')+(combat?'&fixture=renderer-combat-v1':repeatable?'&fixture=repeatable-v1':'')+(physicalOption?'&physical='+physicalOption:'')+(nativeExplicit?'&nativecalls='+nativeMode:''),origin:'http://127.0.0.1'},parent:{postMessage:m=>messages.push(m)},
  URLSearchParams,performance:{now:()=>clock},console,
  document:{querySelector:s=>s==='#status'?status:display,createElement:s=>s==='canvas'?canvas:{},head:{append:s=>queueMicrotask(()=>s.onload())},addEventListener(){}},
  Event:class {constructor(type){this.type=type;}},queueMicrotask,setTimeout,WebSocket:class{},
  cheerpjInit:async o=>{configured=o;},cheerpjCreateDisplay(){},cheerpjRunMain:()=>new Promise(()=>{})});
let transportUrl;
let gateChanged;const cover={blocksWorldInput:false,attach(){},dispose(){coverDisposed++;this.blocksWorldInput=false;},update(state){const next=state?.phase==='loading';if(next!==this.blocksWorldInput){this.blocksWorldInput=next;gateChanged();}}};
const coverModule=new vm.SyntheticModule(['createWorldCover'],function(){this.setExport('createWorldCover',o=>{gateChanged=o.onGateChange;return cover;});},{context});
const transportModule = new vm.SyntheticModule(['createTransport'],function(){this.setExport('createTransport',(...args)=>{transportUrl=args[2];return {dispose:()=>transportDisposed++};});},{context});
const middleStats = {presses:0,moves:0,releases:0,cancels:0,coalesced:0};
const middleNative = async()=>Int32Array.from([1,30,40,0]);
const middleModule = new vm.SyntheticModule(['createBrowserMiddleMouseInput'],function(){this.setExport('createBrowserMiddleMouseInput',()=>{
  middleCreated++;return {stats:middleStats,dispose:()=>middleDisposed++,natives:{Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent:middleNative}};
});},{context});
const displayModule=new vm.SyntheticModule(['createBrowserDisplay'],function(){this.setExport('createBrowserDisplay',()=>({stats:{},scale:1,setSize(){},attachDesktop(){},dispose(){displayDisposed++;}}));},{context});
const nativeTrace=[];
function backend(name,body){
  const invoke=(...args)=>{nativeTrace.push(name);return body(...args);};
  return nativeMode==='sync'?invoke:async(...args)=>invoke(...args);
}
const gpuModule = new vm.SyntheticModule(['createBrowserWebGL','BROWSER_NATIVE_CALL_DISPATCH'],function(){
  this.setExport('BROWSER_NATIVE_CALL_DISPATCH',withoutNativeModule?undefined:nativeMode);
  this.setExport('createBrowserWebGL',(_canvas,_report,options)=>{
    gpuOptions=options;stats.ownedStateActive=options.ownedState;stats.streamUploadValidationActive=options.streamUploadValidation;
    if(!withoutNativeModule)stats.nativeCallDispatch={requested:options.nativeCallDispatch,active:nativeMode,synchronousMethods:nativeMode==='sync'?19:0,asynchronousMethods:nativeMode==='sync'?1:20};
    return {canvas,stats,invalidateLayout(){},queueStreamUploadValidation:value=>{streamRequested=value;},queueOwnedState(){},dispose:()=>gpuDisposed++,natives:{
      Java_orsc_BrowserWebGLBridge_regionTransition:backend('transition',(lib,soft)=>{stats.worldReadiness={phase:'streaming',blocksWorldInput:true,blocksKeyboard:!soft};return 3;}),
      Java_orsc_BrowserWebGLBridge_holdWorld:backend('hold',()=>4),
      Java_orsc_BrowserWebGLBridge_worldProgress:backend('progress',()=>{stats.worldReadiness={phase:stats.declaredPhase||'loading'};return 5;}),
      Java_orsc_BrowserWebGLBridge_present:backend('present',(lib,width,height)=>{if(nativeFault&&width===1)return Promise.resolve(6);awtVisible=true;canvas.width=width;canvas.height=height;stats.lastFrame=clock;stats.worldReadiness={phase:'ready'};return 6;}),
    }};
  });
},{context});
const renderWorkerModule=new vm.SyntheticModule(['createBrowserRenderWorker'],function(){this.setExport('createBrowserRenderWorker',async(...args)=>{const instance=gpuModule.namespace.createBrowserWebGL(...args),present=instance.natives.Java_orsc_BrowserWebGLBridge_present;instance.natives.Java_orsc_BrowserWebGLBridge_present=async(...input)=>{const result=await present(...input);args[2].onComplete?.({frameId:1});return result;};return instance;});},{context});
const nativeModule=nativeExplicit?new vm.SourceTextModule(execFileSync('git',['show',nativeCommit+':tools/browser-gameplay/web/browser-native-call-dispatch.js'],{cwd:nativeSource,encoding:'utf8'}),{context}):null;
const stutterModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-stutter-diagnostics.js',import.meta.url),'utf8'),{context});
const geometryModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-geometry-transfer-policy.js',import.meta.url),'utf8'),{context});
const preparationCandidatesModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-preparation-candidates.js',import.meta.url),'utf8'),{context});
const repeatableModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-repeatable-policy.js',import.meta.url),'utf8'),{context});
const loadProfileModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-region-load-profile.js',import.meta.url),'utf8'),{context});
const renderWorkerPolicyModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-render-worker-protocol.js',import.meta.url),'utf8'),{context});
const physicalOutputModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-physical-output.js',import.meta.url),'utf8'),{context});
const controlsModule = new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-benchmark-controls.js',import.meta.url),'utf8'),{context});
const streamValidationModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-upload-validation.js',import.meta.url),'utf8'),{context});
const spriteKeyModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-sprite-key-policy.js',import.meta.url),'utf8'),{context});
const modelBatchModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-batched-model-kernel.js',import.meta.url),'utf8'),{context});
const optimizedDefaultsModule=new vm.SourceTextModule(fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser-optimized-render-defaults.js',import.meta.url),'utf8'),{context});
const source = fs.readFileSync(new URL('../../tools/browser-gameplay/web/browser.js',import.meta.url),'utf8');
const module = new vm.SourceTextModule(source,{context,importModuleDynamically:async specifier=>{if(specifier==='./browser-render-worker.js'){if(renderWorkerModule.status==='unlinked')await renderWorkerModule.link(()=>{});if(renderWorkerModule.status==='linked')await renderWorkerModule.evaluate();return renderWorkerModule;}const selected=specifier==='./browser-native-call-dispatch.js'?nativeModule:gpuModule;if(selected===gpuModule)assert.equal(specifier,nativeMode==='sync'?'./webgl-sync.js':'./webgl.js');assert(selected,'Unexpected native helper import');if(selected.status==='unlinked')await selected.link(()=>{});if(selected.status==='linked')await selected.evaluate();return selected;}});
await module.link(specifier=>{
  if(specifier==='./browser-region-load-profile.js')return loadProfileModule;
  if(specifier==='./browser-render-worker-protocol.js')return renderWorkerPolicyModule;
  if(specifier==='./browser-optimized-render-defaults.js')return optimizedDefaultsModule;
  if(specifier==='./browser-batched-model-kernel.js')return modelBatchModule;
  if(specifier==='./browser-stutter-diagnostics.js')return stutterModule;
  if(specifier==='./browser-geometry-transfer-policy.js')return geometryModule;
  if(specifier==='./browser-preparation-candidates.js')return preparationCandidatesModule;
  if(specifier==='./browser-repeatable-policy.js')return repeatableModule;
  if(specifier==='./browser-world-cover.js')return coverModule;
  if(specifier==='./browser-display.js')return displayModule;
  if(specifier==='./browser-benchmark-controls.js')return controlsModule;
  if(specifier==='./browser-physical-output.js')return physicalOutputModule;
  if(specifier==='./browser-upload-validation.js')return streamValidationModule;
  if(specifier==='./browser-sprite-key-policy.js')return spriteKeyModule;
  if(specifier==='./browser-middle-mouse.js')return middleModule;
  assert.equal(specifier,'./transport.js');return transportModule;
});
module.evaluate();
for(let i=0;i<30&&!configured;i++)await new Promise(resolve=>setImmediate(resolve));
if(withoutNativeModule&&nativeMode==='sync'){assert.equal(configured,undefined);assert.equal(gpuOptions,undefined);assert.equal(gpuDisposed,0);assert.equal(transportDisposed,1);assert.equal(middleDisposed,1);assert.ok(messages.some(m=>m.type==='browser-session-stop'&&m.reason.includes('Private synchronous backend is unavailable')));console.log('PASS unavailableSYNC fails before backend/Java startup; no fallback or fake receipt');process.exit(0);}
assert.equal(transportUrl,combat?'ws://127.0.0.1:43539/':repeatable?'ws://127.0.0.1:43549/':'ws://127.0.0.1:43529/');
if(combat){const labels=['cameraPinned','npcCount','playerCount','welcomeVisible','activeProjectileActors','activeImpactActors','activeCombatEffectActors','pendingCombatEffectActors','activeClericBuffActors','activeClericCurseActors','activeHitSplatActors','damagedHealthActors','loadedCombatEffectKinds','loadedCombatEffectFrames','loadedProjectileKinds','loadedProjectileFrames','loadedProjectileImpactKinds','loadedProjectileImpactFrames','iceCrystalFrames','thunderStrikeFrames','batteringRamFrames','summonChargeFrames','summonCombatFrames','summonSupportFrames','summonUtilityFrames'];const values=labels.map((_,i)=>i);await configured.natives.Java_orsc_browser_BrowserBootstrap_reportCombatState(null,'renderdemo',labels.join('|'),values);assert.equal(window.browserProof.combatFixture.username,'renderdemo');assert.equal(window.browserProof.combatFixture.activeProjectileActors,4);assert.equal(window.browserProof.combatFixture.iceCrystalFrames,18);await assert.rejects(()=>configured.natives.Java_orsc_browser_BrowserBootstrap_reportCombatState(null,'renderdemo','wrong',[1]),/Unexpected/);}
if(repeatable){await configured.natives.Java_orsc_browser_BrowserBootstrap_reportRepeatableState(null,'browserdemo',[1,32,1,0]);assert.equal(window.browserProof.repeatableFixture.username,'browserdemo');assert.equal(window.browserProof.repeatableFixture.cameraPinned,true);assert.equal(window.browserProof.repeatableFixture.npcCount,32);assert.equal(window.browserProof.repeatableFixture.welcomeVisible,false);}
assert.ok(configured,'actual frontend did not initialize Java options');
assert.equal(window.browserProof.renderWorker.actual,benchmarkRenderWorker);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserLoadProfile=true'),benchmarkLoadProfile);
if(benchmarkLoadProfile){const load=new Float64Array(36);load[0]=1;load[1]=1;load[2]=1;load[3]=2;load[4]=960;load[5]=540;load[7]=8;load[8]=3;load[33]=8;configured.natives.Java_orsc_BrowserWebGLBridge_loadingWork(null,load);assert.equal(stats.loadProfile.last.path,2);assert.equal(window.browserProof.loadProfile.totalReports,1);}
const expectedPhysical=physicalOption?physicalOption==='on':!benchmarkStream;assert.equal(gpuOptions.physicalOutput,expectedPhysical,'Ordinary physical output defaults ON with explicit OFF; benchmark retains explicit opt-in');assert.equal(window.browserProof.physicalOutputRequested,expectedPhysical);
assert.equal(gpuOptions.nativeCallDispatch,nativeMode);assert.equal(window.browserProof.nativeCallDispatch.reported,stats.nativeCallDispatch??null);if(withoutNativeModule){assert.equal(window.browserProof.nativeCallDispatch.module,null);assert.equal(window.browserProof.nativeCallDispatch.reported,null);}
assert.equal(gpuOptions.ownedState,!benchmarkStream,'Ordinary frontend must pass the verified owned policy to the actual backend seam');
assert.equal(gpuOptions.benchmarkControl,benchmarkStream,'Ordinary frontend must not expose live benchmark control');
assert.equal(window.browserProof.ownedStateRequested,!benchmarkStream);assert.equal(stats.ownedStateActive,!benchmarkStream,'Actual selected backend policy receipt');
if(!benchmarkStream)assert.equal(window.browserBenchmarkOwnedState,undefined);
else assert.equal(typeof window.browserBenchmarkOwnedState,'function');
if(!benchmarkStream)assert.equal(configured.javaProperties.some(p=>p.startsWith('spoiledmilk.browserModelPickBounds=')||p.startsWith('spoiledmilk.presentationHaloMatchReuse=')),false,'Ordinary frontend must not override CPU benchmark experiment policies');
else {assert(configured.javaProperties.includes('spoiledmilk.browserModelPickBounds=false'));assert(configured.javaProperties.includes('spoiledmilk.presentationHaloMatchReuse=true'));}
assert.equal(typeof configured.natives.Java_orsc_graphics_three_BrowserBatchedModelKernel_transformPacket,'function');
for(const key of ['spoiledmilk.browserBatchedModelKernel','spoiledmilk.browserTerrainQueryIndex'])assert.equal(configured.javaProperties.filter(v=>v.startsWith(key+'=')).length,1);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserTerrainQueryIndex=true'),!benchmarkStream);
assert.equal(window.browserProof.batchedModelKernelWork.packets,0);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserBatchedModelKernel=false'),benchmarkStream&&!benchmarkBatch);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserBatchedModelKernel=true'),!benchmarkStream||benchmarkBatch);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserOverlayDeltaTransport=false'),benchmarkStream&&!benchmarkDelta);
assert.equal(configured.javaProperties.includes('spoiledmilk.browserOverlayDeltaTransport=true'),benchmarkDelta);
assert.equal(gpuOptions.overlayDeltaTransport,benchmarkDelta);
assert.equal(middleCreated,1,'WebGL frontend creates one middle-input owner');
assert.equal(window.browserProof.middleInput,middleStats,'proof counters come from the real module seam');
assert.equal(configured.natives.Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent,middleNative,'Java input consumer is merged into CheerpJ natives');
assert.deepEqual(Array.from(await configured.natives.Java_orsc_browser_BrowserBootstrap_awaitMiddleMouseEvent(null)),[1,30,40,0]);
assert.equal(canvas.style.pointerEvents,'none');
assert.equal(configured.enableInputMethods,false);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportRegionPreparationDiagnostics(null,true,[10,2,1672704]);assert.equal(window.browserProof.regionPreparationDiagnostics.hits,10);assert.equal(window.browserProof.regionPreparationDiagnostics.retainedTileBytes,1672704);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportRegionPreparationDiagnostics(null,false,[0,0,0,1,1,1,5,10]);
assert.deepEqual(JSON.parse(JSON.stringify(window.browserProof.terrainQueryIndex)),{present:true,eligible:true,enabled:true,dimension:5,protocol:10});
assert.equal(window.browserProof.regionPreparationDiagnostics.available,false);assert.equal(window.browserProof.regionPreparationDiagnostics.hits,null);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportRegionPreparationDiagnostics(null,true,[2,3,4,0,0,0,0,0]);assert.equal(window.browserProof.regionPreparationDiagnostics.hits,2);
for(const v of [[0,0,0,2,1,1,5,10],[0,0,0,1,1,1,4,10],[0,0,0,1,1,1,5,11]])await assert.rejects(()=>configured.natives.Java_orsc_browser_BrowserBootstrap_reportRegionPreparationDiagnostics(null,false,v),/Terrain query diagnostics/);
for(const name of ['geometryTransfer','regionPreparation','descriptorWriting','haloHeightLookup','sceneryProjection','chunkCoverage','batchedModelKernel','overlayDeltaTransport','terrainQueryIndex'])for(const [requested,available,enabled]of [[false,false,false],[true,true,false],[true,true,true]]){await configured.natives.Java_orsc_browser_BrowserBootstrap_reportCandidatePolicy(null,name,requested,available,enabled);assert.deepEqual(JSON.parse(JSON.stringify(window.browserProof.privatePolicies[name])),{requested,available,enabled});}
assert.equal(gpuOptions.streamUploadValidation,true,'Ordinary WebGL adopts validated streaming policy');assert.equal(stats.streamUploadValidationActive,true,'Actual selected backend streaming receipt');
if(benchmarkStream){assert.equal(typeof window.browserBenchmarkStreamUploadValidation,'function');window.browserBenchmarkStreamUploadValidation(false);assert.equal(streamRequested,false);assert.equal(window.browserProof.benchmark.streamUploadValidationRequested,true);}else assert.equal(window.browserBenchmarkStreamUploadValidation,undefined,'ordinary sessions cannot change stream policy');
assert.equal(configured.javaProperties.some(p=>p.startsWith('spoiledmilk.browserFramePreparation=')||p.startsWith('spoiledmilk.browserAnimatedBufferReuse=')),benchmarkStream,'only private benchmark URL receives preparation overrides');
assert.equal(configured.javaProperties.includes('spoiledmilk.browserSpriteKeyLimbs=true'),benchmarkKeys);
assert.equal(configured.javaProperties.some(p=>p.startsWith('spoiledmilk.browserSpriteKeyLimbs=')),benchmarkStream);
if(!benchmarkStream){
 await configured.natives.Java_orsc_browser_BrowserBootstrap_reportCandidatePolicy(null,'batchedModelKernel',true,true,true);
 await configured.natives.Java_orsc_browser_BrowserBootstrap_reportCandidatePolicy(null,'terrainQueryIndex',true,false,false);
 assert.deepEqual(JSON.parse(JSON.stringify(window.browserProof.optimizedRenderDefaults)),{batchedModelKernel:{requested:true,available:true,enabled:true},terrainQueryIndex:{requested:true,available:false,enabled:false}});
}
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportStartupDiagnostics(null,'route','43639');
assert.equal(window.browserProof.javaPrivateLoopbackPort,43639);
await configured.natives.Java_orsc_browser_BrowserBootstrap_reportStartupDiagnostics(null,'main-returned','loop=absent');
assert.equal(window.browserProof.startupDiagnostics.at(-1).detail,'loop=absent');
const selectedKeys=!benchmarkStream||benchmarkKeys;await configured.natives.Java_orsc_browser_BrowserBootstrap_reportSpriteKeyPolicy(null,selectedKeys,selectedKeys);assert.equal(window.browserProof.spriteKeyLimbs.enabled,selectedKeys);assert.equal(window.browserProof.spriteKeyLimbs.requested,selectedKeys);
const progressReturn=configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,2,0,0,0,1,960,540,0]);assert.equal(typeof progressReturn?.then,nativeMode==='sync'?'undefined':'function');await progressReturn;assert.equal(window.browserWorldInputAllowed(),false);
const presentReturn=configured.natives.Java_orsc_BrowserWebGLBridge_present(null,withoutAwtTarget?896:960,withoutAwtTarget?504:540);assert.equal(typeof presentReturn?.then,nativeMode==='sync'?'undefined':'function');assert.equal(await presentReturn,6);assert.equal(window.browserWorldInputAllowed(),true);assert.deepEqual(gateEvents,['browser-world-readinesschange','browser-world-readinesschange']);
assert.equal(canvas.style.left,withoutAwtTarget?'0px':'20px');assert.equal(canvas.style.top,withoutAwtTarget?'0px':'60px');
assert.equal(canvas.style.width,withoutAwtTarget?'896px':'960px');assert.equal(canvas.style.height,withoutAwtTarget?'504px':'540px');
assert.equal(canvas.style.display,'block');
assert.deepEqual(nativeTrace,['progress','present']);
for(const [name,value]of[['regionTransition',3],['holdWorld',4]]){const result=configured.natives['Java_orsc_BrowserWebGLBridge_'+name](null,true);assert.equal(typeof result?.then,nativeMode==='sync'?'undefined':'function');assert.equal(await result,value);}assert.deepEqual(nativeTrace,['progress','present','transition','hold']);stats.worldReadiness={phase:'ready'};
if(withoutAwtTarget){await configured.natives.Java_orsc_BrowserWebGLBridge_present(null,960,540);assert.equal(canvas.style.left,'20px');assert.equal(canvas.style.top,'60px');}
const steadyMessages=messages.length,steadyLayouts=layoutReads;stats.declaredPhase='ready';for(let i=0;i<10;i++)await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,4,0,0,0,1,960,540,0]);assert.equal(messages.length,steadyMessages,'steady source frames copied whole proof to parent');assert.equal(layoutReads,steadyLayouts,'steady loading protocol forced extra canvas layout queries');
if(benchmarkRenderWorker){
 stats.worldReadiness={phase:'login'};await configured.natives.Java_orsc_browser_BrowserBootstrap_reportState(null,'GAME',60,100);assert.equal(window.browserWorldInputAllowed(),false);
 stats.declaredPhase='login';await configured.natives.Java_orsc_BrowserWebGLBridge_worldProgress(null,[1,2,1,4,4,0,0,0,1,960,540,0]);assert.equal(window.browserWorldInputAllowed(),false,'queued declaration cannot release initial gate');
 stats.worldReadiness={phase:'loading'};gpuOptions.onComplete({frameId:3});assert.equal(window.browserWorldInputAllowed(),false,'held/loading ACK cannot release initial gate');
 stats.worldReadiness={phase:'ready'};stats.javaWorldFrames=0;gpuOptions.onComplete({frameId:4});assert.equal(window.browserWorldInputAllowed(),false,'LOGIN image ACK with no actual WORLD cannot release gate');
 stats.javaWorldFrames=1;gpuOptions.onComplete({frameId:5});assert.equal(window.browserWorldInputAllowed(),true,'actual WORLD-ready completion releases initial gate');stats.declaredPhase='ready';
}
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
if(nativeFault){stats.lastError=null;assert.equal(nativeMode,'sync');assert.throws(()=>configured.natives.Java_orsc_BrowserWebGLBridge_present(null,1,1),/Unexpected asynchronous result/);assert.equal(gpuDisposed,1);assert.equal(transportDisposed,1);assert.ok(messages.some(m=>m.type==='browser-session-stop'&&m.reason.includes('Synchronous frontend native stopped')));}
if(!nativeFault)cover.blocksWorldInput=true;window.browserSessionDispose();window.browserSessionDispose();assert.equal(window.browserBenchmarkStreamUploadValidation,undefined,'Stop removes private streaming control');
assert.equal(window.browserBenchmarkDiagnosticsReset,undefined);
assert.equal(coverDisposed,1,'Stop disposes the loading-cover owner exactly once before iframe removal');assert.equal(window.browserWorldInputAllowed(),true,'Stop releases the world input gate');
assert.equal(displayDisposed,1);assert.equal(gpuDisposed,1);assert.equal(transportDisposed,1);assert.equal(middleDisposed,1,'Stop disposes the middle-input owner exactly once');
if(!nativeFault)assert.ok(messages.some(m=>m.proof?.gpu?.lastError==='controlled shader error'));
if(argument('--trace-output'))fs.writeFileSync(argument('--trace-output'),JSON.stringify({style:canvas.style,nativeTrace,gateEvents,disposal:[gpuDisposed,transportDisposed,middleDisposed,coverDisposed,displayDisposed],status:status.textContent,messages:messages.map(m=>({type:m.type,status:m.status,reason:m.reason}))},null,2));
console.log('PASS mocked frontend contract: GPU/middle native merge, AWT alignment, pointer transparency, sticky input/GPU errors and idempotent owner disposal; no hardware proof');
