import {validateRenderBatch,renderNativeName,inspectRenderCommand,ownRenderCommand} from './browser-render-worker-protocol.js';
import {physicalOutputSize} from './browser-physical-output.js';
/** Main DOM layout is explicit, immutable packet state; never a worker DPR fallback. */
export function createWorkerOutputSizer(){
  let current=null,maxTextureSize=4096;
  return {enabled:true,setLayout(layout){current=Object.freeze({...layout});},setMaximum(n){if(!Number.isSafeInteger(n)||n<1)throw new Error('Invalid worker texture capability');maxTextureSize=n;},
    size(w,h){if(!current||!(current.width>0&&current.height>0))throw new Error('Missing positive main-thread output layout');return {...physicalOutputSize(w,h,current,current.dpr,maxTextureSize),cssWidth:current.width,cssHeight:current.height,devicePixelRatio:current.dpr};},dispose(){current=null;}};
}
/** Serial executor; validate the entire envelope before invoking any backend method. */
export function createRenderWorkerReplay({backend,outputSizer,now=()=>performance.now(),walkBuffer=false}){
  let nextId=1,lastFrame=0,lastLayout=0,lastGeometry=null,failed=false;
  const deferredResources=new Set(['uploadChunk','replaceChunk','deleteChunks','uploadTexture','deleteTextures']);
  const liveCommands=new Set(['regionTransition','walkingTransition','worldProgress','holdWorld']);
  const planningCommands=new Set([...liveCommands,...deferredResources,'wholeZoneFrame','planWorldSprites']);
  const maxDeferredBytes=16*1024*1024,maxDeferredCommands=128;
  let deferred=[],deferredBytes=0;
  function stageResources(commands){
    const resources=commands.filter(c=>deferredResources.has(c.name));
    if(deferred.length+resources.length>maxDeferredCommands)return false;
    const bytes=resources.reduce((total,c)=>total+inspectRenderCommand(c.name,c.args).bytes,0);
    if(deferredBytes+bytes>maxDeferredBytes)return false;
    // An ACK returns the original packet buffers. Own only the GPU updates
    // that carry over, while the old scene still owns its live resources.
    for(const c of resources)deferred.push(ownRenderCommand(c.name,c.args).command);
    deferredBytes+=bytes;
    backend.stats.walkingDeferredPeakBytes=Math.max(backend.stats.walkingDeferredPeakBytes||0,deferredBytes);
    backend.stats.walkingDeferredResources=(backend.stats.walkingDeferredResources||0)+resources.length;
    return true;
  }
  async function execute(packet){
    if(failed)throw new Error('Render replay poisoned');
    try{
      validateRenderBatch(packet);
      if(packet.id!==nextId||packet.layout.epoch<lastLayout||(packet.frameId&&packet.frameId!==lastFrame+1))throw new Error('Render packet ordering violation');
      if(packet.layout.epoch===lastLayout&&lastGeometry&&(packet.layout.width!==lastGeometry.width||packet.layout.height!==lastGeometry.height||packet.layout.dpr!==lastGeometry.dpr))throw new Error('Changed layout requires fresh epoch');
      // Verify all entrypoints before any prefix may change backend state.
      for(const c of packet.commands)if(c.name==='$measurement'?false:c.name[0]==='$'?typeof backend[{ '$owned':'queueOwnedState','$validation':'queueStreamUploadValidation','$physical':'queuePhysicalOutput'}[c.name]]!=='function':typeof backend.natives[renderNativeName(c.name)]!=='function')throw new Error('Missing render worker entrypoint');
      const sameLayout=packet.layout.epoch===lastLayout;
      // A second transition invalidates the old transaction even if its
      // layout is identical. Finish the accepted prefix in packet order.
      const superseded=deferred.length>0&&packet.commands.some(c=>
        c.name==='regionTransition'||c.name==='walkingTransition');
      const keepLive=backend.stopWalkingReplay&&sameLayout&&!superseded
        &&packet.commands.every(c=>liveCommands.has(c.name));
      const staged=walkBuffer&&sameLayout&&!superseded&&backend.hasActiveWalkingReplay?.()
        &&packet.commands.every(c=>planningCommands.has(c.name))&&stageResources(packet.commands);
      if(!keepLive&&!staged)backend.stopWalkingReplay?.();
      outputSizer.setLayout(packet.layout);lastGeometry={...packet.layout};lastLayout=packet.layout.epoch;
      let result;const start=now();
      if(!keepLive&&!staged&&deferred.length){
        const updates=deferred;deferred=[];deferredBytes=0;
        for(const c of updates)await backend.natives[renderNativeName(c.name)](null,...c.args);
      }
      const commands=staged?packet.commands.filter(c=>!deferredResources.has(c.name)):packet.commands;
      for(const c of commands){
        if(c.name==='$measurement'){backend.stats.measurement=c.args[0];result=undefined;}
        else if(c.name[0]==='$')result=backend[{ '$owned':'queueOwnedState','$validation':'queueStreamUploadValidation','$physical':'queuePhysicalOutput'}[c.name]](...c.args);
        else result=await backend.natives[renderNativeName(c.name)](null,...c.args);
        if(c.name==='initialize')outputSizer.setMaximum(result);
      }
      nextId++;if(packet.frameId)lastFrame=packet.frameId;
      return {type:'ack',version:1,id:packet.id,frameId:packet.frameId,layoutEpoch:lastLayout,result,executionMilliseconds:now()-start};
    }catch(error){failed=true;throw error;}
  }
  return {execute,dispose(){failed=true;deferred=[];deferredBytes=0;backend.dispose();}};
}
