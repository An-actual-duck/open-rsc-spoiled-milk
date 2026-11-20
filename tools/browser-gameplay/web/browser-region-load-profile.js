/** Private source-load diagnostics; clocks overlap and are not GPU durations. */
export const BROWSER_LOAD_PROFILE_FIELDS=Object.freeze(['version','owner','attempt','path','width','height','sourceEpoch','required','ready','remainingRepacks','missing','activeKeysMs','textureMs','geometryMs','vertexPackMs','materialPackMs','nativeChunkSubmitMs','spriteMs','lightingMs','skyMs','overlayMs','terminalMs','totalMs','newChunks','replacedChunks','submittedVertices','submittedChunkBytes','textureCount','textureBytes','residentChunks','residentChunkBytes','cachedDraws','packCalls','sourceChunks','missingAtFinish','initializationMs','progressCallMs','presentCallMs','lookaheadOfferMs','frameWorkCallMs']);
export function browserLoadProfilePolicy(mode,options){
  const raw=options.get('loadprofile');if(raw!==null&&!['on','off'].includes(raw))throw new Error('Invalid private loadprofile policy');
  const requested=raw==='on';if(requested&&(mode!=='webgl'||!options.has('benchmark')))throw new Error('Load profiling requires private WebGL benchmark');
  return {requested,properties:mode==='webgl'&&options.has('benchmark')?['spoiledmilk.browserLoadProfile='+requested]:[]};
}
export function createBrowserLoadProfileCollector({enabled=false,gpu=()=>null,clock=()=>performance.now()}={}){
  const stats={requested:enabled,totalReports:0,last:null,samples:[],firstPresentedByEpoch:[],slowPresentedFrames:[],scope:'Java source attempt phases; nested timers. Native submit includes enqueue/copy/backpressure, not Worker/GPU execution.'};
  const lastAttempts=new Map();
  function report(lib,input){
    if(!enabled||(input.length!==36&&input.length!==40))throw new Error('Unexpected bounded load profile');
    const a=new Array(input.length);for(let n=0;n<a.length;n++)a[n]=input[n];for(let n=0;n<a.length;n++){if(!Number.isFinite(a[n])||a[n]<0||(n<11||n>22&&n<35)&&!Number.isSafeInteger(a[n]))throw new Error('Invalid load profile numeric shape');}
    if(a[0]!==1||a[1]<1||a[2]<1||a[3]>4||a[4]>32768||a[5]>32768||a[4]*a[5]>2097152||a[7]>256||a[8]>a[7]||a[33]>256)throw new Error('Invalid load profile schema');
    if(a[2]<=(lastAttempts.get(a[1])||0))throw new Error('Stale load profile attempt');
    lastAttempts.delete(a[1]);lastAttempts.set(a[1],a[2]);if(lastAttempts.size>8)lastAttempts.delete(lastAttempts.keys().next().value);
    const g=gpu(),w=g?.stats?.renderWorker;
    const sample=Object.fromEntries(BROWSER_LOAD_PROFILE_FIELDS.slice(0,a.length).map((key,n)=>[key,a[n]]));
    Object.assign(sample,{receivedTime:clock(),workerSubmittedPacket:w?.submittedPackets??null,workerCompletedPacket:w?.lastCompletedPacket??null,workerCompletedFrame:w?.lastCompletedFrame??null});
    stats.last=sample;stats.totalReports++;stats.samples.push(sample);if(stats.samples.length>128)stats.samples.shift();
    // The rolling sample window expires during a walking leg. Retain the
    // first full Java submission for each recent scene epoch separately.
    const first = stats.firstPresentedByEpoch;
    if(sample.path===3 && sample.sourceEpoch>0 && !first.some(row=>
        row.owner===sample.owner && row.sourceEpoch===sample.sourceEpoch)) {
      first.push(sample);
      if(first.length>8)first.shift();
    }
    const slow=stats.slowPresentedFrames;
    if(sample.path===3&&sample.sourceEpoch>0&&sample.totalMs>=40
        &&slow.filter(row=>row.owner===sample.owner&&row.sourceEpoch===sample.sourceEpoch).length<8){
      slow.push(sample);
      if(slow.length>64)slow.shift();
    }
    if(g){g.stats.loadProfile=stats;g.stats.loadProfileSamples=stats.samples;}
  }
  return {stats,report};
}
