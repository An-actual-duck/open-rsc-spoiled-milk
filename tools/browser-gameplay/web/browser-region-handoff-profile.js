/** Private Java boundary events. Java-relative and browser clocks stay separate. */
export function createRegionHandoffTraceCollector({enabled=false,gpu=()=>null,clock=()=>performance.now(),stopped=()=>false}={}) {
  const stats={requested:enabled,reports:0,lastOrdinal:0,missingEvents:0,events:[],observations:[],
    scope:'Existing Java boundary hooks, bounded to512 producer/2048 observer events. Durations overlap; startMs=-1 means not captured. Browser receipt snapshots are not exact event-time GPU/ACK or scanout measurements.'};
  let dictionary=null;
  function report(lib,phaseText,input) {
    if(stopped())return;
    if(!enabled||typeof phaseText!=='string'||phaseText.length>4096||!input||input.length<8)throw new Error('Unexpected region-handoff trace');
    const phases=phaseText.split('|');
    if(phases.length>64||phases.some(p=>!p||p.length>96||!/^[a-z.-]+$/.test(p)))throw new Error('Invalid handoff phase dictionary');
    if(dictionary!==null&&dictionary!==phaseText)throw new Error('Changed handoff phase dictionary');
    const a=new Array(input.length);for(let n=0;n<a.length;n++)a[n]=input[n];
    if(a.some(x=>!Number.isFinite(x))||a[0]!==1||a[1]!==1||!Number.isSafeInteger(a[2])||a[2]<0||!Number.isSafeInteger(a[3])||a[3]<0
       ||!Number.isSafeInteger(a[4])||a[4]<0||a[4]>512||a[5]!==12||a.length!==8+a[4]*12||a[6]<0||!Number.isSafeInteger(a[7])||a[7]<0
       ||a[3]!==a[2]-a[4]||a[2]<stats.lastOrdinal||(a[4]===0&&a[2]!==0))throw new Error('Invalid handoff trace header');
    const added=[];
    for(let n=0;n<a[4];n++) {
      const r=a.slice(8+n*12,20+n*12),[ordinal,context,kind,phase,observedMs,startMs,durationMs,...payload]=r;
      if(ordinal!==a[3]+n+1||!Number.isSafeInteger(context)||context<1||!Number.isSafeInteger(kind)||kind<1||kind>9
         ||!Number.isSafeInteger(phase)||phase<0||phase>=phases.length||observedMs<0||observedMs>a[6]||startMs< -1||startMs>observedMs
         ||(startMs!==-1&&startMs<0)||durationMs<0||payload.some(x=>!Number.isSafeInteger(x)||Math.abs(x)>Number.MAX_SAFE_INTEGER))throw new Error('Invalid handoff trace row');
      if(ordinal>stats.lastOrdinal)added.push({ordinal,context,kind,phase:phases[phase],observedMs,startMs,durationMs,payload});
    }
    if(added.length&&added[0].ordinal>stats.lastOrdinal+1)stats.missingEvents+=added[0].ordinal-stats.lastOrdinal-1;
    dictionary=phaseText;stats.lastOrdinal=a[2];stats.reports++;
    stats.events.push(...added);if(stats.events.length>2048)stats.events.splice(0,stats.events.length-2048);
    const g=gpu()?.stats,w=g?.renderWorker;
    stats.observations.push({receivedBrowserMs:clock(),snapshotJavaMs:a[6],context:a[7],lastOrdinal:a[2],producerOverwritten:a[3],newEvents:added.length,
      gpu:g?{sourceEpoch:g.worldReadiness?.epoch??null,phase:g.worldReadiness?.phase??null,freshWorldFrames:g.javaWorldFrames??null,
        heldWorldFrames:g.heldWorldFrames??null,lastFreshBrowserMs:g.lastFrame??null,workerSubmittedPacket:w?.lastSubmittedPacket??null,
        workerCompletedPacket:w?.lastCompletedPacket??null,workerCompletedFrame:w?.lastCompletedFrame??null}:null});
    if(stats.observations.length>128)stats.observations.shift();
  }
  return {stats,report};
}
