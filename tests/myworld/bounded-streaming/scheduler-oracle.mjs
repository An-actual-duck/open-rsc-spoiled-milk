/** Adversarial caller-owned allocation ledger for actual bounded scheduler. No GL. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import assert from 'node:assert/strict';
const args=process.argv.slice(2),option=n=>{const i=args.indexOf(n);if(i<0||!args[i+1])throw Error('Required '+n);return path.resolve(args[i+1]);};
const root=option('--source-root'),out=option('--out'),file=path.join(root,'tools/browser-gameplay/web/browser-bounded-residency.js'),source=fs.readFileSync(file,'utf8');
const {createBoundedResidency}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
let checks=0;const check=(b,label)=>{assert(b,label);checks++;};
const chunks=Array.from({length:8},(_,n)=>({id:1000000+n,role:n%2,vertexCount:1,indexCount:3,batchIntCount:5,bytes:160}));
const id=n=>chunks[n].id,geometryBytes=124;
function harness(options={}){
 const stats={},resident=new Map(),timers=new Map(),ledger={partial:0,peak:40,evictions:[],published:[],disposed:0,errors:[],prepare:[],signals:[],protected:new Set()};let next=0;
 const total=()=>40+ledger.partial+[...resident.values()].reduce((n,c)=>n+c.bytes,0);
 const api=createBoundedResidency({catalog:{chunks},stats,capacityBytes:412,maxChunks:3,maxPendingBytes:256,maxQueue:8,maxQueueBytes:1280,sliceBytes:128,optionalReserveBytes:0,resident,totalBytes:total,totalCount:()=>resident.size+(ledger.partial?1:0),schedule:fn=>{timers.set(++next,fn);return next;},cancelSchedule:i=>timers.delete(i),
  evict:i=>{assert(!ledger.protected.has(i),'active selected resource evicted');ledger.evictions.push(i);resident.delete(i);},
  prepare:async(c,signal)=>{ledger.prepare.push(c.id);ledger.signals.push(signal);return options.prepare?options.prepare(c,signal):{id:c.id};},
  createUpload:(c,payload)=>{check(payload.id===c.id,'source/payload identity');let steps=0,owns=false,done=false;return {step:limit=>{check(limit<=128,'upload slice bounded');if(options.failStep)throw Error('upload failure');if(!owns){ledger.partial+=geometryBytes;owns=true;ledger.peak=Math.max(ledger.peak,total());return false;}if(++steps<2)return false;ledger.partial-=geometryBytes;owns=false;resident.set(c.id,{bytes:geometryBytes});ledger.published.push(c.id);done=true;return true;},dispose:()=>{if(owns){ledger.partial-=geometryBytes;owns=false;}if(!done)ledger.disposed++;}};},
  onError:e=>ledger.errors.push(String(e)),...options.config});
 const select=(required,optional=[],key='')=>{ledger.protected=new Set(required);return api.select(required,optional,key);};
 const advance=async()=>{await Promise.resolve();await Promise.resolve();const t=timers.entries().next().value;if(t){timers.delete(t[0]);t[1]();}await Promise.resolve();await Promise.resolve();};
 const drain=async()=>{for(let n=0;n<300;n++){await advance();if(!timers.size&&stats.pendingBytes===0)return;}throw Error('scheduler did not drain');};
 return {api,stats,resident,ledger,timers,total,select,advance,drain};
}
{
 const h=harness();check(!h.select([id(0),id(1)],[id(2)],'source-A/context1'),'not ready from descriptors alone');await h.drain();check(h.api.ready,'required set ready after publication');check(h.resident.size===3,'count ledger includes actual optional uploads');check(h.total()<=412&&h.ledger.peak<=412,'combined fixed texture plus allocated/partial geometry cap');check(h.stats.peakPendingBytes<=256,'transient pending bounded');check(h.stats.queuedChunks===0,'queue drained');
 const requiredCopy=h.api.required;requiredCopy.clear();check(h.api.required.size===2,'defensive required ownership');
 h.select([id(1),id(3)],[],'source-A/context1');await h.drain();check(h.api.ready&&h.resident.has(id(1))&&h.resident.has(id(3)),'new active scope fully published');check(!h.ledger.evictions.includes(id(1)),'overlapping active cell never evicted');h.api.dispose();check(!h.api.ready,'disposed cannot report ready');
}
{
 const h=harness();h.select([id(0)]);for(let n=0;n<30&&!h.ledger.partial;n++)await h.advance();check(h.ledger.partial===124&&!h.api.ready,'partial allocation is not drawable readiness');h.select([id(1)],[],'turn');check(h.ledger.partial===0&&h.ledger.disposed===1,'superseded partial upload released');await h.drain();check(!h.resident.has(id(0))&&h.resident.has(id(1)),'stale upload never publishes');h.api.dispose();
}
{
 let resolve;const waiting=new Promise(r=>resolve=r);let first=true;const h=harness({prepare:async c=>{if(first){first=false;await waiting;}return{id:c.id};}});h.select([id(0)]);await h.advance();check(h.stats.pendingBytes>0,'inflight producer admitted');h.select([id(1)],[],'teleport');check(h.ledger.signals[0].aborted,'stale source fetch aborted');check(h.ledger.prepare.length===1,'backpressure retains one producer until old owner releases');resolve();await h.drain();check(!h.ledger.published.includes(id(0))&&h.ledger.published.includes(id(1)),'late successful old fetch cannot publish');h.api.dispose();
}
{
 let reject;const waiting=new Promise((_,r)=>reject=r);const h=harness({prepare:()=>waiting});h.select([id(0)]);await h.advance();h.api.dispose();check(h.ledger.signals[0].aborted,'context disposal aborts fetch');reject(Error('transport rejects after context loss'));await h.drain();check(h.ledger.errors.length===0&&h.ledger.published.length===0,'cancelled failure does not revive context or publish');
}
for(const failure of ['prepare','upload']){const h=harness(failure==='prepare'?{prepare:async()=>{throw Error('prepare failure');}}:{failStep:true});h.select([id(0)]);await h.drain();check(!!h.api.error&&!h.api.ready,'failure blocks fresh readiness');check(h.ledger.errors.length===1,'failure reported once');check(h.ledger.partial===0&&h.stats.pendingBytes===0,'failure releases pending ownership');h.api.dispose();}
{
 const h=harness({config:{capacityBytes:200}});assert.throws(()=>h.select([id(0),id(1)]),/exceeds|capacity/);checks++;check(!h.api.ready&&!!h.api.error,'oversized active set fails closed');h.api.dispose();
}
{
 const h=harness({config:{maxQueueBytes:159}});assert.throws(()=>h.select([id(0)]),/queue byte cap/);checks++;await h.drain();check(h.ledger.prepare.length===0,'oversized descriptor byte queue never starts');check(h.stats.queuedBytes<=159,'reported descriptor queue bytes remain bounded');h.api.dispose();
}
const result={passed:true,checks,sourceRoot:root,sourceSha256:crypto.createHash('sha256').update(source).digest('hex'),scope:'Actual scheduler plus independently owned texture/geometry/partial allocation ledger and adversarial asynchronous producers. Covers publication readiness, active eviction, byte/count/transient caps, stale fetch/upload cancellation, context disposal and failures. Synthetic uploads; no actual GPU allocation, pixels, total VRAM, timing or FPS proof.'};fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
