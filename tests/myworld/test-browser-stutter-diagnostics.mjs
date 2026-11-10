import assert from 'node:assert/strict';
import {createStutterDiagnostics,stutterDiagnosticsPolicy} from '../../tools/browser-gameplay/web/browser-stutter-diagnostics.js';
let clock=0,next=0,scheduled=new Map(),callback,disconnected=0;
const win={setTimeout(fn,delay){const id=++next;scheduled.set(id,{fn,delay});return id;},clearTimeout(id){scheduled.delete(id);},PerformanceObserver:class{constructor(fn){callback=fn;}observe(value){assert.deepEqual(value,{type:'longtask',buffered:false});}disconnect(){disconnected++;}}};
win.PerformanceObserver.supportedEntryTypes=['longtask'];
const disabled=createStutterDiagnostics(win,{now:()=>clock});assert.equal(scheduled.size,0);assert.equal(next,0);disabled.dispose();
const module=createStutterDiagnostics(win,{enabled:true,now:()=>clock});assert.equal(module.stats.longTaskSupport,true);assert.equal(scheduled.size,1);
for(let n=0;n<600;n++){const [id,value]=scheduled.entries().next().value;scheduled.delete(id);clock+=n===0?250:100;value.fn();}
assert.equal(module.stats.delaySamples.length,512);assert.equal(module.stats.delayCount,600);assert.equal(module.stats.maxDelayMs,150);assert.equal(module.stats.delayOver100,1);
callback({getEntries:()=>Array.from({length:600},(_,n)=>({startTime:n,duration:50+n}))});assert.equal(module.stats.longTaskSamples.length,512);assert.equal(module.stats.longTaskCount,512);assert.equal(module.stats.longTaskDropped,88);
module.reset();assert.equal(module.stats.delaySamples.length,0);assert.equal(module.stats.longTaskCount,0);
const oldCallback=scheduled.values().next().value.fn;module.dispose();module.dispose();assert.equal(scheduled.size,0);assert.equal(disconnected,1);oldCallback();callback({getEntries:()=>[{startTime:1,duration:99}]});assert.equal(module.stats.delayCount,0);assert.equal(module.stats.longTaskCount,0);
const unsupported=createStutterDiagnostics({...win,PerformanceObserver:class{observe(){throw new Error('unsupported');}disconnect(){}}},{enabled:true,now:()=>clock});assert.equal(unsupported.stats.longTaskSupport,false);unsupported.dispose();
assert.equal(stutterDiagnosticsPolicy('webgl',new URLSearchParams('benchmark=1&diagnostics=1')),true);for(const [mode,query]of [['webgl','diagnostics=1'],['software','benchmark=1&diagnostics=1'],['webgl','benchmark=1']])assert.equal(stutterDiagnosticsPolicy(mode,new URLSearchParams(query)),false);
console.log('PASS opt-in bounded delay/longtask observations, unsupported fallback, reset and disposed callbacks; no GC attribution or performance claim');
