import {createRenderWorkerTransport} from './browser-render-worker-transport.js';
import {RENDER_WORKER_SCHEMA,renderNativeName} from './browser-render-worker-protocol.js';
/** Experimental main-thread adapter. Offscreen transfer is irreversible: errors stop. */
export async function createBrowserRenderWorker(canvas,report=()=>{},options={}){
  if(options.overlayDeltaTransport===true||options.nativeCallDispatch==='sync')throw new Error('Unsupported worker transport policy');
  if(typeof canvas.transferControlToOffscreen!=='function')throw new Error('OffscreenCanvas transfer unavailable');
  const worker=options.workerFactory?options.workerFactory():new Worker(new URL('./browser-render-worker-entry.js',import.meta.url),{type:'module',name:'Spoiled Milk private renderer'});
  let disposed=false,readyResolve,readyReject,readyTimer,epoch=0,lastLayout=null,dirty=true,rect=null,dpr=0,readId=1;
  const readers=new Map(),controls=new Map();let measurement=false,disposeResolve=null;const stats={backend:'WebGL2 resident production geometry / private Worker',javaFrames:0,javaWorldFrames:0,javaBridgeReportedWorldFrames:0,frameSamples:[],worldFrameSamples:[],gpuWorkSamples:[],jsSubmissionSamples:[],javaBridgeWorkSamples:[],worldFramePolicies:[],worldFrameSpriteGroups:[],physicalOutputWorldFramePolicies:[]};
  const invalidate=()=>{dirty=true;},win=globalThis.window;
  const observer=typeof ResizeObserver!=='undefined'?new ResizeObserver(invalidate):null;observer?.observe(canvas);
  for(const e of ['resize','browser-display-change'])win?.addEventListener(e,invalidate);
  function layout(){const next=win?.devicePixelRatio||1;if(next!==dpr){dpr=next;dirty=true;}if(dirty){rect=canvas.getBoundingClientRect?.()||{width:0,height:0};dirty=false;}const l={width:rect.width,height:rect.height,dpr};if(!lastLayout||l.width!==lastLayout.width||l.height!==lastLayout.height||l.dpr!==lastLayout.dpr){lastLayout=l;epoch++;}return {epoch,...l};}
  function fatal(error){stats.lastError=String(error);stats.backendFailed=true;readyReject?.(error);for(const p of readers.values())p.reject(error);readers.clear();report('Private render worker stopped: '+error);options.onFatal?.(error);}
  const transport=createRenderWorkerTransport({postMessage:(packet,buffers)=>worker.postMessage(packet,buffers),layout,onFatal:fatal,onStats(snapshot,ack){
    if(!snapshot||!snapshot.value||!snapshot.samples)throw new Error('Missing worker completion stats');
    if(Number.isFinite(snapshot.value.lastFrame)&&Number.isFinite(snapshot.timeOrigin))snapshot.value.lastFrame+=snapshot.timeOrigin-performance.timeOrigin;
    const display=stats.display;Object.assign(stats,snapshot.value);if(display)stats.display=display;
    for(const [key,value]of Object.entries(snapshot.samples)){if(!Array.isArray(stats[key]))throw new Error('Unknown worker sample field');stats[key].push(value);if(stats[key].length>4096)stats[key].shift();}
  stats.renderWorker=transport.stats;stats.renderWorker.lastCompletedPacket=ack.id;if(ack.frameId)stats.renderWorker.lastCompletedFrame=ack.frameId;stats.renderWorker.layoutEpoch=ack.layoutEpoch;stats.renderWorker.executionMilliseconds=ack.executionMilliseconds;
    options.onComplete?.(ack);
  }});
    Object.defineProperty(stats,'measurement',{enumerable:true,get:()=>measurement,set:v=>{if(typeof v!=='boolean')throw new Error('Invalid worker measurement policy');measurement=v;controls.set('$measurement',v);}});
  stats.renderWorker=transport.stats;
  const ready=new Promise((a,b)=>{readyResolve=a;readyReject=b;readyTimer=setTimeout(()=>{transport.fail(new Error('Render worker startup timed out'));},30000);});
  const onMessage=event=>{const m=event.data;if(m?.type==='ready'){clearTimeout(readyTimer);readyResolve();return;}if(m?.type==='report'){report(m.text);return;}if(m?.type==='pixels'){const p=readers.get(m.id);if(!p){transport.fail(new Error('Unknown worker readback'));return;}readers.delete(m.id);p.resolve(m);return;}if(m?.type==='disposed'){disposeResolve?.(m.stats);return;}transport.receive(m);};
  worker.addEventListener('message',onMessage);worker.addEventListener('error',event=>transport.fail(new Error(event.message||'Render worker error')));worker.addEventListener('messageerror',()=>transport.fail(new Error('Render worker message decode failed')));
  try{const offscreen=canvas.transferControlToOffscreen();worker.postMessage({type:'init',version:1,canvas:offscreen,oracle:options.oracle===true,options:{nativeCallDispatch:'async',ownedState:options.ownedState===true,opaqueProof:options.opaqueProof===true,streamUploadValidation:options.streamUploadValidation===true,overlayDeltaTransport:false,overlayRectMode:'off',overlayJournalObserve:false,physicalOutput:options.physicalOutput===true,physicalOutputControl:options.physicalOutputControl===true,benchmarkControl:options.benchmarkControl===true}},[offscreen]);await ready;}
  catch(error){clearTimeout(readyTimer);transport.fail(error);worker.terminate();throw error;}
  const natives={};for(const name of Object.keys(RENDER_WORKER_SCHEMA))if(name[0]!=='$')natives[renderNativeName(name)]=(lib,...args)=>{for(const [key,v]of controls)transport.enqueue(key,[v]);controls.clear();return transport.enqueue(name,args);};
  function dispose(){if(disposed)return;disposed=true;clearTimeout(readyTimer);observer?.disconnect();for(const e of ['resize','browser-display-change'])win?.removeEventListener(e,invalidate);for(const p of readers.values())p.reject(new Error('Render worker disposed'));readers.clear();transport.dispose();try{worker.postMessage({type:'dispose'});}finally{worker.terminate();}stats.disposed=true;}
  function queueControl(key,v,active){if(options.benchmarkControl!==true||typeof v!=='boolean')throw new Error('Private worker control unavailable');controls.set(key,v);return {queued:v,active};}
  return {canvas,natives,stats,dispose,invalidateLayout:invalidate,queueOwnedState:v=>queueControl('$owned',v,stats.ownedStateActive),queueStreamUploadValidation:v=>queueControl('$validation',v,stats.streamUploadValidationActive),queuePhysicalOutput:v=>queueControl('$physical',v,stats.physicalOutputActive),
    ...(options.oracle===true?{async disposeAndConfirm(){await transport.drain();const done=new Promise(resolve=>{disposeResolve=resolve;});worker.postMessage({type:'dispose'});const result=await done;dispose();return result;},async drain(){return transport.drain();},async readPixels(){if(readers.size)throw new Error('Worker oracle readback already pending');await transport.drain();const id=readId++;const result=new Promise((resolve,reject)=>readers.set(id,{resolve,reject}));worker.postMessage({type:'readPixels',id});return result;}}:{})};
}
