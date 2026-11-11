import {createBrowserWebGL} from './webgl.js';
import {createWorkerOutputSizer,createRenderWorkerReplay} from './browser-render-worker-replay.js';
let backend=null,replay=null,poisoned=false,chain=Promise.resolve(),oracle=false,oraclePixels=null;
const arrayFields=['frameSamples','worldFrameSamples','gpuWorkSamples','jsSubmissionSamples','javaBridgeWorkSamples','worldFramePolicies','worldFrameSpriteGroups','physicalOutputWorldFramePolicies'];
let previousSamples={};
function snapshot(frameId){
  // Bounded per-ACK snapshot, not structured cloning growing history every frame.
  const value={};for(const [key,v]of Object.entries(backend.stats)){
    if(arrayFields.includes(key)||key==='measurement')continue;
    if(Array.isArray(v)){value[key]=v.slice(-16);continue;}
    if(v&&typeof v==='object'){value[key]=v;continue;}
    value[key]=v;
  }
  const samples={};for(const k of arrayFields){const a=backend.stats[k]||[],prior=previousSamples[k];const token=k.includes('worldFrame')||k==='physicalOutputWorldFramePolicies'?backend.stats.javaWorldFrames:backend.stats.javaFrames;if(frameId&&a.length&&token!==prior){samples[k]=a[a.length-1];previousSamples[k]=token;}}
  return {value,samples,timeOrigin:performance.timeOrigin};
}
function fatal(error){if(poisoned)return;poisoned=true;self.postMessage({type:'fatal',error:String(error),stack:error?.stack||null});try{replay?.dispose();}catch(_){};}
self.addEventListener('unhandledrejection',event=>{event.preventDefault();fatal(event.reason);});
self.addEventListener('error',event=>fatal(event.error||event.message));
self.onmessage=event=>{
  const message=event.data;
  chain=chain.then(async()=>{
    if(poisoned)return;
    if(message?.type==='init'){
      if(backend||message.version!==1||!message.canvas||message.options?.overlayDeltaTransport===true)throw new Error('Invalid render worker initialization');
      oracle=message.oracle===true;const outputSizer=createWorkerOutputSizer();
      backend=createBrowserWebGL(message.canvas,text=>self.postMessage({type:'report',text}),{...message.options,outputSizer});
      message.canvas.addEventListener('webglcontextlost',()=>fatal(new Error('Render worker context lost')));
      replay=createRenderWorkerReplay({backend,outputSizer});
      self.postMessage({type:'ready',version:1});return;
    }
    if(!replay)throw new Error('Render worker not initialized');
    if(message?.type==='dispose'){replay.dispose();self.postMessage({type:'disposed',stats:{gpuBytes:backend.stats.gpuBytes,spriteResourceBytes:backend.stats.spriteResourceBytes,disposed:backend.stats.disposed===true}});self.close();return;}
    if(message?.type==='readPixels'){
      if(!oracle||!oraclePixels)throw new Error('Render worker readback unavailable');
      const value=oraclePixels;oraclePixels=null;
      self.postMessage({type:'pixels',id:message.id,...value},[value.pixels.buffer]);return;
    }
    const ack=await replay.execute(message);
    if(oracle&&message.frameId){
      const canvas=backend.canvas,gl=canvas.getContext('webgl2'),pixels=new Uint8Array(canvas.width*canvas.height*4);
      gl.readPixels(0,0,canvas.width,canvas.height,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
      if(gl.getError()!==gl.NO_ERROR)throw new Error('Worker oracle readback failed');
      oraclePixels={width:canvas.width,height:canvas.height,pixels};
    }
    ack.stats=snapshot(message.frameId);self.postMessage(ack);
  }).catch(fatal);
};
