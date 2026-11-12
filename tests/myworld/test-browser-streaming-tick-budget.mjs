import assert from 'node:assert/strict';
import {createBoundedResidency} from '../../tools/browser-gameplay/web/browser-bounded-residency.js';
const chunks=Array.from({length:12},(_,n)=>({id:1000000+n,role:n%2,vertexCount:1,indexCount:3,batchIntCount:5,bytes:160}));
function harness(config={},options={}){
  const stats={},resident=new Map(),timers=[],prepared=[],signals=[],evicted=[];let now=0,partial=0,disposed=0,created=0;
  const api=createBoundedResidency({catalog:{chunks},stats,resident,totalBytes:()=>40+partial+resident.size*124,totalCount:()=>resident.size+(partial?1:0),
    capacityBytes:4096,maxChunks:20,optionalReserveBytes:0,sliceBytes:4096,tickMilliseconds:2,maxTickSteps:32,clock:()=>now,
    schedule:fn=>{timers.push(fn);return fn;},cancelSchedule:fn=>{const i=timers.indexOf(fn);if(i>=0)timers.splice(i,1);},
    evict:id=>{evicted.push(id);resident.delete(id);},prepare:async(c,signal)=>{prepared.push(c.id);signals.push(signal);await options.wait?.();return c;},
    createUpload:c=>{created++;now+=options.createCost||0;let at=0,used=0,owns=false,done=false;const phases=options.phases||[112,12,20,0,112,12];return{
      step:limit=>{assert(limit>=112);used=options.invalidBytes?limit+1:phases[at++];assert(used<=limit||options.invalidBytes);now+=options.cost??.05;
        if(at===4){partial+=124;owns=true;}if(at===phases.length){if(owns)partial-=124;owns=false;resident.set(c.id,{bytes:124});done=true;}return done;},
      dispose:()=>{if(owns){partial-=124;owns=false;}if(!done)disposed++;},get lastStepBytes(){return used;},get allocatedBytes(){return owns?124:0;}};},...config});
  const flush=async()=>{for(let i=0;i<60;i++)await Promise.resolve();};
  const frame=async()=>{const fn=timers.shift();if(fn)fn();await flush();};
  const drain=async()=>{for(let i=0;i<200;i++){await frame();if(!timers.length&&!stats.pendingBytes&&!stats.queuedChunks)return;}throw new Error('Did not drain');};
  return {api,stats,resident,timers,prepared,signals,evicted,frame,flush,drain,elapse:n=>{now+=n;},get partial(){return partial;},get created(){return created;},get disposed(){return disposed;}};
}
{
  const h=harness();h.api.select(chunks.slice(0,3).map(c=>c.id));await h.frame();
  assert(h.api.ready);assert.equal(h.stats.uploads,3);assert.equal(h.stats.ticks,1,'cheap stages/resources retained artificial per-phase RAF delays');
  assert.equal(h.stats.uploadSlices,18);assert.equal(h.stats.maxTickBytes,3*268);assert(h.stats.maxTickMilliseconds<2);assert(h.stats.maxObservedTickSteps<=32);h.api.dispose();
}
{
  const h=harness({sliceBytes:512});h.api.select([chunks[0].id]);await h.drain();assert(h.api.ready);assert(h.stats.maxTickBytes<=512);h.api.dispose();
}
{
  const h=harness({}, {cost:.8});h.api.select([chunks[0].id]);await h.frame();assert(!h.api.ready);assert.equal(h.stats.uploadSlices,3,'time deadline allowed another expensive step');
  assert.equal(h.stats.tickOverruns,1);assert(h.stats.maxTickOverrunMilliseconds>=.39);await h.drain();assert(h.api.ready);assert.equal(h.stats.ticks,2);h.api.dispose();
}
{
  const h=harness({maxTickSteps:3},{cost:0,phases:new Array(20).fill(0)});h.api.select([chunks[0].id]);await h.frame();assert.equal(h.stats.uploadSlices,1);await h.drain();assert(h.api.ready);assert.equal(h.stats.maxObservedTickSteps,3);h.api.dispose();
}
{
  const h=harness({}, {cost:7});h.api.select([chunks[0].id]);await h.frame();assert.equal(h.stats.uploadSlices,1);assert.equal(h.stats.maxTickMilliseconds,7);assert.equal(h.stats.maxTickOverrunMilliseconds,5);h.api.dispose();
}
{
  const h=harness({}, {createCost:7});h.api.select([chunks[0].id]);await h.frame();assert.equal(h.created,1);assert.equal(h.stats.uploadSlices,0,'unbudgeted job constructor hid preparation time');assert.equal(h.stats.maxTickMilliseconds,7);await h.drain();assert(h.api.ready);h.api.dispose();
}
{
  const h=harness({}, {invalidBytes:true});h.api.select([chunks[0].id]);await h.drain();assert(h.api.error);assert(!h.api.ready);assert.equal(h.stats.pendingBytes,0);assert.equal(h.disposed,1);h.api.dispose();
}
{
  const h=harness({capacityBytes:412,maxChunks:3});h.api.select([chunks[0].id],chunks.slice(1).map(c=>c.id));await h.drain();
  assert.equal(h.stats.optionalPlanChunks,2);assert.equal(h.stats.optionalPlanBytes,248);assert.equal(h.resident.size,3);assert.equal(h.prepared.length,3);assert.equal(h.evicted.length,0,'optional-on-optional churn');
  for(let n=0;n<4;n++){h.api.select([chunks[0].id],chunks.slice(1).map(c=>c.id));await h.drain();}assert.equal(h.prepared.length,3);assert.equal(h.evicted.length,0);h.api.dispose();
}
{
  let release;const wait=new Promise(r=>{release=r;}),h=harness({}, {wait:()=>wait});h.api.select([chunks[0].id],[chunks[1].id]);await h.frame();
  const generation=h.stats.generation;h.api.select([chunks[0].id],[chunks[2].id]);assert.equal(h.stats.generation,generation);assert.equal(h.signals[0].aborted,false,'lookahead reorder aborted required source fetch');
  release();await h.drain();assert(h.api.ready);assert(h.resident.has(chunks[0].id));assert(h.resident.has(chunks[2].id));h.api.dispose();
}
{
  let release;const wait=new Promise(r=>{release=r;}),h=harness({}, {wait:()=>wait});h.api.select([chunks[0].id]);await h.frame();h.elapse(3);release();await h.flush();
  assert.equal(h.created,0,'late source completion constructed upload outside a new tick');assert(h.stats.pendingBytes>0,'prepared payload ownership vanished from pending ledger');
  h.api.select([chunks[1].id],[],'teleport');assert.equal(h.stats.cancellations,1);await h.drain();assert(!h.resident.has(chunks[0].id));assert(h.resident.has(chunks[1].id));h.api.dispose();
}
{
  const h=harness({capacityBytes:288,maxChunks:2,maxTickSteps:1});h.api.select([chunks[0].id],[chunks[1].id]);
  for(let n=0;n<50&&!(h.stats.uploads===1&&h.partial>0);n++)await h.frame();assert(h.partial>0);assert(h.resident.has(chunks[0].id));
  h.api.reclaim(124,1);assert.equal(h.partial,0,'optional partial allocation blocked local authority');assert.equal(h.stats.cancellations,1);assert(h.resident.has(chunks[0].id));assert(!h.resident.has(chunks[1].id));await h.drain();assert(h.api.ready);h.api.dispose();
}
console.log('PASS shared tick time/aggregate-byte/step bounds, immediate cheap-resource continuation, measured single-call overruns, optional no-churn admission and in-flight required protection. Synthetic clock/uploads only.');
