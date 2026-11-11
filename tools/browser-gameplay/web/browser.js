import {optimizedRenderDefaults,canonicalRenderProperties} from './browser-optimized-render-defaults.js';
import {createBatchedModelKernel} from './browser-batched-model-kernel.js';
import {browserGeometryTransferPolicy} from './browser-geometry-transfer-policy.js';
import {privatePreparationCandidates} from './browser-preparation-candidates.js';
import {createStutterDiagnostics,stutterDiagnosticsPolicy} from './browser-stutter-diagnostics.js';
import {repeatableFixturePolicy} from './browser-repeatable-policy.js';
import {physicalOutputRequested} from './browser-physical-output.js';
import {browserSpriteKeyBenchmarkControls} from './browser-sprite-key-policy.js';
import {browserUploadValidationBenchmarkPolicy} from './browser-upload-validation.js';
const started = performance.now();
import {createTransport} from './transport.js';
import {createWorldCover} from './browser-world-cover.js';
import {createBrowserMiddleMouseInput} from './browser-middle-mouse.js';
import {createBrowserDisplay} from './browser-display.js';
import {browserPreparationBenchmarkControls,browserOwnedStateBenchmarkPolicy,browserModelBenchmarkControls} from './browser-benchmark-controls.js';
const status = document.querySelector('#status');
const options = new URLSearchParams(location.search);
function nativeCallPolicy(mode,parameters){
  if(mode!=='webgl'||!parameters.has('benchmark'))return 'async';
  const value=parameters.get('nativecalls')||'async';
  if(value!=='async'&&value!=='sync')throw new Error('Invalid private nativecalls policy');
  return value;
}
const nativeCallDispatch=nativeCallPolicy(options.get('mode')||'software',options);
const javaVersion = Number(options.get('java') || '8');
if (![8, 11, 17].includes(javaVersion)) throw new Error('Private probe supports Java 8, 11 or 17');
window.browserProof = {events: [], started, renderer: 'AWT software / direct framebuffer', mode: options.get('mode') || 'software', javaVersion, wasmGC: options.has('wasmgc'), runtimeVersion: window.browserRuntimeVersion};
let stopped = false;
let preparationControls = {properties: [], requested: null};
let spriteKeyControls = {properties: [], requested: null};
let modelPreparationControls = {properties: [], requested: null};
let gpu,worldCover,awaitingGameFrame=false,displayLayout,stutterDiagnostics;
window.browserWorldInputAllowed=()=>!worldCover?.blocksWorldInput;
function updateWorldCover(){const state=gpu?.stats.worldReadiness,error=gpu?.stats.lastError||(gpu?.stats.contextLost?'Graphics context lost. Stop and restart to retry.':null),waiting=awaitingGameFrame&&state?.phase==='login';if(!worldCover||(!waiting&&!error&&!['loading','streaming','error'].includes(state?.phase)&&!worldCover.blocksWorldInput))return;worldCover.update(waiting?{phase:'loading',reason:'Waiting for area data'}:state,gpu?.canvas.style,error);}
function setStatus(message) {
  if (gpu?.stats.lastError) {
    const age = gpu.stats.lastFrame > 0 ? Math.max(0, Math.round(performance.now() - gpu.stats.lastFrame)) + ' ms ago' : 'none';
    status.textContent = `Browser GPU error: ${gpu.stats.lastError} · last submitted frame: ${age}`;
  } else if(gpu?.stats.worldReadiness?.phase==='error')status.textContent=gpu.stats.worldReadiness.reason;
  else if (window.browserProof.inputError) status.textContent = `Browser input error: ${window.browserProof.inputError}`;
  else status.textContent = message;
}
function publish() {
  updateWorldCover();
  if (gpu) window.browserProof.gpu = gpu.stats;
  if (!stopped) parent.postMessage({type: 'browser-session-proof', proof: window.browserProof, status: status.textContent}, location.origin);
}
function dispose() {
  if (stopped) return;
  stopped = true; delete window.browserBenchmarkOwnedState; delete window.browserBenchmarkStreamUploadValidation; delete window.browserBenchmarkPhysicalOutput;delete window.browserBenchmarkDiagnosticsReset;stutterDiagnostics?.dispose(); pointer?.dispose(); displayLayout?.dispose(); worldCover?.dispose(); transport.dispose(); gpu?.dispose();
}
function finish(reason) {
  if (stopped) return;
  publish(); dispose();
  document.activeElement?.blur?.();
  parent.postMessage({type: 'browser-session-stop', reason: gpu?.stats.lastError ? status.textContent : reason}, location.origin);
}
// The outer Stop control disposes app-owned transport/GPU resources before
// discarding this document. Page exit follows the same idempotent path.
window.browserSessionDispose = dispose;
window.addEventListener('blur', () => setTimeout(() => {
  if (!document.hasFocus() && !stopped) parent.postMessage({type: 'browser-session-focus-left'}, location.origin);
}, 0));
document.addEventListener('visibilitychange', () => {
  window.browserProof.visibility = document.visibilityState;
  publish();
});
window.addEventListener('pagehide', dispose);
// Keyboard events, including Escape, remain ordinary game/AWT input. The
// wrapper does not turn closing a menu into closing a Java session.
function report(message) {
  const event = {milliseconds: Math.round(performance.now() - started), message};
  window.browserProof.events.push(event); setStatus(message); console.log('BROWSER_PROOF', JSON.stringify(event)); publish();
}
const geometryTransfer=browserGeometryTransferPolicy(window.browserProof.mode,options);
const candidatePreparation=privatePreparationCandidates(window.browserProof.mode,options);
const optimizedDefaults=optimizedRenderDefaults(window.browserProof.mode,options);
const modelBatchKernel=createBatchedModelKernel();
window.browserProof.batchedModelKernelWork=modelBatchKernel.stats;
const fixturePolicy=repeatableFixturePolicy(window.browserProof.mode,options,window.browserPrivateTestRoute,location.origin);
if(window.browserPrivateTestRoute!==undefined)window.browserProof.privateLoopbackRoute={origin:location.origin,websocket:fixturePolicy.websocket,tcpPort:fixturePolicy.tcpPort};
window.browserProof.repeatableFixtureRequested=fixturePolicy.requested;
const transport = createTransport(WebSocket, report,fixturePolicy.websocket);
const pointer = window.browserProof.mode === 'webgl' ? createBrowserMiddleMouseInput(window,document,{logicalExtent:()=>({width:gpu?.stats.physicalOutput?.logicalWidth||gpu?.stats.display?.internalWidth||960,height:gpu?.stats.physicalOutput?.logicalHeight||gpu?.stats.display?.internalHeight||540})}) : undefined;
if (pointer) window.browserProof.middleInput = pointer.stats;
window.browserTransport = transport;
const natives = {
  Java_orsc_graphics_three_BrowserBatchedModelKernel_transformPacket: modelBatchKernel.native,
  Java_orsc_browser_BrowserBootstrap_reportBatchedModelWork(lib,counters){
    const v=Array.from(counters);
    if(v.length!==14||v.some(x=>!Number.isSafeInteger(x)||x<0))throw new Error('Invalid batched model work receipt');
    window.browserProof.batchedModelPreparationWork=Object.fromEntries(['frames','groups','nativePackets','nativeModels','nativeVertices','metadataModels','literalModels','rejectedPackets','aliasBarriers','dirtyBarriers','queuedModels','maxGroup','arrayReferencesQueued','roleMapOperations'].map((name,n)=>[name,v[n]]));
  },
  async Java_orsc_browser_BrowserBootstrap_reportRegionPreparationDiagnostics(lib,available,counters){
    if(counters.length!==8&&((available&&counters.length!==3)||(!available&&counters.length!==0)))throw new Error('Region diagnostics layout changed');
    const values=Array.from(counters);if(values.some(value=>!Number.isSafeInteger(value)||value<0))throw new Error('Region diagnostics numeric range changed');
    window.browserProof.regionPreparationDiagnostics={available:available===true,hits:available?values[0]:null,builds:available?values[1]:null,retainedTileBytes:available?values[2]:null};
    if(values.length===8){
      if(values.slice(3,6).some(v=>v>1)||![0,3,5].includes(values[6])||![0,3,4,5,6,7,8,9,10].includes(values[7]))throw new Error('Terrain query diagnostics layout changed');
      window.browserProof.terrainQueryIndex={present:values[3]===1,eligible:values[4]===1,enabled:values[5]===1,dimension:values[6],protocol:values[7]};
    }
  },
  async Java_orsc_browser_BrowserBootstrap_reportCandidatePolicy(lib,name,requested,available,enabled){
    if(!['geometryTransfer','regionPreparation','descriptorWriting','haloHeightLookup','sceneryProjection','chunkCoverage','batchedModelKernel','overlayDeltaTransport','terrainQueryIndex'].includes(name))throw new Error('Unknown private policy receipt');
    (window.browserProof.privatePolicies||={})[name]={requested:requested===true,available:available===true,enabled:enabled===true};
    if(!options.has('benchmark')&&['batchedModelKernel','terrainQueryIndex'].includes(name))(window.browserProof.optimizedRenderDefaults||={})[name]={requested:requested===true,available:available===true,enabled:enabled===true};
  },
  async Java_orsc_browser_BrowserBootstrap_reportCombatState(lib,username,names,counters){
    const labels=String(names).split('|'),values=Array.from(counters);
    if(fixturePolicy.requested!=='renderer-combat-v1'||labels.length!==25||values.length!==labels.length
      ||new Set(labels).size!==labels.length||labels.some(name=>!/^\w+$/.test(name))
      ||values.some(value=>!Number.isSafeInteger(value)||value<0))throw new Error('Unexpected private combat fixture receipt');
    window.browserProof.combatFixture={username:String(username).slice(0,40),scope:'Received client state and loaded frames; not proof of visible pixels or spell damage',...Object.fromEntries(labels.map((name,index)=>[name,values[index]]))};
  },
  async Java_orsc_browser_BrowserBootstrap_reportRepeatableState(lib,username,counters){
    if(fixturePolicy.requested!=='repeatable-v1'||counters.length!==4)throw new Error('Unexpected repeatable fixture receipt');
    window.browserProof.repeatableFixture={username:String(username).slice(0,40),cameraPinned:counters[0]===1,npcCount:counters[1],playerCount:counters[2],welcomeVisible:counters[3]===1};
  },
  async Java_orsc_browser_BrowserBootstrap_reportInputError(lib, message) {
    window.browserProof.inputError=String(message).slice(0,512);setStatus(message);publish();
  },
  async Java_orsc_browser_BrowserBootstrap_report(lib, message) { report(message); },
  async Java_orsc_browser_BrowserBootstrap_reportTelemetry(lib,names,values) {
    const labels=names.split('|'),sample=Array.from(values);
    if(labels.length>32||sample.length!==labels.length*2||sample.some(v=>!Number.isFinite(v)||v<0))throw new Error('Invalid bounded browser telemetry export');
    window.browserProof.profiled=true;window.browserProof.profileKind="counters-only";window.browserProof.telemetryNames=labels;
    const samples=window.browserProof.telemetrySamples||=[];samples.push({milliseconds:performance.now(),values:sample});if(samples.length>300)samples.shift();
  },
  async Java_orsc_browser_BrowserBootstrap_reportLoop(lib,mode,early,full) { window.browserProof.clientLoop=mode;window.browserProof.earlyFaceCull=early===true;window.browserProof.fullResidentOwnership=full===true; },
  async Java_orsc_browser_BrowserBootstrap_reportOverlayRectPolicy(lib,requested,enabled){
    if(!['off','observe','on','packed'].includes(requested)||!['off','observe','on','packed'].includes(enabled))throw new Error('Invalid overlay rectangle policy receipt');
    window.browserProof.overlayRectMode={requested,enabled};
  },
  async Java_orsc_browser_BrowserBootstrap_reportSpriteKeyPolicy(lib,requested,enabled){window.browserProof.spriteKeyLimbs={requested:!!requested,enabled:!!enabled};publish();},
  async Java_orsc_browser_BrowserBootstrap_reportPreparation(lib, enabled, active, animated) {
    window.browserProof.preparationControls = {framePreparationEnabled: enabled === true,
      framePreparationActive: active === true, animatedBufferReuseEnabled: animated === true};
  },
  async Java_orsc_browser_BrowserBootstrap_reportModelPickBounds(lib, requested, enabled) {
    window.browserProof.modelPickBounds = {requested:requested === true, enabled:enabled === true};
  },
  Java_orsc_browser_BrowserBootstrap_reportStartupDiagnostics(lib,phase,detail){
    if(typeof phase!=='string'||phase.length>32||typeof detail!=='string'||detail.length>8192)throw new Error('Invalid bounded startup diagnostic');
    const rows=window.browserProof.startupDiagnostics||=[];if(rows.length<16)rows.push({milliseconds:Math.round(performance.now()-started),phase,detail});
    if(phase==='route'&&['43629','43639','43649'].includes(detail))window.browserProof.javaPrivateLoopbackPort=Number(detail);
    publish();
  },
  async Java_orsc_browser_BrowserBootstrap_reportState(lib, mode, fps, loadingPercent) {
    if(mode==='GAME'&&window.browserProof.state?.mode!=='GAME')awaitingGameFrame=true;else if(mode!=='GAME')awaitingGameFrame=false;
    const state = {milliseconds: Math.round(performance.now() - started), mode, fps, loadingPercent};
    if (window.browserProof.state?.mode !== mode) console.log('BROWSER_PROOF_STATE', JSON.stringify(state));
    window.browserProof.state = state;
    (window.browserProof.samples ||= []).push(state);
    if (window.browserProof.samples.length > 300) window.browserProof.samples.shift();
    setStatus(`${window.browserProof.mode === 'webgl' ? 'Experimental WebGL client' : 'Software client'} · ${mode} · ${fps} reported Java loop frames/s · private loopback world`);
    publish();
  },
  async Java_orsc_browser_BrowserBootstrap_wsOpen() { return transport.open(); },
  async Java_orsc_browser_BrowserBootstrap_wsAvailable(lib, id) { return transport.available(id); },
  async Java_orsc_browser_BrowserBootstrap_wsRead(lib, id, maximum) { return transport.read(id, maximum); },
  async Java_orsc_browser_BrowserBootstrap_wsWrite(lib, id, bytes) { transport.write(id, bytes); },
  async Java_orsc_browser_BrowserBootstrap_wsClose(lib, id) { transport.close(id); },
};
if (pointer) Object.assign(natives, pointer.natives);
try {
  stutterDiagnostics=createStutterDiagnostics(window,{enabled:stutterDiagnosticsPolicy(window.browserProof.mode,options)});
  window.browserProof.stutterDiagnostics=stutterDiagnostics.stats;
  if(options.has('benchmark'))window.browserBenchmarkDiagnosticsReset=()=>stutterDiagnostics.reset();
  preparationControls = browserPreparationBenchmarkControls(window.browserProof.mode, options);
  spriteKeyControls = browserSpriteKeyBenchmarkControls(window.browserProof.mode,options);
  modelPreparationControls = browserModelBenchmarkControls(window.browserProof.mode, options);
  if (window.browserProof.mode==='webgl' && options.has('benchmark')) {
    if(!['active','lru'].includes(options.get('cache')) || !['always','initial'].includes(options.get('underlay')) || !['cpu','gpu'].includes(options.get('depth')||'cpu') || !['legacy','modern'].includes(options.get('loop')||'legacy') || !['late','early'].includes(options.get('cull')||'late') || !['objects','all'].includes(options.get('ownership')||'objects') || !['uncached','cached'].includes(options.get('sprites')||'uncached') || !['marker','coverage'].includes(options.get('overlay')||'marker'))throw new Error('Private benchmark requires explicit cache and underlay policies');
    window.browserProof.benchmark={cache:options.get('cache'),underlay:options.get('underlay'),depth:options.get('depth')||'cpu',loop:options.get('loop')||'legacy',cull:options.get('cull')||'late',ownership:options.get('ownership')||'objects',sprites:options.get('sprites')||'uncached',overlay:options.get('overlay')||'marker',preparationRequested:preparationControls.requested,modelPreparationRequested:modelPreparationControls.requested,tone:'day',sun:'135/45',brightness:'high',zoom:75,rotation:128};
  }
  if (window.browserProof.mode === 'webgl') {
    const backendModule=await import(nativeCallDispatch==='sync'?'./webgl-sync.js':'./webgl.js');
    const {createBrowserWebGL}=backendModule;
    if(nativeCallDispatch==='sync'&&backendModule.BROWSER_NATIVE_CALL_DISPATCH!=='sync')throw new Error('Private synchronous backend is unavailable');
    const synchronousNative=nativeCallDispatch==='sync'?(await import('./browser-native-call-dispatch.js')).createSynchronousNativeCall:null;
    const synchronousFailure=error=>finish('Synchronous frontend native stopped: '+error);

    const canvas = document.createElement('canvas'); canvas.id = 'browser-webgl';
    canvas.setAttribute('aria-hidden', 'true');
    Object.assign(canvas.style, {position: 'absolute', pointerEvents: 'none', display: 'none', zIndex: '1'});
    const ownedState=browserOwnedStateBenchmarkPolicy(window.browserProof.mode,options);
    const streamUploadValidation=browserUploadValidationBenchmarkPolicy(window.browserProof.mode,options);
    const physicalOutput=physicalOutputRequested(window.browserProof.mode,options);
    window.browserProof.physicalOutputRequested=physicalOutput;
    window.browserProof.ownedStateRequested=ownedState;
    if(window.browserProof.benchmark)window.browserProof.benchmark.streamUploadValidationRequested=streamUploadValidation;
    gpu = createBrowserWebGL(canvas, report, {nativeCallDispatch,opaqueProof:options.get('alpha-proof')==='1',ownedState,streamUploadValidation,overlayDeltaTransport:candidatePreparation.requested?.overlayDeltaTransport===true,overlayJournalObserve:candidatePreparation.journalObserve===true,overlayRectMode:candidatePreparation.rectMode||'off',physicalOutput,physicalOutputControl:options.has('benchmark'),benchmarkControl:options.has('benchmark')});
    window.browserProof.nativeCallDispatch={requested:nativeCallDispatch,module:backendModule.BROWSER_NATIVE_CALL_DISPATCH||null,reported:gpu.stats.nativeCallDispatch||null};
    if(options.has('benchmark')){window.browserBenchmarkOwnedState=enabled=>gpu.queueOwnedState(enabled);window.browserBenchmarkStreamUploadValidation=enabled=>gpu.queueStreamUploadValidation(enabled);window.browserBenchmarkPhysicalOutput=enabled=>gpu.queuePhysicalOutput(enabled);}
    worldCover=createWorldCover({document,window,display:document.querySelector('#display'),onGateChange:()=>window.dispatchEvent(new Event('browser-world-readinesschange'))});
    window.browserProof.worldLoading=worldCover.stats;
    window.browserProof.renderer = 'WebGL2 resident world and sprites / software UI (experimental)';
    Object.assign(natives, gpu.natives);
    function alignWorldSurface(width,height){
      const display=document.querySelector('#display'),target=[...display.querySelectorAll('canvas')].filter(c=>c!==canvas).sort((a,b)=>b.width*b.height-a.width*a.height)[0];
      displayLayout?.setSize(width,height);
      if(target&&canvas.parentElement){const box=target.getBoundingClientRect(),parentBox=display.getBoundingClientRect(),scale=displayLayout?.scale||1,sx=box.width/target.width/scale,sy=box.height/target.height/scale;Object.assign(canvas.style,{display:'block',left:((box.left-parentBox.left)/scale+(target.width-width)*sx/2)+'px',top:((box.top-parentBox.top)/scale+(target.height-height)*sy)+'px',width:width*sx+'px',height:height*sy+'px'});}
      updateWorldCover();
    }
    const progressName='Java_orsc_BrowserWebGLBridge_worldProgress',progress=natives[progressName];
    if(typeof progress!=='function')throw new Error('WebGL bridge has no loading protocol');
    if(nativeCallDispatch==='sync') {
      natives[progressName]=synchronousNative((lib,metadata)=>{const blocked=worldCover.blocksWorldInput;const result=progress(lib,metadata);awaitingGameFrame=false;if(gpu.stats.worldReadiness?.blocksWorldInput||gpu.stats.worldReadiness?.phase==='loading'||Number(metadata[9])!==gpu.stats.display?.internalWidth||Number(metadata[10])!==gpu.stats.display?.internalHeight)alignWorldSurface(Number(metadata[9]),Number(metadata[10]));else updateWorldCover();if(blocked!==worldCover.blocksWorldInput)publish();return result;},{name:'frontend worldProgress',failure:synchronousFailure});
    } else {
    natives[progressName]=async(lib,metadata)=>{const blocked=worldCover.blocksWorldInput;await progress(lib,metadata);awaitingGameFrame=false;if(gpu.stats.worldReadiness?.blocksWorldInput||gpu.stats.worldReadiness?.phase==='loading'||Number(metadata[9])!==gpu.stats.display?.internalWidth||Number(metadata[10])!==gpu.stats.display?.internalHeight)alignWorldSurface(Number(metadata[9]),Number(metadata[10]));else updateWorldCover();if(blocked!==worldCover.blocksWorldInput)publish();};
    }
    for(const name of ['Java_orsc_BrowserWebGLBridge_regionTransition','Java_orsc_BrowserWebGLBridge_holdWorld']) {
      const callback=natives[name];if(typeof callback!=='function')throw new Error('WebGL bridge has no streaming protocol');
      if(nativeCallDispatch==='sync')natives[name]=synchronousNative((...args)=>{const blocked=worldCover.blocksWorldInput;const result=callback(...args);updateWorldCover();if(blocked!==worldCover.blocksWorldInput)publish();return result;},{name:'frontend '+name,failure:synchronousFailure});
      else {
      natives[name]=async(...args)=>{const blocked=worldCover.blocksWorldInput;const result=await callback(...args);updateWorldCover();if(blocked!==worldCover.blocksWorldInput)publish();return result;};
      }
    }
    const presentName = 'Java_orsc_BrowserWebGLBridge_present';
    const present = natives[presentName];
    if (typeof present !== 'function') throw new Error('WebGL bridge has no frame presenter');
    if(nativeCallDispatch==='sync') {
      natives[presentName]=synchronousNative((...args)=>{
        const blocked=worldCover.blocksWorldInput;
        const result=present(...args);
        alignWorldSurface(Number(args[1]),Number(args[2]));
        if(blocked!==worldCover.blocksWorldInput)publish();
        gpu.stats.display={internalWidth:Number(args[1]),internalHeight:Number(args[2]),cssWidth:canvas.style.width,cssHeight:canvas.style.height,devicePixelRatio:window.devicePixelRatio||1};
        return result;
      },{name:'frontend present',failure:synchronousFailure});
    } else {
    natives[presentName] = async (...args) => {
      const blocked=worldCover.blocksWorldInput;
      const result = await present(...args);
      // Preserve ordinary AWT input; the loading cover shares the game bounds.
      alignWorldSurface(Number(args[1]),Number(args[2]));
      if(blocked!==worldCover.blocksWorldInput)publish();
      gpu.stats.display={internalWidth:Number(args[1]),internalHeight:Number(args[2]),cssWidth:canvas.style.width,cssHeight:canvas.style.height,devicePixelRatio:window.devicePixelRatio||1};
      return result;
    };
    }
  }
  await new Promise((resolve, reject) => {
    const script = document.createElement('script'); script.src = `https://cjrtnc.leaningtech.com/${window.browserRuntimeVersion}/loader.js`;
    script.onload = resolve; script.onerror = () => reject(new Error('Could not load browser Java runtime')); document.head.append(script);
  });
  await cheerpjInit({version: javaVersion, ...(options.has('wasmgc') ? {wasmGC: true} : {}), disableScaling: true,
    enableInputMethods: false, clipboardMode: 'java', overrideShortcuts: () => false,
    javaProperties: canonicalRenderProperties(['user.dir=/files', ...fixturePolicy.properties, ...geometryTransfer.properties, ...candidatePreparation.properties, ...preparationControls.properties, ...spriteKeyControls.properties, ...modelPreparationControls.properties, ...(options.get('inputdiagnostics')==='1'?['spoiledmilk.browserInputDiagnostics=true']:[]), ...(window.browserProof.mode==='webgl'&&options.has('benchmark') ? ['spoiledmilk.browserBenchmark=true','spoiledmilk.browserCachePolicy='+options.get('cache'),'spoiledmilk.browserUnderlay='+options.get('underlay'),'spoiledmilk.browserDepthSource='+(options.get('depth')||'cpu'),'spoiledmilk.browserLoop='+(options.get('loop')||'legacy'),'spoiledmilk.browserEarlyFaceCull='+(options.get('cull')==='early'),'spoiledmilk.browserOwnsWallsAndRoofs='+(options.get('ownership')==='all'),'spoiledmilk.browserSpriteCache='+(options.get('sprites')||'uncached'),'spoiledmilk.browserOverlayCoverage='+(options.get('overlay')==='coverage')] : []), ...(window.browserProof.mode==='webgl'&&options.get('profile')==='1' ? ['spoiledmilk.browserProfile=true','spoiledmilk.rendererTelemetry=true','spoiledmilk.rendererTelemetryCountersOnly=true','spoiledmilk.rendererTelemetryInterval=1200','spoiledmilk.rendererSlowFrameMs=1000000'] : [])],optimizedDefaults), natives});
  report('Browser Java ready');
  const display = document.querySelector('#display');
  displayLayout=createBrowserDisplay({window,document,display,viewport:document.querySelector('#viewport')});
  window.browserProof.displayLayout=displayLayout.stats;
  displayLayout.attachDesktop(cheerpjCreateDisplay(960,540,display));
  if (gpu) { display.append(gpu.canvas);worldCover.attach(); }
  const code = await cheerpjRunMain('orsc.browser.BrowserBootstrap', '/app/browser-spike.jar:/app/client.jar', window.browserProof.mode);
  window.browserProof.ended = true; report('Java program stopped (exit ' + code + '); see console for compatibility results');
  finish('Java program stopped (exit ' + code + '). See console for compatibility results.');
} catch (error) { report('Bootstrap error: ' + error); console.error(error); finish('Bootstrap error: ' + error); }
