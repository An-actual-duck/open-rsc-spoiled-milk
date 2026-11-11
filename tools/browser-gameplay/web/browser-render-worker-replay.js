import {validateRenderBatch,renderNativeName} from './browser-render-worker-protocol.js';
import {physicalOutputSize} from './browser-physical-output.js';
/** Main DOM layout is explicit, immutable packet state; never a worker DPR fallback. */
export function createWorkerOutputSizer(){
  let current=null,maxTextureSize=4096;
  return {enabled:true,setLayout(layout){current=Object.freeze({...layout});},setMaximum(n){if(!Number.isSafeInteger(n)||n<1)throw new Error('Invalid worker texture capability');maxTextureSize=n;},
    size(w,h){if(!current||!(current.width>0&&current.height>0))throw new Error('Missing positive main-thread output layout');return {...physicalOutputSize(w,h,current,current.dpr,maxTextureSize),cssWidth:current.width,cssHeight:current.height,devicePixelRatio:current.dpr};},dispose(){current=null;}};
}
/** Serial executor; validate the entire envelope before invoking any backend method. */
export function createRenderWorkerReplay({backend,outputSizer,now=()=>performance.now()}){
  let nextId=1,lastFrame=0,lastLayout=0,lastGeometry=null,failed=false;
  async function execute(packet){
    if(failed)throw new Error('Render replay poisoned');
    try{
      validateRenderBatch(packet);
      if(packet.id!==nextId||packet.layout.epoch<lastLayout||(packet.frameId&&packet.frameId!==lastFrame+1))throw new Error('Render packet ordering violation');
      if(packet.layout.epoch===lastLayout&&lastGeometry&&(packet.layout.width!==lastGeometry.width||packet.layout.height!==lastGeometry.height||packet.layout.dpr!==lastGeometry.dpr))throw new Error('Changed layout requires fresh epoch');
      // Verify all entrypoints before any prefix may change backend state.
      for(const c of packet.commands)if(c.name==='$measurement'?false:c.name[0]==='$'?typeof backend[{ '$owned':'queueOwnedState','$validation':'queueStreamUploadValidation','$physical':'queuePhysicalOutput'}[c.name]]!=='function':typeof backend.natives[renderNativeName(c.name)]!=='function')throw new Error('Missing render worker entrypoint');
      outputSizer.setLayout(packet.layout);lastGeometry={...packet.layout};lastLayout=packet.layout.epoch;
      let result;const start=now();
      for(const c of packet.commands){
        if(c.name==='$measurement'){backend.stats.measurement=c.args[0];result=undefined;}
        else if(c.name[0]==='$')result=backend[{ '$owned':'queueOwnedState','$validation':'queueStreamUploadValidation','$physical':'queuePhysicalOutput'}[c.name]](...c.args);
        else result=await backend.natives[renderNativeName(c.name)](null,...c.args);
        if(c.name==='initialize')outputSizer.setMaximum(result);
      }
      nextId++;if(packet.frameId)lastFrame=packet.frameId;
      return {type:'ack',version:1,id:packet.id,frameId:packet.frameId,layoutEpoch:lastLayout,result,executionMilliseconds:now()-start};
    }catch(error){failed=true;throw error;}
  }
  return {execute,dispose(){failed=true;backend.dispose();}};
}
