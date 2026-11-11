import {ownRenderCommand,inspectRenderCommand,validateRenderLayout,RENDER_WORKER_MAX_BYTES,RENDER_WORKER_MAX_COMMANDS} from './browser-render-worker-protocol.js';
/** Two unACKed packets + one bounded producer; promises never retain borrowed inputs. */
export function createRenderWorkerTransport({postMessage,layout,now=()=>performance.now(),onStats=()=>{},onFatal=()=>{}}){
  let commands=[],transfers=[],bytes=0,nextId=1,nextFrame=1,lastAck=0,lastLayout=0,flushing=false,failed=null,disposed=false,worldPending=false;
  const pending=new Map(),waiters=[];
  const stats={enabled:true,submittedPackets:0,completedPackets:0,submittedFrames:0,completedFrames:0,copiedBytes:0,transferredBytes:0,copyMilliseconds:0,backpressureWaits:0,maxInFlight:0,maxStagedBytes:0,inFlightBytes:0,stagedBytes:0,queueAgeMilliseconds:0,droppedFrames:0};
  function alive(){if(failed||disposed)throw failed||new Error('Render worker disposed');}
  function fail(error,notify=true){if(failed||disposed)return;failed=error instanceof Error?error:new Error(String(error));commands=[];transfers=[];bytes=0;stats.stagedBytes=0;stats.lastError=String(failed);for(const p of pending.values())p.reject(failed);pending.clear();stats.inFlightBytes=0;while(waiters.length)waiters.shift().reject(failed);if(notify)onFatal(failed);}
  async function slot(){alive();if(pending.size<2)return;stats.backpressureWaits++;await new Promise((resolve,reject)=>waiters.push({resolve,reject}));alive();}
  async function flush({frame=false,result=false}={}){
    alive();if(!commands.length)return undefined;
    if(flushing){const e=new Error('Concurrent render flush');fail(e);throw e;}flushing=true;
    try{
      await slot();alive();const snapshot=validateRenderLayout(layout());if(snapshot.epoch<lastLayout)throw new Error('Render layout moved backwards');lastLayout=snapshot.epoch;
      const id=nextId++,frameId=frame?nextFrame++:0;
      const packet={version:1,type:'batch',id,frameId,layout:snapshot,commands,bytes};
      const transfer=transfers;commands=[];transfers=[];bytes=0;stats.stagedBytes=0;flushing=false;
      let resolve,reject;const done=new Promise((a,b)=>{resolve=a;reject=b;});done.catch(()=>{});
      pending.set(id,{resolve,reject,done,bytes:packet.bytes,frameId,layoutEpoch:snapshot.epoch,sent:now()});stats.inFlightBytes+=packet.bytes;stats.maxInFlight=Math.max(stats.maxInFlight,pending.size);stats.submittedPackets++;if(frame)stats.submittedFrames++;
      stats.transferredBytes+=transfer.reduce((sum,b)=>sum+b.byteLength,0);postMessage(packet,transfer);
      return result?await done:undefined;
    }catch(error){fail(error);throw error;}finally{flushing=false;}
  }
  function capture(name,args){
    alive();if(flushing)throw new Error('Render producer during backpressure');const shape=inspectRenderCommand(name,args);
    // Never cross await while retaining live Java args: oversize producer batch is
    // rejected rather than auto-flushed with borrowed input still on its stack.
    if(commands.length>=RENDER_WORKER_MAX_COMMANDS||bytes+shape.bytes>RENDER_WORKER_MAX_BYTES){const e=new Error('Render producer batch exhausted');fail(e);throw e;}
    const begin=now(),owned=ownRenderCommand(name,args);stats.copyMilliseconds+=now()-begin;stats.copiedBytes+=owned.transfers.reduce((sum,b)=>sum+b.byteLength,0);commands.push(owned.command);transfers.push(...owned.transfers);bytes+=owned.bytes;stats.stagedBytes=bytes;stats.maxStagedBytes=Math.max(stats.maxStagedBytes,bytes);
    if(name==='present'){
      if(worldPending){const e=new Error('Missing world frameWork before next present');fail(e);throw e;}
      if(args[2]){worldPending=true;return;}
      return flush({frame:true});
    }
    if(name==='frameWork'){if(!worldPending){const e=new Error('frameWork without world presentation');fail(e);throw e;}worldPending=false;return flush({frame:true});}
    if(name==='holdWorld')return flush({frame:true});
    if(name==='initialize'||name==='planWorldSprites')return flush({result:true});
  }
  function enqueue(name,args){try{return capture(name,args);}catch(error){fail(error);throw error;}}
  function receive(message){
    if(disposed||failed)return;
    try{
      if(message?.type==='fatal')throw new Error(message.error||'Render worker failed');
      if(message?.type!=='ack'||message.id!==lastAck+1)throw new Error('Out-of-order render ACK');
      const p=pending.get(message.id);if(!p||p.frameId!==message.frameId)throw new Error('Unknown render ACK identity');
      if(message.layoutEpoch!==p.layoutEpoch)throw new Error('Invalid render ACK layout');
      onStats(message.stats,message);
      pending.delete(message.id);lastAck=message.id;stats.inFlightBytes-=p.bytes;stats.completedPackets++;if(p.frameId)stats.completedFrames++;stats.queueAgeMilliseconds=Math.max(0,now()-p.sent);
      p.resolve(message.result);if(waiters.length)waiters.shift().resolve();
    }catch(error){fail(error);}
  }
  async function drain(){if(worldPending)throw new Error('Unfinished world frame');if(commands.length)await flush({result:true});await Promise.all([...pending.values()].map(p=>p.done));alive();}
  function dispose(){if(disposed)return;fail(new Error('Render worker disposed'),false);disposed=true;stats.disposed=true;}
  return {enqueue,flush,drain,receive,fail,dispose,stats};
}
