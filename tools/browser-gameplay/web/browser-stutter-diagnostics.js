/** Explicit private diagnostic only. Delays/long tasks are observations, not GC attribution. */
export function stutterDiagnosticsPolicy(mode,options){return mode==='webgl'&&options.has('benchmark')&&options.get('diagnostics')==='1';}
export function createStutterDiagnostics(win,{enabled=false,now=()=>performance.now()}={}) {
  const LIMIT=512,INTERVAL=100;
  const stats={enabled,intervalMs:INTERVAL,delaySamples:[],longTaskSamples:[],delayCount:0,delayOver16:0,delayOver50:0,delayOver100:0,maxDelayMs:0,longTaskSupport:false,longTaskCount:0,longTaskDropped:0,disposed:false};
  let timer=null,observer=null,expected=0;
  function append(rows,sample){rows.push(sample);if(rows.length>LIMIT)rows.shift();}
  function sample(){
    if(stats.disposed)return;
    const time=now(),delay=Math.max(0,time-expected);
    append(stats.delaySamples,{milliseconds:time,delayMs:delay});stats.delayCount++;
    if(delay>16)stats.delayOver16++;if(delay>50)stats.delayOver50++;if(delay>100)stats.delayOver100++;
    stats.maxDelayMs=Math.max(stats.maxDelayMs,delay);schedule();
  }
  function schedule(){expected=now()+INTERVAL;timer=win.setTimeout(sample,INTERVAL);}
  function reset(){
    if(!enabled||stats.disposed)return;
    stats.delaySamples.length=stats.longTaskSamples.length=0;
    for(const key of ['delayCount','delayOver16','delayOver50','delayOver100','maxDelayMs','longTaskCount','longTaskDropped'])stats[key]=0;
  }
  if(enabled){
    schedule();
    if(typeof win.PerformanceObserver==='function'&&Array.isArray(win.PerformanceObserver.supportedEntryTypes)&&win.PerformanceObserver.supportedEntryTypes.includes('longtask'))try {
      observer=new win.PerformanceObserver(list=>{
        if(stats.disposed)return;const entries=list.getEntries();stats.longTaskDropped+=Math.max(0,entries.length-LIMIT);
        for(let n=Math.max(0,entries.length-LIMIT);n<entries.length;n++) {
          const entry=entries[n];if(!Number.isFinite(entry.startTime)||!Number.isFinite(entry.duration)||entry.duration<0)continue;
          append(stats.longTaskSamples,{milliseconds:entry.startTime,durationMs:entry.duration});stats.longTaskCount++;
        }
      });
      observer.observe({type:'longtask',buffered:false});stats.longTaskSupport=true;
    }catch(error){observer?.disconnect();observer=null;stats.longTaskSupport=false;}
  }
  function dispose(){if(stats.disposed)return;stats.disposed=true;if(timer!==null)win.clearTimeout(timer);observer?.disconnect();}
  return {stats,reset,dispose};
}
