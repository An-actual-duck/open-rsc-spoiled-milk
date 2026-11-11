// Independent behavioral oracle. Requires detached, exact-source ES modules.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {pathToFileURL} from 'node:url';
const root=process.argv[2];
const P=await import(pathToFileURL(root+'/browser-render-worker-protocol.js'));
const {createRenderWorkerTransport}=await import(pathToFileURL(root+'/browser-render-worker-transport.js'));
const {createBrowserRenderWorker}=await import(pathToFileURL(root+'/browser-render-worker.js'));
const {createRenderWorkerReplay,createWorkerOutputSizer}=await import(pathToFileURL(root+'/browser-render-worker-replay.js'));
const {createWorkerStatsSnapshot,WORKER_SAMPLE_FIELDS}=await import(pathToFileURL(root+'/browser-render-worker-stats.js'));
let checks=0;function ok(v,m){assert.ok(v,m);checks++;}function eq(a,b,m){assert.deepEqual(a,b,m);checks++;}
const tick=()=>new Promise(r=>setImmediate(r));
const layout=()=>({epoch:1,width:1843,height:1037,dpr:1});
function fake(options={}){const sent=[];const t=createRenderWorkerTransport({layout,postMessage:(p,x)=>{sent.push(structuredClone(p,{transfer:x}));},...options});return {t,sent};}
function ack(p,more={}){const buffers=p.commands.flatMap(c=>c.args.filter(v=>ArrayBuffer.isView(v)).map(v=>v.buffer));const result={type:'ack',version:1,id:p.id,frameId:p.frameId,layoutEpoch:p.layout.epoch,result:4096,buffers,...more};return structuredClone(result,{transfer:buffers});}
function command(name,args){return P.ownRenderCommand(name,args).command;}
function batch(commands,more={}){return {version:1,type:'batch',id:1,frameId:0,layout:layout(),commands,bytes:commands.reduce((n,c)=>n+P.inspectRenderCommand(c.name,c.args).bytes,0),...more};}
function backend(){const calls=[];const natives={};for(const name of Object.keys(P.RENDER_WORKER_SCHEMA)){if(name[0]!=='$')natives[P.renderNativeName(name)]=async(_,...args)=>{calls.push([name,args]);return name==='initialize'?8192:undefined;};}return {calls,natives,dispose(){calls.push(['dispose']);}};}
// Raw-bit oracle uses byte equality, including deliberately noncanonical float NaNs.
{
 const storage=new ArrayBuffer(48),words=new Uint32Array(storage);words.set([0x12345678,0x7fc01234,0x80000000,0xffc0abcd,0x3f800000,0x89abcdef]);
 const source=new Float32Array(storage,4,4),wanted=Array.from(new Uint8Array(storage,4,16));
 Object.defineProperties(source,{buffer:{get(){throw Error('own buffer getter');}},byteOffset:{value:0},byteLength:{value:0},length:{value:999999}});
 const own=P.ownRenderCommand('lightingFrame',[source]);eq(Array.from(new Uint8Array(own.command.args[0].buffer)),wanted,'intrinsic offset/raw bits');
 words.fill(0);eq(Array.from(new Uint8Array(own.command.args[0].buffer)),wanted,'mutation after capture');
 const cloned=structuredClone(own.command,{transfer:own.transfers});eq(storage.byteLength,48,'borrowed buffer not detached');eq(own.command.args[0].byteLength,0,'only owned copy transferred');eq(Array.from(new Uint8Array(cloned.args[0].buffer)),wanted,'transfer preserves bits');
}
{
 const source=new Int32Array([0x12345678,-1,0,0x7fffffff]);const own=P.ownRenderCommand('deleteChunks',[source.subarray(1,3)]);source.fill(33);eq(Array.from(own.command.args[0]),[-1,0],'integer source offset');
 assert.throws(()=>P.ownRenderCommand('lightingFrame',[new Int32Array(4)]));checks++;
 assert.throws(()=>P.inspectRenderCommand('unknown',[]));checks++;
 assert.throws(()=>P.inspectRenderCommand('lightingFrame',[new Float32Array(new SharedArrayBuffer(16))]));checks++;
}
// Slot oracle holds worker completions. Successful postMessage is never presentation.
{
 const {t,sent}=fake();await t.enqueue('holdWorld',[]);await t.enqueue('holdWorld',[]);eq(sent.length,2);eq(t.stats.completedFrames,0);eq(t.stats.maxInFlight,2);
 let third=false;const waiting=t.enqueue('holdWorld',[]).then(()=>third=true);await tick();eq(sent.length,2);eq(third,false,'third packet blocked');
 t.receive(ack(sent[0]));await waiting;eq(sent.length,3);eq(t.stats.completedFrames,1,'only actual ACK counted');
 t.receive(ack(sent[1]));t.receive(ack(sent[2]));eq(t.stats.completedFrames,3);eq(t.stats.inFlightBytes,0);t.dispose();
}
// Explicit result barrier must not resolve from submission or a malformed identity.
{
 const {t,sent}=fake();let resolved=false;const result=t.enqueue('initialize',['#version 120 v','#version 120 f']).then(x=>{resolved=true;return x;});await tick();eq(resolved,false);t.receive(ack(sent[0],{result:8192}));eq(await result,8192);t.dispose();
}
// Resource order oracle, independent of any GPU implementation or resource cache.
{
 const b=backend(),s=createWorkerOutputSizer(),r=createRenderWorkerReplay({backend:b,outputSizer:s});
 const packet=batch([command('uploadTexture',[4,1,1,new Int32Array([123]),false]),command('deleteTextures',[new Int32Array([4])]),command('uploadTexture',[4,1,1,new Int32Array([456]),false]),command('holdWorld',[])],{frameId:1});
 const completion=await r.execute(structuredClone(packet));eq(b.calls.map(x=>x[0]),['uploadTexture','deleteTextures','uploadTexture','holdWorld']);eq(completion.frameId,1);eq(completion.layoutEpoch,1);
 const size=s.size(960,540);eq([size.width,size.height],[1843,1037],'physical sizing uses explicit DOM layout and legacy aspect rounding');r.dispose();
}
// Entire batch malformed at its END must produce zero backend prefix side effects.
{
 const b=backend(),r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});const packet=batch([command('deleteChunks',[new Int32Array([5])])]);packet.commands.push({name:'lightingFrame',args:[new Int32Array(4)]});
 await assert.rejects(r.execute(packet));checks++;eq(b.calls,[]);await assert.rejects(r.execute(batch([command('initializeSky',[])])));checks++;eq(b.calls,[]);r.dispose();
}
// Backend failure prevents a false ACK and permanently poisons subsequent packets.
{
 const b=backend();b.natives[P.renderNativeName('initializeSky')]=async()=>{throw Error('backend fault');};const r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});await assert.rejects(r.execute(batch([command('initializeSky',[])])));checks++;await assert.rejects(r.execute(batch([command('holdWorld',[])],{id:2,frameId:1})));checks++;r.dispose();
}
// Disposal wakes every unresolved result and blocked slot producer.
{
 const {t,sent}=fake();const a=t.enqueue('initialize',['v','f']);a.catch(()=>{});await tick();await t.enqueue('holdWorld',[]);const blocked=t.enqueue('holdWorld',[]);blocked.catch(()=>{});await tick();eq(sent.length,2);t.dispose();await assert.rejects(a);checks++;await assert.rejects(blocked);checks++;eq(t.stats.completedFrames,0);eq(t.stats.inFlightBytes,0);
}
// Shared source aliases become separately owned planes; opaque login T stays null.
{
 const storage=new ArrayBuffer(32),i=new Int32Array(storage),f=new Float32Array(storage);i.set([0x80000000,0x7fc01234,0x11223344,7]);
 const own=P.ownRenderCommand('present',[960,540,false,f.subarray(0,4),f.subarray(1,3),i.subarray(0,2),i.subarray(2,4)]);
 const before=own.command.args.slice(3).map(v=>Array.from(new Uint8Array(v.buffer)));i.fill(0);eq(own.command.args.slice(3).map(v=>Array.from(new Uint8Array(v.buffer))),before);eq(new Set(own.transfers).size,4,'no owned transfer aliases');eq(storage.byteLength,32);
 const opaque=P.ownRenderCommand('overlayFrame',[960,540,0,1,1,0,false,null]);eq(opaque.command.args[7],null);eq(opaque.transfers,[]);
}
// Caps are validated on command count before a malformed suffix can be dispatched.
{
 const b=backend(),r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});const commands=Array.from({length:1025},()=>({name:'initializeSky',args:[]}));
 await assert.rejects(r.execute({version:1,type:'batch',id:1,frameId:0,layout:layout(),commands,bytes:0}));checks++;eq(b.calls,[]);r.dispose();
 const {t,sent}=fake();for(let n=0;n<1024;n++)t.enqueue('initializeSky',[]);assert.throws(()=>t.enqueue('initializeSky',[]));checks++;eq(sent,[]);eq(t.stats.stagedBytes,0);assert.throws(()=>t.enqueue('initializeSky',[]));checks++;t.dispose();
}
// Diagnostic callback failure and invalid layout must settle every outstanding result.
{
 const {t,sent}=fake({onStats(){throw Error('diagnostics fault');}});const p=t.enqueue('initialize',['v','f']);p.catch(()=>{});await tick();t.receive(ack(sent[0]));await assert.rejects(p);checks++;eq(t.stats.completedPackets,0);eq(t.stats.inFlightBytes,0);assert.throws(()=>t.enqueue('initializeSky',[]));checks++;t.dispose();
 const x=fake({layout:()=>({epoch:1,width:NaN,height:3,dpr:1})});await assert.rejects(x.t.enqueue('holdWorld',[]));checks++;eq(x.sent,[]);eq(x.t.stats.stagedBytes,0);assert.throws(()=>x.t.enqueue('initializeSky',[]));checks++;x.t.dispose();
}
// Worker fatal/post failure clears staged planes and rejects held result barriers.
{
 const {t,sent}=fake();const p=t.enqueue('initialize',['v','f']);p.catch(()=>{});await tick();t.enqueue('lightingFrame',[new Float32Array([1,2,3])]);t.receive({type:'fatal',error:'context lost'});await assert.rejects(p);checks++;eq(t.stats.stagedBytes,0);eq(t.stats.inFlightBytes,0);eq(t.stats.completedPackets,0);t.receive(ack(sent[0]));eq(t.stats.completedPackets,0);t.dispose();
 const x=fake({postMessage(){throw Error('post fault');}});await assert.rejects(x.t.enqueue('holdWorld',[]));checks++;eq(x.t.stats.completedFrames,0);eq(x.t.stats.inFlightBytes,0);x.t.dispose();
}
// An actual byte-copy failure is terminal, with no later flush of staged prefix.
{
 const {t}=fake();const pending=t.enqueue('initialize',['v','f']);pending.catch(()=>{});await tick();t.enqueue('lightingFrame',[new Float32Array([8])]);
 const set=Uint8Array.prototype.set;try{Uint8Array.prototype.set=function(){throw Error('injected copy fault');};assert.throws(()=>t.enqueue('lightingFrame',[new Float32Array([9])]),/copy fault/);checks++;}finally{Uint8Array.prototype.set=set;}
 await assert.rejects(pending);checks++;eq(t.stats.stagedBytes,0);eq(t.stats.inFlightBytes,0);assert.throws(()=>t.enqueue('initializeSky',[]));checks++;t.dispose();
}
// Layout epochs are ordered and immutable. Same epoch geometry change rejects prefix.
{
 const b=backend(),r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});await r.execute(batch([command('initializeSky',[])]));const count=b.calls.length;
 await assert.rejects(r.execute(batch([command('initializeSky',[])],{id:2,layout:{...layout(),width:1842}})));checks++;eq(b.calls.length,count);r.dispose();
 const b2=backend(),s=createWorkerOutputSizer(),r2=createRenderWorkerReplay({backend:b2,outputSizer:s});await r2.execute(batch([command('initialize',['v','f'])]));await r2.execute(batch([command('holdWorld',[])],{id:2,frameId:1,layout:{epoch:2,width:1201,height:677,dpr:1}}));eq([s.size(960,540).width,s.size(960,540).height],[1203,677]);r2.dispose();
}
// World presentation is one packet completed only by subsequent frameWork.
{
 const {t,sent}=fake();t.enqueue('present',[960,540,true,new Float32Array(16),new Float32Array(),new Int32Array(),new Int32Array()]);eq(sent.length,0);await assert.rejects(t.drain());checks++;await t.enqueue('frameWork',[new Float32Array(14),new Int32Array()]);eq(sent.length,1);eq(sent[0].commands.map(c=>c.name),['present','frameWork']);eq(t.stats.completedFrames,0);t.receive(ack(sent[0]));await t.drain();eq(t.stats.completedFrames,1);t.dispose();
}
// Exact ACK identities: stale/foreign/frame/layout mistakes never release/count slots.
for(const mutate of [x=>({...x,version:99}),x=>({...x,id:2}),x=>({...x,frameId:4}),x=>({...x,layoutEpoch:2}),x=>({...x,type:'queued'})]){
 const {t,sent}=fake();const p=t.enqueue('initialize',['v','f']);p.catch(()=>{});await tick();t.receive(mutate(ack(sent[0])));await assert.rejects(p);checks++;eq(t.stats.completedPackets,0);eq(t.stats.inFlightBytes,0);t.dispose();
}
{
 const {t,sent}=fake();await t.enqueue('holdWorld',[]);t.receive(ack(sent[0]));eq(t.stats.completedFrames,1);t.receive(ack(sent[0]));eq(t.stats.completedFrames,1);assert.throws(()=>t.enqueue('initializeSky',[]));checks++;t.dispose();
}
// Aggregate payload over cap rejects before any copy; aliases do not evade total cost.
{
 const data=new ArrayBuffer(17*1024*1024),f=new Float32Array(data),i=new Int32Array(data);const set=Uint8Array.prototype.set;let copied=false;
 try{Uint8Array.prototype.set=function(...a){copied=true;return set.apply(this,a);};assert.throws(()=>P.ownRenderCommand('present',[960,540,true,f,f,i,i]),/cap/);checks++;}finally{Uint8Array.prototype.set=set;}
 eq(copied,false);eq(data.byteLength,17*1024*1024);
}
// Last missing endpoint preflight also protects a valid earlier resource prefix.
{
 const b=backend();delete b.natives[P.renderNativeName('initializeSky')];const r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});await assert.rejects(r.execute(batch([command('deleteChunks',[new Int32Array([1])]),command('initializeSky',[])])));checks++;eq(b.calls,[]);r.dispose();
}
// Returned transport storage is reused only after ACK and fully overwritten.
{
 const {t,sent}=fake({onReturnedBuffers:buffers=>{for(const b of buffers)new Uint8Array(b).fill(0xa5);}});
 const source=new Float32Array([1,-0,3,4]);t.enqueue('lightingFrame',[source]);await t.enqueue('holdWorld',[]);eq(t.stats.poolBytes,0);eq(t.stats.reusedBuffers,0);const first=sent[0];t.receive(ack(first));eq(t.stats.poolBytes,16);eq(first.commands[0].args[0].byteLength,0,'returned fake-worker input detached');eq(source.byteLength,16);
 source.set([9,8,7,6]);t.enqueue('lightingFrame',[source]);await t.enqueue('holdWorld',[]);eq(t.stats.reusedBuffers,1);eq(t.stats.poolBytes,0);eq(Array.from(sent[1].commands[0].args[0]),[9,8,7,6],'reused poison bytes fully overwritten');eq(source.byteLength,16);t.receive(ack(sent[1]));eq(t.stats.returnedBuffers,2);eq(t.mutateReturnedBuffers(),16);t.dispose();eq(t.stats.poolBytes,0);
}
// Missing, wrong-size, duplicate, shared or forged returned buffers fail before ACK/pooling.
for(const mutate of [x=>({...x,buffers:[]}),x=>({...x,buffers:[new ArrayBuffer(8),new ArrayBuffer(4)]}),x=>{const a=new ArrayBuffer(4);return {...x,buffers:[a,a]};},x=>({...x,buffers:[new SharedArrayBuffer(4),new ArrayBuffer(4)]}),x=>({...x,buffers:[{},new ArrayBuffer(4)]})]){
 const {t,sent}=fake();t.enqueue('skyFrame',[new Float32Array(1),new Float32Array(1)]);const p=t.flush({result:true});p.catch(()=>{});await tick();t.receive(mutate(ack(sent[0])));await assert.rejects(p);checks++;eq(t.stats.poolBytes,0);eq(t.stats.completedPackets,0);eq(t.stats.inFlightBytes,0);t.dispose();
}
// Pool entry cap remains bounded independently of packet/producer byte caps.
{
 const {t,sent}=fake();for(let n=0;n<512;n++)t.enqueue('uploadSkyGeometry',[new Float32Array(1),new Int32Array(1),1]);await t.enqueue('holdWorld',[]);t.receive(ack(sent[0]));eq(t.stats.poolBytes,4096);eq(t.stats.returnedBuffers,1024);
 t.enqueue('skyFrame',[new Float32Array(2),new Float32Array(2)]);await t.enqueue('holdWorld',[]);t.receive(ack(sent[1]));eq(t.stats.poolBytes,4104,'recent buffers replace cold entries at1024entry cap');eq(t.stats.poolEvictions,2);t.dispose();
}
// Executor completion is after frameWork finishes, not after present was queued.
{
 const b=backend();let release,completed=false;const gate=new Promise(r=>release=r);b.natives[P.renderNativeName('frameWork')]=async(_,...args)=>{b.calls.push(['frameWork',args]);await gate;};const r=createRenderWorkerReplay({backend:b,outputSizer:createWorkerOutputSizer()});
 const counters=new Int32Array([0x7fffffff,-1]),work=new Float32Array(14);new Uint32Array(work.buffer).set([0x80000000,0x7fc01234,0x3f800001]);
 const packet=batch([command('present',[960,540,true,new Float32Array(16),new Float32Array(),new Int32Array(),new Int32Array()]),command('frameWork',[work,counters])],{frameId:1});const result=r.execute(packet).then(x=>{completed=true;return x;});await tick();eq(completed,false);eq(b.calls.map(x=>x[0]),['present','frameWork']);eq(Array.from(new Uint32Array(b.calls[1][1][0].buffer)),Array.from(new Uint32Array(work.buffer)));eq(Array.from(b.calls[1][1][1]),[0x7fffffff,-1]);release();eq((await result).frameId,1);r.dispose();
}
// LRU admission evicts cold geometry, retaining hot frame planes for actual reuse.
{
 const {t,sent}=fake();t.enqueue('lightingFrame',[new Float32Array(4*1024*1024)]);await t.enqueue('holdWorld',[]);t.receive(ack(sent[0]));eq(t.stats.poolBytes,16*1024*1024);
 t.enqueue('lightingFrame',[new Float32Array(512*1024)]);await t.enqueue('holdWorld',[]);t.receive(ack(sent[1]));eq(t.stats.poolBytes,2*1024*1024);eq(t.stats.poolEvictions,1);const reused=t.stats.reusedBuffers;t.enqueue('lightingFrame',[new Float32Array(512*1024)]);await t.enqueue('holdWorld',[]);eq(t.stats.reusedBuffers,reused+1);t.receive(ack(sent[2]));ok(t.stats.peakPoolBytes<=16*1024*1024);t.dispose();
}
// Acquiring from a NONEMPTY bucket refreshes recency before another ACK pressures it.
{
 const {t,sent}=fake();t.enqueue('lightingFrame',[new Float32Array(1)]);t.enqueue('lightingFrame',[new Float32Array(1)]);for(let n=0;n<511;n++)t.enqueue('skyFrame',[new Float32Array(2),new Float32Array(2)]);await t.enqueue('holdWorld',[]);t.receive(ack(sent[0]));eq(t.stats.returnedBuffers,1024);
 t.enqueue('skyFrame',[new Float32Array(3),new Float32Array(3)]);await t.enqueue('holdWorld',[]);t.enqueue('lightingFrame',[new Float32Array(1)]);const reused=t.stats.reusedBuffers;t.receive(ack(sent[1]));eq(t.stats.poolEvictions,1);t.enqueue('lightingFrame',[new Float32Array(1)]);eq(t.stats.reusedBuffers,reused+1,'remaining hot-size bucket survived eviction');await t.enqueue('holdWorld',[]);t.receive(ack(sent[2]));t.dispose();
}
// Main adapter mock exercises lifecycle only, never OffscreenCanvas/realWorker/GPU.
class FakeWorker{
 constructor(startup='ready'){this.events=new Map();this.sent=[];this.terminated=0;this.startup=startup;}
 addEventListener(k,f){if(!this.events.has(k))this.events.set(k,[]);this.events.get(k).push(f);}
 emit(k,data){for(const f of this.events.get(k)||[])f(k==='message'?{data}:data);}
 postMessage(m){if(m.type===this.throwOnType)throw Error('injected proxy post fault');this.sent.push(m);if(m.type==='init')queueMicrotask(()=>this.startup==='ready'?this.emit('message',{type:'ready',version:1}):this.emit('error',{message:'startup fault'}));}
 terminate(){this.terminated++;}
}
{
 const oldWindow=globalThis.window,oldResize=globalThis.ResizeObserver,windowEvents=new Map();let disconnected=0;
 globalThis.window={devicePixelRatio:1,addEventListener(k,f){windowEvents.set(k,f);},removeEventListener(k,f){if(windowEvents.get(k)===f)windowEvents.delete(k);}};
 globalThis.ResizeObserver=class{observe(){}disconnect(){disconnected++;}};
 const canvas={transferControlToOffscreen(){return {};},getBoundingClientRect(){return {width:1843,height:1037};}};
 try{
  const worker=new FakeWorker();const view=await createBrowserRenderWorker(canvas,()=>{},{workerFactory:()=>worker,oracle:true});
  const initialized=view.natives[P.renderNativeName('initialize')](null,'v','f');await tick();const packet=worker.sent.find(x=>x.type==='batch');eq(packet.layout,{epoch:1,width:1843,height:1037,dpr:1});worker.emit('message',ack(packet,{result:8192,stats:{value:{},samples:{},timeOrigin:performance.timeOrigin}}));eq(await initialized,8192);
  const read=view.readPixels();read.catch(()=>{});await tick();worker.emit('error',{message:'worker died during readback'});await assert.rejects(read);checks++;view.dispose();eq(windowEvents.size,0);eq(disconnected,1);eq(worker.terminated,1);
  const failed=new FakeWorker('error');await assert.rejects(createBrowserRenderWorker(canvas,()=>{},{workerFactory:()=>failed}));checks++;eq(failed.terminated,1);eq(windowEvents.size,0,'startup failure removes window listeners');eq(disconnected,2,'startup failure disconnects observer');
  const closingWorker=new FakeWorker(),closingView=await createBrowserRenderWorker(canvas,()=>{},{workerFactory:()=>closingWorker,oracle:true});
  let closingState='pending';const closing=closingView.disposeAndConfirm().then(()=>closingState='resolved',()=>closingState='rejected');await tick();closingWorker.emit('error',{message:'worker died while disposing'});await tick();eq(closingState,'rejected','fatal rejects pending dispose confirmation');await closing;closingView.dispose();eq(windowEvents.size,0);
  const postingWorker=new FakeWorker(),postingView=await createBrowserRenderWorker(canvas,()=>{},{workerFactory:()=>postingWorker,oracle:true});postingWorker.throwOnType='readPixels';await assert.rejects(postingView.readPixels());checks++;eq(postingView.stats.backendFailed,true,'readback post error poisons adapter');postingView.dispose();eq(windowEvents.size,0);

 }finally{globalThis.window=oldWindow;globalThis.ResizeObserver=oldResize;}
}
// Closed statistics surface: scalar/object budgets, omission and actual sample pushes.
{
 function stats(extra={}){return Object.assign(Object.fromEntries(WORKER_SAMPLE_FIELDS.map(k=>[k,[]])),extra);}
 const source=stats({javaFrames:3,worldReadiness:{phase:'ready',blocksWorldInput:false},cacheThatMustStayPrivate:{pixels:new Int32Array(1024)},privatePlane:new Float32Array(100)}),snap=createWorkerStatsSnapshot(source);
 const first=snap();eq(first.value.javaFrames,3);eq(first.value.worldReadiness,{phase:'ready',blocksWorldInput:false});ok(!('cacheThatMustStayPrivate'in first.value));ok(!('privatePlane'in first.value));eq(first.samples,{});
 source.frameSamples.push(16,16);eq(snap().samples.frameSamples,[16,16],'equal actual samples preserved');eq(snap().samples,{},'no stale repeated sample');
 for(let n=0;n<5;n++){source.frameSamples.push(n);if(source.frameSamples.length>3)source.frameSamples.shift();}eq(snap().samples.frameSamples,[2,3,4],'full ring only actual retained tail');eq(snap().samples,{});
 for(const extra of [{worldReadiness:{pixels:new Int32Array(2)}},{worldReadiness:{bad:NaN}},{worldReadiness:{s:'x'.repeat(4097)}},{worldReadiness:{arr:Array(17).fill(0)}},{worldReadiness:Object.fromEntries(Array.from({length:65},(_,n)=>['k'+n,n]))}]){const snapshot=createWorkerStatsSnapshot(stats(extra));assert.throws(()=>snapshot());checks++;}
}
// Sample payloads themselves are copied/validated, not a plane/cache escape.
{
 const make=extra=>Object.assign(Object.fromEntries(WORKER_SAMPLE_FIELDS.map(k=>[k,[]])),extra);
 const source=make({measurement:true}),snap=createWorkerStatsSnapshot(source),row=Array.from({length:14},(_,n)=>n);source.javaBridgeWorkSamples.push(row);source.worldFramePolicies.push(true);const output=snap();row.fill(999);eq(output.samples.javaBridgeWorkSamples[0],Array.from({length:14},(_,n)=>n));eq(output.samples.worldFramePolicies,[true]);eq(output.measurementActive,true);
 for(const [key,bad]of [['frameSamples',new Float32Array(10)],['frameSamples',NaN],['frameSamples',-1],['worldFramePolicies',1],['javaBridgeWorkSamples',Array(13).fill(0)],['javaBridgeWorkSamples',new Float32Array(14)],['javaBridgeWorkSamples',Array(14).fill(Infinity)]]){const s=make({}),f=createWorkerStatsSnapshot(s);s[key].push(bad);assert.throws(()=>f());checks++;}
 const big=make(Object.fromEntries(Array.from({length:193},(_,n)=>['f'+n,n])));assert.throws(()=>createWorkerStatsSnapshot(big)());checks++;
 const strings=make(Object.fromEntries(Array.from({length:5},(_,n)=>['s'+n,'x'.repeat(4096)])));assert.throws(()=>createWorkerStatsSnapshot(strings)());checks++;
}
// Execute actual extracted frontend alignment body with independent DOM geometry.
{
 const source=fs.readFileSync(root+'/browser.js','utf8'),start=source.indexOf('function alignWorldSurface(');ok(start>=0);let end=source.indexOf('{',start),depth=1;for(end++;depth&&end<source.length;end++){if(source[end]==='{')depth++;else if(source[end]==='}')depth--;}const body=source.slice(start,end);
 let awt=null,enabled=true,invalidations=0,layoutSets=[],coverUpdates=0;const style={};
 const display={querySelectorAll(){return awt?[canvas,awt]:[canvas];},getBoundingClientRect(){return {left:10,top:20,width:1604,height:1204};}};
 const canvas={style,parentElement:display,getBoundingClientRect(){return {left:10,top:20,width:parseFloat(style.width||0)*2,height:parseFloat(style.height||0)*2};}};
 const renderWorker={get enabled(){return enabled;}},context=vm.createContext({document:{querySelector(){return display;}},canvas,renderWorker,displayLayout:{scale:2,setSize(w,h){layoutSets.push([w,h]);}},gpu:{invalidateLayout(){invalidations++;}},updateWorldCover(){coverUpdates++;},Object});
 vm.runInContext(body+';this.align=alignWorldSurface;',context);context.align(802,602);eq(style,{display:'block',left:'0px',top:'0px',width:'802px',height:'602px'});eq(canvas.getBoundingClientRect().width,1604);eq(invalidations,1);context.align(802,602);eq(invalidations,1,'stable style causes no perframe invalidation');eq(layoutSets,[[802,602],[802,602]]);
 awt={width:1000,height:800,getBoundingClientRect(){return {left:40,top:80,width:1500,height:1200};}};context.align(802,602);eq(style,{display:'block',left:'89.25px',top:'178.5px',width:'601.5px',height:'451.5px'});eq(invalidations,2);context.align(802,602);eq(invalidations,2);eq(canvas.getBoundingClientRect().width,1203);
 enabled=false;context.align(802,602);eq(invalidations,2,'same-thread path never calls worker invalidation');awt=null;const original={...style};context.align(700,450);eq(style,original,'ordinary noAWT behavior unchanged');ok(coverUpdates>=6);
 // Invoke the actual frontend native wrapper, proving alignment precedes backend submission.
 enabled=true;for(const key of Object.keys(style))delete style[key];let atBackend=null;Object.assign(context,{natives:{},presentName:'p',worldCover:{blocksWorldInput:false},publish(){},window:{devicePixelRatio:1.25},present:async(_lib,w,h)=>{atBackend={w,h,rect:canvas.getBoundingClientRect()};return 6;}});context.gpu={stats:{},invalidateLayout(){invalidations++;}};
 const wrapStart=source.lastIndexOf('natives[presentName] = async (...args) => {');ok(wrapStart>=0);let wrapEnd=source.indexOf('{',wrapStart),braces=1;for(wrapEnd++;braces&&wrapEnd<source.length;wrapEnd++){if(source[wrapEnd]==='{')braces++;else if(source[wrapEnd]==='}')braces--;}vm.runInContext(source.slice(wrapStart,wrapEnd)+';',context);
 eq(await context.natives.p(null,802,602),6);eq([atBackend.w,atBackend.h,atBackend.rect.width,atBackend.rect.height],[802,602,1604,1204],'first present non960 has realpositive canvas before backend');awt={width:1000,height:800,getBoundingClientRect(){return {left:40,top:80,width:1500,height:1200};}};await context.natives.p(null,802,602);eq([atBackend.rect.width,atBackend.rect.height],[1203,903],'lateAWT target used before next present');eq(style.top,'178.5px');

}
// Actual adapter must retry a zero initial canvas rect, then cache positive layout.
{
 const oldWindow=globalThis.window;let reads=0,positive=false;globalThis.window={devicePixelRatio:1.25,addEventListener(){},removeEventListener(){}};
 const canvas={transferControlToOffscreen(){return {};},getBoundingClientRect(){reads++;return positive?{width:1604,height:1204}:{width:0,height:0};}};const worker=new FakeWorker();let view;
 try{view=await createBrowserRenderWorker(canvas,()=>{},{workerFactory:()=>worker,oracle:true});const init=view.natives[P.renderNativeName('initialize')](null,'v','f');await tick();let packets=worker.sent.filter(x=>x.type==='batch');eq(packets[0].layout,{epoch:1,width:0,height:0,dpr:1.25});worker.emit('message',ack(packets[0],{stats:{value:{},samples:{}}}));await init;eq(reads,1);
 positive=true;await view.natives[P.renderNativeName('holdWorld')](null);packets=worker.sent.filter(x=>x.type==='batch');eq(packets[1].layout,{epoch:2,width:1604,height:1204,dpr:1.25});worker.emit('message',ack(packets[1],{stats:{value:{},samples:{}}}));eq(reads,2);
 await view.natives[P.renderNativeName('holdWorld')](null);packets=worker.sent.filter(x=>x.type==='batch');worker.emit('message',ack(packets[2],{stats:{value:{},samples:{}}}));eq(reads,2,'positive steady state does not repoll');view.invalidateLayout();await view.natives[P.renderNativeName('holdWorld')](null);packets=worker.sent.filter(x=>x.type==='batch');worker.emit('message',ack(packets[3],{stats:{value:{},samples:{}}}));eq(reads,3);eq(packets[3].layout.epoch,2,'unchanged measured geometry preserves epoch');
 }finally{view?.dispose();globalThis.window=oldWindow;}
}
console.log(JSON.stringify({passed:true,checks,cpuOnly:true,actualGPU:false,actualWorker:false,scope:'owned bytes/transfer, ordered resources, bounded ACK slots, result barriers, physical metadata, rejection and cleanup'}));
