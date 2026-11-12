/** Metadata-only bounded scheduler. GPU/payload ownership stays in its callbacks.
 * One fetch/hash and one incremental upload job at a time; no unbounded promises.
 * Required IDs cannot be evicted. Optional IDs use spare capacity only.
 */
export function createBoundedResidency({catalog,stats={},capacityBytes=256*1024*1024,maxChunks=512,
  maxPendingBytes=64*1024*1024,maxQueue=512,maxQueueBytes=320*1024*1024,sliceBytes=4*1024*1024,optionalReserveBytes=32*1024*1024,tickMilliseconds=2,maxTickSteps=32,clock=()=>performance.now(),
  resident,totalBytes,totalCount=()=>resident.size,evict,prepare,createUpload,
  schedule=fn=>globalThis.requestAnimationFrame?requestAnimationFrame(fn):setTimeout(fn,16),
  cancelSchedule=id=>globalThis.cancelAnimationFrame?cancelAnimationFrame(id):clearTimeout(id),onError=()=>{}}){
  for(const [name,value]of Object.entries({capacityBytes,maxChunks,maxPendingBytes,maxQueue,maxQueueBytes,sliceBytes,maxTickSteps}))if(!Number.isSafeInteger(value)||value<1)throw new Error('Invalid bounded scheduler '+name);
  if(!Number.isFinite(tickMilliseconds)||tickMilliseconds<=0)throw new Error('Invalid bounded scheduler tick budget');
  if(sliceBytes<112||sliceBytes%4||!Number.isSafeInteger(optionalReserveBytes)||optionalReserveBytes<0)throw new Error('Invalid bounded scheduler slice/reserve');
  const byId=new Map(catalog.chunks.map(c=>[c.id,c])),lru=new Map();
  let required=new Set(),queue=[],generation=0,key=null,loading=null,job=null,timer=null,disposed=false,suspended=true,failed=null,tick=0,activeTick=null,optionalKey=null,optionalDesired=new Set();
  const bytes=c=>c.vertexCount*112+c.indexCount*4;
  Object.assign(stats,{policy:'bounded',capacityBytes,maxChunks,maxPendingBytes,maxQueue,maxQueueBytes,sliceBytes,tickMilliseconds,maxTickSteps,
    generation:0,requiredChunks:0,requiredBytes:0,missingChunks:0,ready:false,queuedChunks:0,queuedBytes:0,
    pendingBytes:0,peakPendingBytes:0,evictions:0,cancellations:0,uploads:0,uploadSlices:0,peakGpuBytes:totalBytes(),
    hashInputCopyUpperBoundBytes:64*1024*1024,ticks:0,maxTickMilliseconds:0,totalTickMilliseconds:0,maxTickBytes:0,maxObservedTickSteps:0,tickOverruns:0,maxTickOverrunMilliseconds:0,workBytes:0,optionalPlanChunks:0,optionalPlanBytes:0,
    scope:'Geometry+texture allocation cap; pending raw+batch CPU bytes bounded separately; WebCrypto input copy may add up to64MiB. Retained CPU/Worker, HTTP reader, sprites/overlay/shadows/framebuffers/driver memory separate'});
  function account(){
    stats.queuedChunks=queue.length;stats.queuedBytes=queue.reduce((n,c)=>n+c.bytes,0);
    stats.missingChunks=[...required].filter(id=>!resident.has(id)).length;
    stats.ready=!disposed&&!failed&&!suspended&&stats.missingChunks===0;
    stats.peakGpuBytes=Math.max(stats.peakGpuBytes,totalBytes());
    if(totalBytes()>capacityBytes||totalCount()>maxChunks)throw new Error('Bounded GPU allocation/count cap violated');
  }
  function wake(){if(!disposed&&!failed&&!loading&&timer===null&&(queue.length||job))timer=schedule(()=>{timer=null;pump();});}
  function cancelJob(){if(job){job.value.dispose();job=null;stats.cancellations++;}if(loading){loading.controller.abort();stats.cancellations++;}stats.pendingBytes=loading?.bytes||0;}
  function stop(error){if(failed||disposed)return;failed=error;cancelJob();queue=[];stats.ready=false;stats.error=String(error);account();onError(error);}
  function ensureRoom(c,optional){
    while(totalBytes()+bytes(c)>capacityBytes-(optional?optionalReserveBytes:0)||totalCount()+1>maxChunks){
      const victim=[...resident.keys()].filter(id=>byId.has(id)&&!required.has(id)&&(!optional||!optionalDesired.has(id))).sort((a,b)=>(lru.get(a)||0)-(lru.get(b)||0))[0];
      if(victim===undefined){if(optional)return false;throw new Error('Required active GPU set cannot fit bounded capacity including live geometry/textures');}
      evict(victim);lru.delete(victim);stats.evictions++;
    }
    return true;
  }
  function reclaim(extraBytes,extraCount=0){
    if(disposed||failed)throw failed||new Error('Bounded residency disposed');
    while(totalBytes()+extraBytes>capacityBytes||totalCount()+extraCount>maxChunks){
      const victim=[...resident.keys()].filter(id=>byId.has(id)&&!required.has(id)).sort((a,b)=>(lru.get(a)||0)-(lru.get(b)||0))[0];
      if(victim===undefined)throw new Error('Required active GPU resources leave no room within bounded capacity');
      evict(victim);lru.delete(victim);stats.evictions++;
    }
  }
  function refreshOptional(optionalIds,force=false){
    const signature=optionalIds.join(',');if(!force&&signature===optionalKey)return;optionalKey=signature;
    const inflight=id=>job?.id===id||loading?.id===id&&loading.generation===generation&&!loading.controller.signal.aborted;
    const missing=[...required].filter(id=>!resident.has(id)&&!inflight(id)).map(id=>byId.get(id)).sort((a,b)=>a.role-b.role);
    let queuedBytes=missing.reduce((n,c)=>n+c.bytes,0);
    if(queuedBytes>maxQueueBytes){const error=new Error('Required bounded queue byte cap exceeded');stop(error);throw error;}
    let catalogBytes=0,catalogCount=0;for(const id of resident.keys())if(byId.has(id)){catalogBytes+=bytes(byId.get(id));catalogCount++;}
    // Admit one stable optional working set. A later optional upload must not
    // evict an earlier member just to churn through a larger descriptor queue.
    let room=capacityBytes-optionalReserveBytes-(totalBytes()-catalogBytes)-stats.requiredBytes;
    let countRoom=maxChunks-(totalCount()-catalogCount)-required.size;
    optionalDesired=new Set();let optionalBytes=0;
    for(const id of optionalIds){const c=byId.get(id);if(!c||required.has(id)||optionalDesired.has(id))continue;
      if(bytes(c)>room||countRoom<1||c.bytes+c.batchIntCount*4>maxPendingBytes)continue;
      if(!resident.has(id)&&!inflight(id)&&(missing.length>=maxQueue||queuedBytes+c.bytes>maxQueueBytes))continue;
      optionalDesired.add(id);room-=bytes(c);countRoom--;optionalBytes+=bytes(c);
      if(!resident.has(id)&&!inflight(id)){missing.push(c);queuedBytes+=c.bytes;}
    }
    queue=missing;stats.optionalPlanChunks=optionalDesired.size;stats.optionalPlanBytes=optionalBytes;
  }
  function select(requiredIds,optionalIds=[],generationKey=''){
    if(disposed||failed)throw failed||new Error('Bounded residency disposed');
    const next=new Set(requiredIds);if(next.size>maxQueue)throw new Error('Required bounded queue count exceeded');
    for(const id of next)if(!byId.has(id))throw new Error('Unknown required catalogue resource');
    const nextKey=generationKey+'|'+[...next].sort((a,b)=>a-b).join(',');
    if(nextKey===key){refreshOptional(optionalIds);account();wake();return stats.ready;}
    key=nextKey;suspended=false;generation++;stats.generation=generation;activeTick=null;cancelJob();required=next;
    let requiredBytes=0,residentCatalogBytes=0,residentCatalogCount=0;
    for(const id of required){requiredBytes+=bytes(byId.get(id));lru.set(id,++tick);}
    for(const id of resident.keys())if(byId.has(id)){residentCatalogBytes+=bytes(byId.get(id));residentCatalogCount++;}
    if(requiredBytes+totalBytes()-residentCatalogBytes>capacityBytes||required.size+totalCount()-residentCatalogCount>maxChunks){
      const error=new Error('Required active GPU set exceeds explicit bounded'+capacityBytes/(1024*1024)+'MiB/count capacity; full residency reference remains available');stop(error);throw error;
    }
    stats.requiredChunks=required.size;stats.requiredBytes=requiredBytes;
    refreshOptional(optionalIds,true);account();wake();return stats.ready;
  }
  function tickAvailable(t){return t&&t===activeTick&&clock()<t.deadline&&t.bytes+112<=sliceBytes&&t.steps<maxTickSteps;}
  function reportTick(t){
    const elapsed=Math.max(0,clock()-t.started),delta=Math.max(0,elapsed-t.reported);t.reported=elapsed;
    stats.totalTickMilliseconds+=delta;stats.maxTickMilliseconds=Math.max(stats.maxTickMilliseconds,elapsed);
    stats.maxTickBytes=Math.max(stats.maxTickBytes,t.bytes);stats.maxObservedTickSteps=Math.max(stats.maxObservedTickSteps,t.steps);
    if(elapsed>tickMilliseconds){if(!t.overrun){stats.tickOverruns++;t.overrun=true;}stats.maxTickOverrunMilliseconds=Math.max(stats.maxTickOverrunMilliseconds,elapsed-tickMilliseconds);}
  }
  function continueTick(t){
    if(disposed||failed||t!==activeTick)return;
    try{
      while(tickAvailable(t)){
        if(job){
          const current=job;if(current.generation!==generation){cancelJob();break;}
          const limit=sliceBytes-t.bytes,done=current.value.step(limit);
          const used=current.value.lastStepBytes??limit;
          if(!Number.isSafeInteger(used)||used<0||used>limit)throw new Error('Bounded upload exceeded shared tick byte budget');
          t.bytes+=used;t.steps++;stats.workBytes+=used;stats.uploadSlices++;
          if(done){job=null;stats.pendingBytes=0;stats.uploads++;lru.set(current.id,++tick);}
          account();continue;
        }
        if(loading)break;
        while(queue.length&&resident.has(queue[0].id))queue.shift();
        const c=queue.shift();account();if(!c)break;
        const pendingBytes=c.bytes+c.batchIntCount*4;
        if(pendingBytes>maxPendingBytes)throw new Error('Bounded prepared resource exceeds pending byte cap');
        if(!ensureRoom(c,!required.has(c.id)))continue;
        const controller=new AbortController(),token={id:c.id,generation,controller,bytes:pendingBytes};loading=token;
        stats.pendingBytes=pendingBytes;stats.peakPendingBytes=Math.max(stats.peakPendingBytes,pendingBytes);
        Promise.resolve().then(()=>prepare(c,controller.signal)).then(payload=>{
          if(disposed||failed||generation!==token.generation||controller.signal.aborted)return;
          if(!ensureRoom(c,!required.has(c.id)))return;
          job={id:c.id,generation,value:createUpload(c,payload)};
        }).catch(error=>{if(!disposed&&!controller.signal.aborted&&generation===token.generation)stop(error);}).finally(()=>{
          if(loading===token)loading=null;stats.pendingBytes=job?pendingBytes:0;account();
          // Immediate fetch/hash completion may spend the same tick's remaining
          // budget. A later completion gets a new RAF, never a fresh hidden budget.
          if(job&&tickAvailable(t))continueTick(t);else wake();
        });
        break;
      }
    }catch(error){stop(error);}
    finally{reportTick(t);wake();}
  }
  function pump(){
    if(disposed||failed)return;
    const started=clock(),t={started,deadline:started+tickMilliseconds,bytes:0,steps:0,reported:0,overrun:false};
    activeTick=t;stats.ticks++;continueTick(t);
  }
  function dispose(){if(disposed)return;disposed=true;generation++;activeTick=null;if(timer!==null)cancelSchedule(timer);timer=null;cancelJob();queue=[];required.clear();stats.ready=false;stats.queuedChunks=stats.queuedBytes=0;}
  function suspend(){if(disposed||failed)return;suspended=true;generation++;activeTick=null;stats.generation=generation;key=null;optionalKey=null;optionalDesired.clear();cancelJob();queue=[];required.clear();stats.requiredChunks=stats.requiredBytes=0;account();}
  return {select,pump,reclaim,suspend,dispose,get ready(){return !disposed&&!failed&&stats.ready;},get error(){return failed;},get required(){return new Set(required);}};
}
