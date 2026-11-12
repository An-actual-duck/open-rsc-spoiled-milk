import {frozenBrowserModules} from '../browser-source-import.mjs';
/** Independent aggregate-tick and cache-horizon oracle; real uploader, simulated GL/RAF. */
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';import assert from 'node:assert/strict';
const args=process.argv.slice(2),option=n=>{const i=args.indexOf(n);if(i<0||!args[i+1])throw Error('Required '+n);return path.resolve(args[i+1]);};
const root=option('--source-root'),out=option('--out'),web=path.join(root,'tools/browser-gameplay/web');
const names=['browser-bounded-residency.js','browser-bounded-upload.js','browser-whole-zone.js','browser-whole-zone-policy.js'];
const sources=Object.fromEntries(names.map(n=>[n,fs.readFileSync(path.join(web,n),'utf8')])),data=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
const frozen=frozenBrowserModules(web);
const {createBoundedResidency}=await import(frozen.url('browser-bounded-residency.js'));const {createBoundedChunkUpload}=await import(frozen.url('browser-bounded-upload.js'));
let checks=0;const check=(v,label)=>{assert(v,label);checks++;};
const id=n=>1000000+n,geometryBytes=124,workBytes=268;
const chunks=Array.from({length:32},(_,n)=>({id:id(n),role:n%2,vertexCount:1,indexCount:3,batchIntCount:5,bytes:160}));
function packed(c){const b=new ArrayBuffer(c.bytes);new Uint32Array(b,0,4).set([0x575a4331,1,3,5]);new Float32Array(b,16,28).set([2,3,4]);new Int32Array(b,128,3).set([0,0,0]);new Int32Array(b,140,5).set([7,0,3,1,0]);return b;}
async function flush(){for(let n=0;n<250;n++)await Promise.resolve();}
function harness(options={}){
 const stats={},resident=new Map(),timers=new Map(),buffers=new Map(),objects=new Set();let token=0,objectId=0,time=0,vao={external:true},array={external:true},api;
 const ledger={partial:0,published:[],evicted:[],prepared:[],admissions:[],signals:[],constructors:[],steps:[],driverBytes:0,disposed:0,required:new Set(),tick:0};
 const clock=()=>time,total=()=>40+ledger.partial+[...resident.values()].reduce((n,c)=>n+c.bytes,0);
 const gl={VERTEX_ARRAY_BINDING:1,ARRAY_BUFFER_BINDING:2,ARRAY_BUFFER:3,ELEMENT_ARRAY_BUFFER:4,STATIC_DRAW:5,FLOAT:6};
 gl.getParameter=p=>p===1?vao:array;gl.bindVertexArray=v=>{vao=v;};gl.bindBuffer=(target,b)=>{if(target===3)array=b;else vao.element=b;};
 for(const kind of ['VertexArray','Buffer']){gl['create'+kind]=()=>{const v={id:++objectId};objects.add(v);return v;};gl['delete'+kind]=v=>{assert(objects.delete(v),'resource released twice');buffers.delete(v);};}
 gl.bufferData=(target,n)=>buffers.set(target===3?array:vao.element,new Uint8Array(n));
 gl.bufferSubData=(target,at,bytes)=>{buffers.get(target===3?array:vao.element).set(bytes,at);ledger.driverBytes+=bytes.byteLength;if(options.glOverrun)time+=options.glOverrun;};
 gl.getAttribLocation=()=>0;gl.enableVertexAttribArray=gl.vertexAttribPointer=()=>{};
 api=createBoundedResidency({catalog:{chunks},stats,capacityBytes:40+32*geometryBytes,maxChunks:32,maxPendingBytes:256,maxQueue:32,maxQueueBytes:32*160,sliceBytes:4*1024*1024,optionalReserveBytes:0,tickMilliseconds:2,maxTickSteps:32,clock,resident,totalBytes:total,totalCount:()=>resident.size+(ledger.partial?1:0),
  schedule:fn=>{timers.set(++token,fn);return token;},cancelSchedule:t=>timers.delete(t),
  evict:n=>{assert(!ledger.required.has(n),'required active resource evicted');ledger.evicted.push(n);resident.delete(n);},
  prepare:async(c,signal)=>{ledger.prepared.push(c.id);ledger.admissions.push({id:c.id,tick:ledger.tick});ledger.signals.push(signal);time+=options.prepareCost||0;return options.prepare?options.prepare(c,signal):packed(c);},
  createUpload:(c,buffer)=>{ledger.constructors.push({id:c.id,tick:ledger.tick,time});time+=options.constructorCost||0;const job=createBoundedChunkUpload(gl,{chunk:c,buffer,program:{},attributes:[['aPosition',3,0]],check(){},reserve:n=>{assert(total()+n<=stats.capacityBytes,'allocation exceeds real ledger');},allocated:n=>{ledger.partial+=n;},released:n=>{ledger.partial-=n;},publish:value=>{resident.set(c.id,value);ledger.partial-=value.bytes;ledger.published.push(c.id);}});
   return{step:limit=>{const before=time;const done=job.step(limit);time+=options.stepCost||0;ledger.steps.push({tick:ledger.tick,id:c.id,bytes:job.lastStepBytes,limit,before,after:time});return done;},dispose:()=>{job.dispose();ledger.disposed++;},get lastStepBytes(){return job.lastStepBytes;},get allocatedBytes(){return job.allocatedBytes;}};},...options.config});
 const select=(required,optional=[],generation='source-A')=>{ledger.required=new Set(required);return api.select(required,optional,generation);};
 const frame=async()=>{const item=timers.entries().next().value;if(!item)return false;timers.delete(item[0]);ledger.tick++;item[1]();await flush();return true;};
 const drain=async()=>{for(let n=0;n<300;n++){await flush();if(!timers.size&&stats.pendingBytes===0)return;await frame();}throw Error('bounded pipeline did not drain');};
 return {api,stats,resident,ledger,timers,select,frame,drain,get time(){return time;},set time(n){time=n;}};
}
function aggregate(h){for(let t=1;t<=h.ledger.tick;t++){const rows=h.ledger.steps.filter(s=>s.tick===t),constructors=h.ledger.constructors.filter(s=>s.tick===t).length,admissions=h.ledger.admissions.filter(s=>s.tick===t).length;check(rows.reduce((n,s)=>n+s.bytes,0)<=h.stats.sliceBytes,'aggregate RAF bytes cap');check(rows.length+constructors+admissions<=h.stats.maxTickSteps,'independent admission+construction+upload step cap');}check(h.stats.maxTickBytes<=h.stats.sliceBytes,'reported aggregate bytes bounded');check(h.stats.maxObservedTickSteps<=h.stats.maxTickSteps,'reported steps bounded');}
{
 const h=harness();h.select(chunks.slice(0,14).map(c=>c.id));await h.frame();check(h.ledger.published.length>=3,'tiny resources progress across stages and immediate sources in one RAF');await h.drain();check(h.api.ready&&h.ledger.published.length===14,'all required resources fully published');check(h.ledger.tick<=5,'tiny-resource throughput does not spend six RAFs per resource');check(h.ledger.driverBytes===14*geometryBytes,'actual GPU-double transfer byte count exact');check(h.stats.workBytes===14*workBytes,'actual uploader validation/copy/transfer bytes charged once');aggregate(h);h.api.dispose();
}
for(const config of [{sliceBytes:224},{maxTickSteps:2}]){
 const h=harness({config});h.select([id(0),id(1)]);await h.drain();check(h.api.ready,'small aggregate budget progresses');aggregate(h);if(config.maxTickSteps)check(h.stats.maxObservedTickSteps===2,'step limit actually reached');h.api.dispose();
}
{
 const h=harness({stepCost:.75});h.select([id(0),id(1)]);await h.drain();check(h.api.ready,'time-exhausted slices eventually ready');check(h.stats.maxTickMilliseconds<=2.75,'no next step starts after budget exhaustion');check(h.stats.tickOverruns>0,'non-preemptible time overrun reported');aggregate(h);h.api.dispose();
}
{
 const h=harness({glOverrun:10});h.select([id(0)]);await h.drain();check(h.api.ready,'single GL overrun eventually completes');check(h.stats.tickOverruns>=1&&h.stats.maxTickOverrunMilliseconds>=8,'driver overrun retained rather than clamped');const spikes=h.ledger.steps.filter(s=>s.after-s.before>=10);check(spikes.length===2&&spikes.every(s=>h.ledger.steps.filter(x=>x.tick===s.tick).at(-1)===s),'unpreemptible GL step ends tick before any further step');h.api.dispose();
}
for(const options of [{constructorCost:7},{prepareCost:7}]){
 const h=harness(options);h.select([id(0)]);await h.frame();check(h.ledger.steps.length===0,'expensive source admission or constructor ends tick before upload');check(h.stats.maxTickMilliseconds===7&&h.stats.maxTickOverrunMilliseconds===5,'source/constructor overrun is reported');await h.drain();check(h.api.ready,'construction/admission time exhaustion resumes on a scheduled budget');aggregate(h);h.api.dispose();
}
{
 let resolve;const waiting=new Promise(r=>resolve=r);let first=true;const h=harness({prepare:async c=>{if(first){first=false;await waiting;}return packed(c);}});h.select([id(0)]);await h.frame();check(h.ledger.published.length===0&&h.ledger.constructors.length===0,'pending source owns no upload work');check(h.timers.size===0,'waiting source does not churn RAFs');h.time=100;resolve();await flush();check(h.stats.ticks===1&&h.ledger.constructors.length===0,'delayed completion cannot create hidden tick or out-of-tick uploader');await h.drain();check(h.api.ready&&h.stats.ticks>=2,'delayed work uses new scheduled budget');h.api.dispose();
}
{
 let resolve;const waiting=new Promise(r=>resolve=r);let first=true;const h=harness({prepare:async c=>{if(first){first=false;await waiting;}return packed(c);}});h.select([id(0)]);await h.frame();h.time=100;resolve();await flush();check(h.stats.pendingBytes>0&&h.ledger.constructors.length===0,'delayed prepared payload keeps pending ownership');h.select([id(1)],[],'new-required');await h.drain();check(!h.ledger.constructors.some(c=>c.id===id(0))&&h.resident.has(id(1)),'replan drops prepared payload before construction');h.api.dispose();
}
{
 const h=harness({config:{maxTickSteps:1}});h.select([id(0)]);for(let n=0;n<15&&!h.ledger.partial;n++)await h.frame();check(h.ledger.partial>0&&!h.api.ready,'incomplete real uploader allocated before replan');h.select([id(1)],[],'source-B');check(h.ledger.partial===0&&h.ledger.disposed>0,'replan releases cancelled partial GL ownership');await h.drain();check(!h.resident.has(id(0))&&h.resident.has(id(1)),'stale required upload never publishes');h.api.dispose();
}
{
 let resolve;const waiting=new Promise(r=>resolve=r);let first=true;const h=harness({prepare:async c=>{if(first){first=false;await waiting;}return packed(c);}});h.select([id(0)],[id(1)]);await h.frame();const generation=h.stats.generation;h.select([id(0)],[id(2)]);check(h.stats.generation===generation&&!h.ledger.signals[0].aborted,'optional-only plan change preserves required producer generation');h.select([id(3)],[],'teleport');check(h.ledger.signals[0].aborted,'required replan aborts pending source');resolve();await h.drain();check(!h.ledger.constructors.some(c=>c.id===id(0))&&h.resident.has(id(3)),'late old source cannot construct or publish');h.api.dispose();
}
{
 const h=harness({config:{capacityBytes:40+3*geometryBytes,maxChunks:3}});
 for(let center=0;center<12;center++){
  const required=[id(center)],optional=[id(center+1),id(center+2)];h.select(required,optional,'source-'+center);await h.drain();check(h.api.ready,'traversal active set stays ready');check(optional.every(n=>h.resident.has(n)),'moved horizon replaces stale optional residents');check(h.resident.size===3,'traversal allocation count bounded');
  const prepared=h.ledger.prepared.length,evicted=h.ledger.evicted.length;for(let n=0;n<8;n++)h.select(required,optional,'source-'+center);await h.drain();check(h.ledger.prepared.length===prepared&&h.ledger.evicted.length===evicted,'fixed same plan does not optional-on-optional churn');
 }
 check(h.ledger.evicted.length>=10,'sustained traversal actually recycles optional horizon');h.api.dispose();
}
{
 const h=harness({config:{capacityBytes:40+3*geometryBytes,maxChunks:3}});h.select([id(0)],chunks.slice(1,12).map(c=>c.id));await h.drain();const count=h.ledger.prepared.length;for(let n=0;n<10;n++)h.select([id(0)],chunks.slice(1,12).map(c=>c.id));await h.drain();check(h.ledger.prepared.length===count&&h.ledger.evicted.length===0,'oversize optional proposal admits stable nonchurning working set');h.select([id(0)],[id(20),id(21)]);await h.drain();check(h.resident.has(id(0))&&h.resident.has(id(20))&&h.resident.has(id(21)),'same-generation horizon refresh evicts truly stale optionals');h.api.dispose();
}
{
 const h=harness({config:{capacityBytes:40+3*geometryBytes,maxChunks:3,maxTickSteps:1}});h.select([id(0)]);await h.drain();h.select([id(0)],[id(1)]);for(let n=0;n<20&&!h.ledger.partial;n++)await h.frame();check(h.ledger.partial===124&&h.resident.has(id(0)),'actual optional partial upload owns the only reclaimable allocation');h.api.reclaim(248,2);check(h.ledger.partial===0&&!h.resident.has(id(1))&&h.resident.has(id(0)),'local allocation can release dispensable partial optional while active remains owned');h.resident.set(42,{bytes:124});h.resident.set(43,{bytes:124});await h.drain();check(h.resident.size===3&&h.resident.has(42)&&h.resident.has(43),'cancelled partial optional does not later publish over local pressure');h.api.dispose();
}
{
 const h=harness({config:{capacityBytes:40+3*geometryBytes,maxChunks:3,maxTickSteps:1}});h.select([id(0)]);for(let n=0;n<20&&!h.ledger.partial;n++)await h.frame();check(h.ledger.partial===124,'required partial allocation exercised');assert.throws(()=>h.api.reclaim(372,3),/no room|capacity/);checks++;check(h.ledger.partial===124&&h.ledger.disposed===0,'required partial upload never cancelled for local pressure');await h.drain();check(h.api.ready&&h.resident.has(id(0)),'protected required partial still completes');h.api.dispose();
}
const result={passed:true,checks,dependencySha256:frozen.hashes,sourceRoot:root,sourceSha256:Object.fromEntries(Object.entries(sources).map(([n,s])=>[n,crypto.createHash('sha256').update(s).digest('hex')])),scope:'Actual scheduler + actual incremental packed-resource uploader with independent byte/allocation ledger, synthetic sources, deterministic RAF/clock and GL double. Aggregate timing includes injected nonpreemptible overruns. No GPU execution, browser/visuals, real timing or FPS acceptance.'};fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));
